package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 実際に報告されている症状を測る。
 *
 * <p>ここまでの fixture は、こちらが触ったコードで困った形から作ってきた。利用者が報告している
 * 形と重なっているとは限らないので、YouTrack と各所で挙がっているものを写して確かめる。
 */
public final class ReportedGapsTest extends BasePlatformTestCase {
    private String typeOf(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    /** foreach の中で、要素の shape が保たれること。 */
    public void testKeepsAShapeInsideForeach() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int, label: non-empty-string}
                 */
                interface Rows
                {
                    /** @return list<row> */
                    public function all(): array;
                }

                function probe(Rows $rows): void
                {
                    foreach ($rows->all() as $row) {
                        $label = $row['label'];
                    }
                }
                """);

        assertTrue("foreach の要素が shape にならない: " + typeOf(file, "row"),
                typeOf(file, "row").contains("array{"));

        // 要素の shape は入るが、そこへのキーアクセスはまだ `mixed` になる。PhpStorm は
        // shape を内部の表現で持っており、こちらが文字列として足した型からは読み解かない。
        // 直すには要素アクセスの側にも入る必要があり、それは別の作業になる
        assertFalse("キーアクセスが解けるようになった。この制限の記述を消すこと: "
                        + typeOf(file, "label"),
                typeOf(file, "label").contains("non-empty-string"));
    }

    /** 代入記号を書かない宣言でも、shape がパースされること。 */
    public void testParsesAShapeDeclaredWithoutAnEquals() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int, label: non-empty-string}
                 */
                final class Port
                {
                    /** @var row */
                    public array $current = [];
                }

                function probe(Port $port): void
                {
                    $value = $port->current['label'];
                }
                """);

        assertTrue("代入記号なしの宣言で shape が読めない: " + typeOf(file, "value"),
                typeOf(file, "value").contains("string"));
    }

    /** `@mixin` の先にあるメソッドでも、エイリアスが解決されること。 */
    public void testResolvesThroughMixin() {
        myFixture.addFileToProject("Source.php", """
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int}
                 */
                final class Source
                {
                    /** @return list<row> */
                    public function rows(): array { return []; }
                }
                """);
        PsiFile file = myFixture.addFileToProject("Facade.php", """
                <?php
                namespace Probe;

                /**
                 * @mixin Source
                 */
                final class Facade {}

                function probe(Facade $facade): void
                {
                    $rows = $facade->rows();
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertTrue("@mixin の先でエイリアスが解決されない: " + typeOf(file, "rows"),
                typeOf(file, "rows").contains("row") || typeOf(file, "rows").contains("array{"));
    }

    /** ファイルをまたいでも、総称の戻り値が効くこと。 */
    public void testKeepsGenericsAcrossFiles() {
        myFixture.addFileToProject("Holder.php", """
                <?php
                namespace Probe;

                /**
                 * @template T
                 */
                final class Holder
                {
                    /** @return T */
                    public function get(): mixed { return null; }
                }
                """);
        PsiFile file = myFixture.addFileToProject("Caller.php", """
                <?php
                namespace Probe;

                final class User {}

                function probe(): void
                {
                    /** @var Holder<User> $holder */
                    $holder = new Holder();
                    $value = $holder->get();
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertTrue("ファイルをまたぐと型引数が落ちる: " + typeOf(file, "value"),
                typeOf(file, "value").contains("User"));
    }
}
