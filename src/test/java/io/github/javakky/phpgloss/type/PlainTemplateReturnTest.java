package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * `@return V|null` のような、型引数を持たない戻り値でも精密化を落とさない。
 *
 * <p>PhpStorm 自身も V は解くが、`positive-int` を `int` に潰す (実機: `null|int`)。
 * `@var list<positive-int>` で与えた精密な要素型を保って返す。
 */
public final class PlainTemplateReturnTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            /**
             * @template K of array-key
             * @template V
             * @param list<V>|array<K, V> $input
             * @return V|null
             */
            function lastOrNull(array $input): mixed
            {
                return $input === [] ? null : $input[array_key_last($input)];
            }

            function probe(): void
            {
                /** @var list<positive-int> $input */
                $input = [1, 2, 3];
                $result = lastOrNull($input);
            }
            """;

    public void testKeepsTheRefinedElementType() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable input = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "input".equals(v.getName())).findFirst().orElseThrow();
        Variable result = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "result".equals(v.getName())).findFirst().orElseThrow();

        var inputParts = ((PhpTypedElement) input).getType().global(getProject())
                .getTypesWithParametrisedParts();
        var parts = ((PhpTypedElement) result).getType().global(getProject())
                .getTypesWithParametrisedParts();

        assertTrue("精密な要素型が落ちている: " + parts,
                parts.stream().anyMatch(t -> t.contains("positive-int") || t.replace(" ", "").contains("int<1,")));
        // Option 型なので null は必ず残る。in-place の解決結果でも落ちてはいけない
        var inPlace = PsiTreeUtil.findChildrenOfType(file, com.jetbrains.php.lang.psi.elements.FunctionReference.class)
                .stream().filter(r -> "lastOrNull".equals(r.getName())).findFirst()
                .flatMap(ResolvedCallType::of).orElseThrow();
        assertTrue("in-place の解決から null が落ちている: " + inPlace.getTypes(),
                inPlace.getTypes().stream().anyMatch(t -> t.equals("\\null") || t.equals("null")));
    }
}
