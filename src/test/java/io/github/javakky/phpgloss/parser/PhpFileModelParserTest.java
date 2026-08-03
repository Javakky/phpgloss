package io.github.javakky.phpgloss.parser;

import junit.framework.TestCase;

import java.util.List;
import java.util.Map;

/**
 * 宣言と、その直前に書かれた PHPDoc の対応。
 *
 * <p>ファイルには宣言の PHPDoc 以外にもコメントがある。プロパティやメソッドに付いたものを
 * 宣言のものと取り違えると、その宣言のエイリアスがまるごと読めなくなる。
 */
public final class PhpFileModelParserTest extends TestCase {
    private final PhpFileModelParser parser = new PhpFileModelParser();

    private List<String> aliasNamesOf(PhpFileModel model, String fqcn) {
        return model.classLikeDocBlocks().stream()
                .filter(b -> b.fqcn().equals(fqcn))
                .flatMap(b -> b.aliases().stream())
                .map(a -> a.aliasName())
                .toList();
    }

    /** 前のクラスがメンバの PHPDoc を持っていても、後ろのクラスの宣言 PHPDoc が読める。 */
    public void testReadsAliasesOfEveryClassLikeInAFile() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type leaf_data array{weight: positive-int}
                 */
                final class Leaf
                {
                    /** @var leaf_data */
                    public array $data = ['weight' => 1];
                }

                /**
                 * @phpstan-type row array{id: positive-int}
                 */
                interface Rows
                {
                    /** @return row */
                    public function find(): array;
                }
                """);

        assertEquals("先に書いたクラスのエイリアスが読めない",
                List.of("leaf_data"), aliasNamesOf(model, "Probe\\Leaf"));
        assertEquals("後ろのクラスのエイリアスが、手前のメンバの PHPDoc に食われている",
                List.of("row"), aliasNamesOf(model, "Probe\\Rows"));
    }

    /** メンバの PHPDoc しか持たないクラスは、宣言の PHPDoc を持たない扱いになる。 */
    public void testDoesNotAttachAMemberDocToTheNextDeclaration() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace Probe;

                final class Holder
                {
                    /** @phpstan-type stray array{x: int} */
                    public array $value = [];
                }

                final class After {}
                """);

        assertTrue("メンバのコメントが後ろのクラスの宣言 PHPDoc として読まれている",
                aliasNamesOf(model, "Probe\\After").isEmpty());
    }

    /** ファイルに namespace が二つあれば、宣言は自分が書かれた側に属する。 */
    public void testUsesTheNamespaceEachDeclarationSitsIn() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace First;

                /**
                 * @phpstan-type early array{a: int}
                 */
                final class Early {}

                namespace Second;

                /**
                 * @phpstan-type late array{b: int}
                 */
                final class Late {}
                """);

        assertEquals("最初の namespace が読めない",
                List.of("early"), aliasNamesOf(model, "First\\Early"));
        assertEquals("二つめの namespace の宣言が、最初の namespace に入れられている",
                List.of("late"), aliasNamesOf(model, "Second\\Late"));
    }

    /** 波括弧で囲む namespace でも同じ。 */
    public void testUsesTheNamespaceOfABracedBlock() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace First {
                    /**
                     * @phpstan-type early array{a: int}
                     */
                    final class Early {}
                }

                namespace Second {
                    /**
                     * @phpstan-type late array{b: int}
                     */
                    final class Late {}
                }
                """);

        assertEquals("波括弧の二つめの namespace が読めない",
                List.of("late"), aliasNamesOf(model, "Second\\Late"));
    }

    /** まとめて書いた use が、一つずつ書いたときと同じに解ける。 */
    public void testExpandsAGroupUse() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace Probe;

                use Vendor\\Pack\\{Alpha, Beta as Renamed};
                use Vendor\\Other\\Gamma;

                /**
                 * @phpstan-type held array{x: int}
                 */
                final class Holder {}
                """);

        Map<String, String> uses = model.classLikeDocBlocks().get(0).uses();
        assertEquals("まとめ書きの一つめが解けない", "Vendor\\Pack\\Alpha", uses.get("Alpha"));
        assertEquals("まとめ書きの中の as が解けない", "Vendor\\Pack\\Beta", uses.get("Renamed"));
        assertEquals("並んだ普通の use が壊れている", "Vendor\\Other\\Gamma", uses.get("Gamma"));
    }

    /** use は自分の namespace のものだけが見える。 */
    public void testKeepsUsesWithinTheirNamespace() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace First;

                use Vendor\\Early;

                /**
                 * @phpstan-type a array{a: int}
                 */
                final class Early2 {}

                namespace Second;

                use Vendor\\Late;

                /**
                 * @phpstan-type b array{b: int}
                 */
                final class Late2 {}
                """);

        Map<String, String> second = model.classLikeDocBlocks().stream()
                .filter(b -> b.fqcn().equals("Second\\Late2"))
                .findFirst().orElseThrow().uses();
        assertEquals("自分の namespace の use が見えない", "Vendor\\Late", second.get("Late"));
        assertNull("別の namespace の use が混ざっている", second.get("Early"));
    }

    /** 属性を挟んでも宣言の PHPDoc は読める。 */
    public void testReadsThroughAnAttribute() {
        PhpFileModel model = parser.parse("""
                <?php
                namespace Probe;

                /**
                 * @phpstan-type marked array{id: int}
                 */
                #[\\Attribute]
                final class Marked {}
                """);

        assertEquals("属性を挟むと宣言の PHPDoc が読めない",
                List.of("marked"), aliasNamesOf(model, "Probe\\Marked"));
    }
}
