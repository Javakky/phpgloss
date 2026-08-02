package io.github.javakky.phpgloss.type;

import junit.framework.TestCase;

import java.util.List;
import java.util.Optional;

/**
 * union に並んだ型のうち、他の型に呑まれているものだけが落ちることを確認する。
 *
 * <p>入力は実測した値をそのまま使う (`$result` は `{list<\int>, \array, \int[]}`)。
 */
public final class TypeUnionSimplifierTest extends TestCase {
    public void testDropsTheAbstractArrayAndThePluralFormWhenAListIsResolved() {
        assertSimplified(List.of("list<\\int>", "\\array", "\\int[]"), "list<int>");
    }

    /** キーを持つ総称形も `X[]` を呑む。実測の `$mapped` がこの形。 */
    public void testDropsThePluralFormWhenAKeyedArrayHasTheSameElement() {
        assertSimplified(List.of("array<\\string, \\int>", "\\array", "\\int[]"), "array<string, int>");
    }

    /** 前後の空白は落として比べる。実測の ` \int[]` は `\int[]` と同じ型。 */
    public void testTreatsTypesThatDifferOnlyInSpacingAsOne() {
        assertSimplified(List.of("\\int[]", " \\int[]", "\\array"), "int[]");
    }

    /** 型が付かなかった印。他に型があるなら読み手が求めるのはそちら。 */
    public void testDropsTheUnknownMarkerWhenAnyTypeIsPresent() {
        assertSimplified(List.of("?", "\\string"), "string");
    }

    /** キーアクセスに PhpStorm が常に足す `mixed`。実測の `$name` がこの形。 */
    public void testDropsMixedWhenAConcreteTypeIsPresent() {
        assertSimplified(List.of("\\mixed", "\\string", "?"), "string");
    }

    /** 具体型が無いなら `mixed` が残る。落とすと出す型が無くなる。 */
    public void testKeepsMixedWhenNothingConcreteIsPresent() {
        assertSimplified(List.of("\\mixed", "?"), "mixed");
    }

    public void testDropsTheListWhenTheNonEmptyListIsPresent() {
        assertSimplified(List.of("list<\\int>", "non-empty-list<\\int>"), "non-empty-list<int>");
    }

    public void testDropsTheAbstractIterableWhenAnArrayTypeIsPresent() {
        assertSimplified(List.of("\\iterable", "list<\\int>"), "list<int>");
    }

    /** `array<X>` はキーを縛らない配列で、`X[]` の別表記。 */
    public void testTreatsAnArrayWithOneArgumentAsThePluralForm() {
        assertSimplified(List.of("array<\\int>", "\\int[]"), "int[]");
    }

    public void testKeepsIncomparableScalars() {
        assertUnchanged(List.of("\\int", "\\string"));
    }

    public void testKeepsPluralFormsOfDifferentClasses() {
        assertUnchanged(List.of("\\App\\Foo[]", "\\App\\Bar[]"));
    }

    /** 要素型が違えば包摂は言えない。 */
    public void testKeepsThePluralFormWhenTheElementDiffers() {
        assertUnchanged(List.of("\\int[]", "list<\\string>"));
    }

    /** `iterable<X>` は配列とは限らないので `X[]` を呑まない。 */
    public void testKeepsThePluralFormAgainstAGenericIterable() {
        assertUnchanged(List.of("\\int[]", "iterable<\\int>"));
    }

    public void testKeepsASingleType() {
        assertUnchanged(List.of("\\int<1,1>"));
    }

    private void assertSimplified(List<String> types, String... expected) {
        Optional<List<String>> simplified = TypeUnionSimplifier.simplify(types);

        assertEquals("整理されていない: " + types, List.of(expected), simplified.orElse(null));
    }

    private void assertUnchanged(List<String> types) {
        assertEquals("減らないのに介入している: " + types,
                Optional.empty(), TypeUnionSimplifier.simplify(types));
    }

    /**
     * `array<int, X>` と `list<X>` は落とさない。未解決の条件型は両分岐を選択肢として union に
     * 残すため、この組は「どちらもありえる」を意味することがある。狭い側だけ残すと嘘になる。
     */
    public void testKeepsAnIntKeyedArrayBesideAList() {
        assertTrue("選択肢かもしれない組を狭めている",
                TypeUnionSimplifier.simplify(java.util.List.of("list<\\int>", "array<\\int, \\int>")).isEmpty());
    }

    /**
     * 分岐確定時、解決器の粗い束縛 (`int`) より PhpStorm の定数追跡 (`int<20, 20>`) が精密なら
     * そちらを見せる。粗い方に固めると、既知の精度を自分で捨てることになる。
     */
    public void testRefinesAgainstAMorePreciseCoDescription() {
        var refined = TypeUnionSimplifier.refineAgainst(
                java.util.List.of("array<string, int>", "null"),
                java.util.List.of("\\array<\\string,\\int<20,20>>", "\\array", "\\null"));

        assertEquals(java.util.List.of("array<string, int<20,20>>", "null"), refined);
    }

    /**
     * 構造が違う成分では置き換えない。`list<int<20,20>>` は `array<string, int>` の精密化ではない。
     */
    public void testDoesNotRefineAcrossDifferentStructures() {
        var refined = TypeUnionSimplifier.refineAgainst(
                java.util.List.of("array<string, int>"),
                java.util.List.of("list<\\int<20,20>>"));

        assertEquals(java.util.List.of("array<string, int>"), refined);
    }
}
