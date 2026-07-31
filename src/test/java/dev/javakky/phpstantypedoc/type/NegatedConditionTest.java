package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * `($mode is not Shape::AS_LIST ? A : B)` の否定条件。PHPStan の PHPDoc 型では `is not` の 2 語で書く。
 *
 * <p>判定そのものは肯定形と同じで、決まった真偽を反転するだけにする。決められない条件は
 * 反転しても決められないので、従来どおり何も足さない。
 */
public final class NegatedConditionTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            enum Shape
            {
                case AS_LIST;
                case AS_KEYED;
            }

            /**
             * @template V
             * @param list<V> $input
             * @phpstan-return ($mode is not Shape::AS_LIST ? array<int, V> : list<V>)
             */
            function pickMode(array $input, Shape $mode = Shape::AS_KEYED): array { return $input; }

            /**
             * @template V
             * @param list<V> $input
             * @phpstan-return ($input is not list<V> ? array<int, V> : non-empty-list<V>)
             */
            function shapeOf(array $input): array { return $input; }

            function probe(): void
            {
                $notList = pickMode([1, 2], Shape::AS_KEYED);
                $isList = pickMode([1, 2], Shape::AS_LIST);
                $byDefault = pickMode([1, 2]);
                $unknown = pickMode([1, 2], $given);
                $literalList = shapeOf([1, 2]);
                $literalAssoc = shapeOf(['a' => 1]);
            }
            """;

    /**
     * 条件が成り立つ (別のケースが渡された) 側。肯定形なら偽側だった分岐が選ばれる。
     */
    public void testPicksTheTrueBranchWhenTheCaseDiffers() {
        var parts = typeOf("notList");

        assertTrue("否定条件の真側が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.replace(" ", "").startsWith("array<int,")));
        assertFalse("否定条件なのに list 側を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * 条件が成り立たない (同じケースが渡された) 側。
     */
    public void testPicksTheFalseBranchWhenTheCaseMatches() {
        var parts = typeOf("isList");

        assertTrue("否定条件の偽側が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<\\int")));
        assertFalse("否定条件の真側を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.replace(" ", "").startsWith("array<int,")));
    }

    /**
     * 実引数が書かれていなければ既定値で判定する。肯定形と同じ材料で、結果だけが反転する。
     */
    public void testUsesTheDeclaredDefault() {
        var parts = typeOf("byDefault");

        assertTrue("既定値で否定条件を判定できていない: " + parts,
                parts.stream().anyMatch(t -> t.replace(" ", "").startsWith("array<int,")));
    }

    /**
     * 判定材料が無ければ、否定でも何も足さない。反転できるのは決まった真偽だけ。
     */
    public void testAddsNothingWhenTheSubjectIsUnknown() {
        var parts = typeOf("unknown");

        assertFalse("判定材料が無いのに分岐を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")
                        || t.replace(" ", "").startsWith("array<int,")));
    }

    /**
     * 否定を条件の型名として読むと、`not` という名前のクラスが型として出る (PhpStorm 自身は
     * この形から `\Probe\not` を出す — 実測)。解決器の出力にその形が混ざらないことを固定する。
     */
    public void testDoesNotEmitTheNegationWordAsAType() {
        var resolved = ResolvedCall.of(myFixture.configureByText(PhpFileType.INSTANCE, SOURCE), "notList");

        assertNotNull("否定条件が解けていない", resolved);
        assertFalse("否定の語を型として出している: " + resolved.getTypesWithParametrisedParts(),
                resolved.getTypesWithParametrisedParts().stream().anyMatch(t -> t.contains("not")));
    }

    /**
     * 値の形による条件も同じ。`[1, 2]` は list なので、否定条件は成り立たない。
     */
    public void testNegatesAShapeCondition() {
        var parts = typeOf("literalList");

        assertTrue("否定条件の偽側 (list) が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<")));
    }

    public void testNegatesAShapeConditionForAKeyedArray() {
        var parts = typeOf("literalAssoc");

        assertTrue("否定条件の真側が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.replace(" ", "").startsWith("array<int,")));
        assertFalse("キー付き配列なのに list 側を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<")));
    }

    private Set<String> typeOf(String variableName) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
