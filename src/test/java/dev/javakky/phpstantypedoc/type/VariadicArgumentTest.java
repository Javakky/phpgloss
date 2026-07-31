package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 可変長引数 (`@param V ...$values`) の束縛。
 *
 * <p>宣言は要素ひとつ分の型なので、その位置以降に書かれた実引数すべてを同じ仮引数に対応付け、
 * 型の union を型変数の中身にする。1 つ目だけを見ると `collect(1, 'a')` が `list<int>` になる。
 *
 * <p>`...$arr` と展開して渡された場合は中身が分からない。何個の何型が入るか決められないので
 * 何も束縛しない。
 */
public final class VariadicArgumentTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            /**
             * @template V
             * @param V ...$values
             * @return list<V>
             */
            function collect(mixed ...$values): array { return $values; }

            /**
             * @template V
             * @template E
             * @param list<V> $input
             * @param E ...$extra
             * @return array<E, list<V>>
             */
            function tagged(array $input, mixed ...$extra): array { return []; }

            /**
             * @template V
             * @param list<V> $input
             * @param V ...$extra
             * @return non-empty-list<V>
             */
            function appended(array $input, mixed ...$extra): array { return []; }

            function probe(): void
            {
                $same = collect(1, 2);
                $mixed = collect(1, 'a');
                $none = collect();
                $spread = collect(...[1, 2]);
                $partlySpread = collect(1, ...[2, 3]);
                $named = appended(input: [1, 2]);
                $withExtra = tagged([1, 2], 'a', 'b');
                $withoutExtra = tagged(input: [1, 2]);
            }
            """;

    /**
     * 同じ型だけを並べた場合は、1 つ目から決めた結果と変わらない。
     */
    public void testBindsFromASingleKindOfArgument() {
        var parts = typeOf("same");

        assertTrue("可変長引数から型変数を決められていない: " + parts, parts.contains("list<\\int>"));
    }

    /**
     * 型が違う実引数が並んだら union になる。1 つ目だけを見ると `\string` が落ちる。
     */
    public void testUnionsEveryWrittenArgument() {
        var parts = typeOf("mixed");

        assertTrue("union で束ねられていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")
                        && t.contains("int") && t.contains("string")));
    }

    /**
     * 1 つも渡されなければ決める材料が無い。何も足さない。
     */
    public void testAddsNothingWithoutArguments() {
        var parts = typeOf("none");

        assertFalse("材料が無いのに型変数を埋めている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * `...$arr` の中身は呼び出し側からは分からない。配列そのものを要素型として束縛すると
     * `list<int[]>` のような誤った型になる。
     */
    public void testAddsNothingForASpreadArgument() {
        var parts = typeOf("spread");

        assertFalse("展開して渡された引数から型変数を決めている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * 書かれた実引数と展開が混ざる場合も決めない。書かれた分だけで union を閉じると、
     * 展開された中身の型が落ちる。
     */
    public void testAddsNothingWhenASpreadIsMixedIn() {
        var parts = typeOf("partlySpread");

        assertFalse("展開が混ざっているのに型変数を決めている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * 可変長引数を持つ関数でも、名前付き引数は名前で対応付ける。可変長の位置以降にまとめると
     * `$input` が `$extra` 側に流れ、`non-empty-list<V>` が決まらなくなる。
     */
    public void testKeepsMatchingNamedArgumentsByName() {
        var parts = typeOf("named");

        assertTrue("名前付き引数が可変長側に流れている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<\\int")));
    }

    /**
     * 可変長より前の仮引数と可変長は、別々の型変数として決まる。
     */
    public void testBindsTheVariadicAndTheLeadingParameterSeparately() {
        var parts = typeOf("withExtra");

        assertTrue("可変長と先頭の仮引数を分けて決められていない: " + parts,
                parts.stream().anyMatch(t -> t.replace(" ", "").contains("array<\\string,list<\\int>>")));
    }

    /**
     * 可変長へ何も渡されなければ、その型変数は決まらない。名前付きで先頭だけ渡した場合がそれで、
     * `E` を含む戻り値は出せない。
     */
    public void testAddsNothingWhenOnlyTheLeadingParameterIsGiven() {
        var parts = typeOf("withoutExtra");

        assertFalse("材料が無いのに可変長の型変数を埋めている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("array<")));
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
