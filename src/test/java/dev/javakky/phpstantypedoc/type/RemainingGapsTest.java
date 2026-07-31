package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 適合スイープが残した沈黙を潰したことを固定する。
 *
 * <p>要素の型が混ざった配列リテラルを PhpStorm は `array<int, mixed>` として持つため、
 * そこから束縛した型変数も `mixed` に潰れていた (PHPStan は `1|2|null` まで追う)。
 * 書かれた要素はそれぞれ型が読めるので、union として組み直して束縛する。
 */
public final class RemainingGapsTest extends BasePlatformTestCase {
    public void testKeepsTheElementTypesOfAMixedLiteral() {
        String source = """
                <?php
                namespace Probe;

                /**
                 * @template K of array-key
                 * @template V
                 * @param list<V>|array<K, V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed
                {
                    $firstKey = array_key_first($input);
                    return $firstKey === null ? null : $input[$firstKey];
                }

                /**
                 * @template V
                 * @template R of array-key
                 * @param list<V> $input
                 * @param callable(V): R $classifier
                 * @return list<list<V>>
                 */
                function chunkBy(array $input, callable $classifier): array
                {
                    $key = $classifier($input[0]);
                    $isList = array_is_list($input);
                    return [];
                }

                function probe(): void
                {
                    $mixedLiteral = [null, 1, 2];
                    $result = firstOrNull($mixedLiteral);
                    $strings = ['a', 'b'];
                    $s = firstOrNull($strings);
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        var call = PsiTreeUtil.findChildrenOfType(file,
                        com.jetbrains.php.lang.psi.elements.FunctionReference.class).stream()
                .filter(r -> "firstOrNull".equals(r.getName())).findFirst().orElseThrow();

        String type = String.join("|", TypeUnionSimplifier.normalize(
                ResolvedCallType.of(call).orElseThrow().getTypesWithParametrisedParts()))
;

        assertTrue("要素型が mixed に潰れている: " + type, type.contains("int"));
        assertTrue("null が落ちている: " + type, type.contains("null"));
        assertFalse("mixed が残っている: " + type, type.contains("mixed"));
    }

    /**
     * 要素の型が読めないリテラルでは組み立てない。粗い型を混ぜると、PhpStorm 自身の答えより
     * 精度が落ちることがある。
     */
    public void testDoesNotBuildFromAnUnreadableLiteral() {
        String source = """
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(mixed $unknown): void
                {
                    $result = firstOrNull([$unknown, $unknown]);
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        var call = PsiTreeUtil.findChildrenOfType(file,
                        com.jetbrains.php.lang.psi.elements.FunctionReference.class).stream()
                .filter(r -> "firstOrNull".equals(r.getName())).findFirst().orElseThrow();

        assertNull("読めない要素からも型を組み立てている",
                ArgumentText.literalArrayTypeOf(call.getParameters()[0], getProject()));
    }
}
