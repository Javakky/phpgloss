package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.List;
import java.util.Set;

/**
 * 交差型 (`@param T&\Countable $subject`) の単一化。
 *
 * <p>型変数でない成分は照合条件で、束縛先ではない。実引数の型が同じ成分を持っていれば差し引き、
 * 残りを型変数に束縛する。型名だけでは実装関係を判定できない (`\Probe\Bag` が `Countable` を
 * 実装しているかは分からない) ため、条件に当たる成分が無くても束縛は止めない。
 */
public final class IntersectionTemplateTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            interface Boxed {}

            final class Bag implements \\Countable, Boxed
            {
                public function count(): int { return 0; }
            }

            /**
             * @template T
             * @param non-empty-list<T> $items
             * @return T&Boxed
             */
            function firstOf(array $items): mixed { return $items[0]; }

            /**
             * @template T
             * @param T&\\Countable $subject
             * @return non-empty-list<T>
             */
            function wrap(mixed $subject): array { return [$subject]; }

            /**
             * @template T
             * @template U
             * @param T&U $subject
             * @return non-empty-list<T>
             */
            function wrapBoth(mixed $subject): array { return [$subject]; }

            /**
             * @template V
             * @param iterable<V>&\\Countable $subject
             * @return non-empty-list<V>
             */
            function elementsOf(iterable $subject): array { return []; }

            function probe(): void
            {
                $bag = new Bag();
                $wrapped = wrap($bag);
                $both = wrapBoth($bag);
                $first = firstOf([new Bag()]);
            }
            """;

    /**
     * 型変数でない成分を跨いで、型変数側に実引数の型が入る。
     */
    public void testBindsTheTypeVariablePartOfAnIntersection() {
        var parts = typeOf("wrapped");

        assertTrue("交差型から型変数を決められていない: " + parts,
                parts.contains("non-empty-list<\\Probe\\Bag>"));
    }

    /**
     * 照合条件に書かれた成分は束縛に混ぜない。混ぜると `Bag&\Countable` が要素型になり、
     * 同じ条件が二重に書かれた型になる。
     */
    public void testDoesNotBindTheConstraintPart() {
        var parts = typeOf("wrapped");

        assertFalse("照合条件が束縛に混ざっている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<") && t.contains("Countable")));
    }

    /**
     * 型変数が 2 つ並ぶ交差では、どちらに何を束縛するか決められない。何も足さない。
     */
    public void testAddsNothingWhenBothPartsAreTypeVariables() {
        var parts = typeOf("both");

        assertFalse("決められないのに型変数を埋めている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<")));
    }

    /**
     * 照合条件の中に埋まった型変数 (`iterable<V>` の `V`) は、そのまま突き合わせて決める。
     */
    public void testBindsATypeVariableNestedInAConstraint() {
        String source = SOURCE.replace(
                "    $both = wrapBoth($bag);",
                "    $both = wrapBoth($bag);\n    $elements = elementsOf([1, 2]);");
        var parts = typeOf(source, "elements");

        assertTrue("照合条件の中の型変数を決められていない: " + parts,
                parts.contains("non-empty-list<\\int>"));
    }

    /**
     * 戻り値が交差型なら、成分ごとに完全修飾する。まとめて解決すると `Bag&Boxed` という名前の
     * クラスを探すことになり、短い名前の側が解決されないまま残る。
     */
    public void testQualifiesEachMemberOfAnIntersectionReturn() {
        // 表示では PhpStorm 自身が解いた同じ形が並ぶため、解決器の出力を直接見る
        var resolved = ResolvedCall.of(myFixture.configureByText(PhpFileType.INSTANCE, SOURCE), "first");

        assertNotNull("交差型の戻り値が解けていない", resolved);
        assertEquals(Set.of("\\Probe\\Bag&\\Probe\\Boxed"), resolved.getTypes());
    }

    /**
     * 表示側の正規化が `&` を壊さないこと。成分ごとに綴りは揃えるが、交差そのものは残す。
     */
    public void testKeepsIntersectionsWhenNormalisingForDisplay() {
        assertEquals(List.of("\\Probe\\Bag&\\Countable", "int"),
                TypeUnionSimplifier.normalize(List.of("\\Probe\\Bag&\\Countable", "\\int")));
    }

    /**
     * 交差型は配列系の型ではない。包摂の判定に巻き込んで落とすと、型が丸ごと消える。
     */
    public void testKeepsIntersectionsWhenSimplifyingForDisplay() {
        var simplified = TypeUnionSimplifier.simplify(List.of("\\Probe\\Bag&\\Countable", "?"));

        assertEquals(List.of("\\Probe\\Bag&\\Countable"), simplified.orElseThrow());
    }

    private Set<String> typeOf(String variableName) {
        return typeOf(SOURCE, variableName);
    }

    private Set<String> typeOf(String source, String variableName) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
