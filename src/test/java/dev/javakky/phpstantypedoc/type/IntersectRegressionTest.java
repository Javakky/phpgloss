package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 実物 the sample project の `intersect` で出た誤り (実機 2026-07-31):
 * `$result: array<array-key, int>|array<string,int>`。
 *
 * <p>第 2 引数の宣言 `array<array-key, V>` の `array-key` は型キーワードであって型変数でない。
 * ここから誤った束縛を作ると、`$input` から取れた `K = string` と競合する。
 */
public final class IntersectRegressionTest extends BasePlatformTestCase {
    public void testDoesNotBindATypeKeywordAsATemplate() {
        String source = """
                <?php
                namespace Probe;

                enum Shape { case AS_LIST; case AS_KEYED; case AUTO;
                    public static function resolveShape(Shape $m, array $i): Shape { return $m; } }

                /**
                 * @template K of array-key
                 * @template V
                 * @param list<V>|array<K, V> $input
                 * @param list<V>|array<array-key, V> $other
                 * @phpstan-return ($mode is Shape::AS_LIST ? list<V> :
                 *     ($mode is Shape::AS_KEYED ? array<K, V> :
                 *       ($input is list<V> ? list<V> :
                 *         array<K, V>
                 *  )))
                 */
                function intersect(array $input, array $other, Shape $mode = Shape::AUTO): array
                {
                    return $input;
                }

                function probe(): void
                {
                    $input = ['alice' => 20, 'bob' => 17];
                    $other = ['dave' => 20];
                    $result = intersect($input, $other);
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "result".equals(v.getName())).findFirst().orElseThrow();

        var parts = ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();

        // 実引数からキーを取れないときは上界 `array-key` で埋まる。それ自体は正しい上界だが、
        // PhpStorm 自身が持つ `array<string, int>` の方が精密なので、表示では呑まれること
        var simplified = TypeUnionSimplifier.simplify(parts).orElseThrow();

        assertFalse("表示に array-key のキーが残っている: " + simplified + " / raw=" + parts,
                simplified.stream().anyMatch(t -> t.contains("array-key")));
        assertTrue("K = string の型が表示されない: " + simplified,
                simplified.stream().anyMatch(t -> t.replace(" ", "").startsWith("array<string,")));
    }
}
