package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 条件型が実引数で評価されることを確認する。
 *
 * <p>PhpStorm はこの構文をパースするだけで評価しない。実引数を `Shape::AS_LIST` から
 * `Shape::AS_KEYED` に変えても結果が変わらず、両分岐の union が返る (実測)。
 */
public final class ConditionalReturnTypeTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            enum Shape
            {
                case AS_LIST;
                case AS_KEYED;
                case AUTO;
            }

            /**
             * @template K of array-key
             * @template V
             * @param non-empty-list<V>|array<K, V> $input
             * @phpstan-return ($mode is Shape::AS_LIST ? non-empty-list<V> : array<K, V>)
             */
            function shaped(array $input, Shape $mode = Shape::AUTO): array
            {
                return $input;
            }

            function probe(): void
            {
                $asList = shaped([new User()], Shape::AS_LIST);
                $asAssoc = shaped([new User()], Shape::AS_KEYED);
                $unknown = shaped([new User()]);
            }
            """;

    public void testPicksTheListBranch() {
        assertBranch("asList", "non-empty-list<\\Probe\\User>");
    }

    /**
     * 別の enum ケースなら list 側は選ばれない。`array<K, V>` の `K` は実引数から決められないため、
     * 分岐は選べても型としては出さない (誤った型を出すより黙る)。
     */
    public void testDoesNotPickTheListBranchForAnotherEnumCase() {
        var parts = typeOf("asAssoc");

        assertFalse("別のケースなのに list 側を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<")));
    }

    /**
     * 実引数が省略されて判定材料が無いときは何も足さない。誤った分岐を出すより黙る。
     */
    public void testAddsNothingWhenTheArgumentIsAbsent() {
        var parts = typeOf("unknown");

        assertFalse("判定材料が無いのに分岐を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<")));
    }

    private void assertBranch(String variableName, String expectedPrefix) {
        var parts = typeOf(variableName);

        assertTrue("選ばれた分岐が出ていない (期待: " + expectedPrefix + "): " + parts,
                parts.stream().anyMatch(t -> t.startsWith(expectedPrefix)));
    }

    private java.util.Set<String> typeOf(String variableName) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
