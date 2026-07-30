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
             * @phpstan-return ($mode is Shape::AS_LIST ? non-empty-list<V> :
             *   ($mode is Shape::AS_KEYED ? list<V> :
             *     non-empty-list<V>
             * ))
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
     * 別の enum ケースなら、入れ子の内側の分岐まで畳んで型を決める。
     */
    public void testPicksTheNestedBranchForAnotherEnumCase() {
        var parts = typeOf("asAssoc");

        assertTrue("入れ子の分岐が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<\\Probe\\User")));
    }

    /**
     * 実際のコードは条件型が入れ子になる。1 段しか読まないと、else 側に残った
     * `($mode is ... )` という文字列をそのまま型として扱ってしまう。
     */
    public void testDoesNotEmitANestedConditionalAsAType() {
        var parts = typeOf("asAssoc");

        assertFalse("条件型の文字列を型として出している: " + parts,
                parts.stream().anyMatch(t -> t.contains(" is ") || t.contains("Shape::")));
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
