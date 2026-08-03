package io.github.javakky.phpgloss.parser;

import junit.framework.TestCase;

import java.util.List;

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
