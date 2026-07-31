package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.FunctionReference;

/**
 * 分類キーを返す callable の型変数 (`G`) を、上界ではなくクロージャの戻り型から束縛する。
 *
 * <p>実機で `array<int, int>[]` が出ていた (正しくは `array<string, list<int>>`)。
 * キーが分類キーの型でなく元配列のキー型になり、値もグループの list でなく要素型になっていた。
 */
public final class GroupByRegressionTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            /**
             * @template K of array-key
             * @template V
             * @template G of array-key
             *
             * @param list<V>|array<K, V> $input 対象の配列
             * @param callable(V, K): G $classifier 分類キーを返す関数
             * @return array<G, list<V>|array<K, V>>
             * @phpstan-return ($input is list<V> ? array<G, list<V>> : array<G, array<K, V>>)
             */
            function groupBy(array $input, callable $classifier): array
            {
                return [];
            }

            function probe(): void
            {
                $input = [3, 1, 4, 2, 5, 6];
                $result = groupBy(
                    $input,
                    fn (int $value, int $index): string => $value % 2 === 0 ? 'even' : 'odd',
                );
            }
            """;

    public void testBindsTheGroupKeyFromTheClassifier() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        var call = PsiTreeUtil.findChildrenOfType(file, FunctionReference.class).stream()
                .filter(r -> "groupBy".equals(r.getName())).findFirst().orElseThrow();

        var resolved = ResolvedCallType.of(call).orElseThrow();
        String type = String.join("|", TypeUnionSimplifier.normalize(
                resolved.getTypesWithParametrisedParts()));

        assertTrue("分類キーが string にならない: " + type,
                type.replace(" ", "").contains("array<string,"));
        assertTrue("値がグループの list にならない: " + type, type.contains("list<"));
    }
}
