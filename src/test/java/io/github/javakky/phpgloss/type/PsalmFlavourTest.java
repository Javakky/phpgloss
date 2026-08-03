package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * Psalm の綴りで書かれた宣言。
 *
 * <p>同じことを `@phpstan-` と `@psalm-` の二通りに書ける。索引だけが両方を読んで、型を出す側が
 * 片方しか見ていないと、宣言はあるのに何も出ないという食い違いになる。両端で測る。
 */
public final class PsalmFlavourTest extends BasePlatformTestCase {
    private String raw(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    private PsiFile probe(String body) {
        myFixture.addFileToProject("Port.php", """
                <?php
                namespace Probe;

                /**
                 * @psalm-type user_row = array{id: positive-int, name: non-empty-string}
                 */
                interface Port
                {
                    /** @psalm-return list<user_row> */
                    public function all(): array;

                    /** @psalm-return user_row */
                    public function one(): array;
                }
                """);
        PsiFile file = myFixture.addFileToProject("probe%d.php".formatted(Math.abs(body.hashCode())), """
                <?php
                namespace Probe;

                function probe(Port $port): void
                {
                %s
                }
                """.formatted(body));
        myFixture.openFileInEditor(file.getVirtualFile());
        return file;
    }

    /** `@psalm-type` で宣言した形が `@psalm-return` から返る。 */
    public void testResolvesAPsalmAliasThroughAPsalmReturn() {
        PsiFile file = probe("    $row = $port->one();");
        assertTrue("psalm の綴りだと形が出ない: " + raw(file, "row"),
                raw(file, "row").contains("positive-int"));
    }

    /** foreach の要素にも届く。 */
    public void testResolvesAPsalmAliasThroughForeach() {
        PsiFile file = probe("""
                    foreach ($port->all() as $each) {
                        $seen = $each;
                    }
                """);
        assertTrue("psalm の綴りだと要素の形が出ない: " + raw(file, "seen"),
                raw(file, "seen").contains("non-empty-string"));
    }

    /** キーを読める。 */
    public void testReadsAKeyOfAPsalmAlias() {
        PsiFile file = probe("    $id = $port->one()['id'];");
        assertTrue("psalm の綴りだとキーの型が出ない: " + raw(file, "id"),
                raw(file, "id").contains("positive-int"));
    }

    /** `@psalm-import-type` で他ファイルから持ち込める。 */
    public void testImportsAPsalmAliasFromAnotherFile() {
        probe("    $unused = 1;");
        myFixture.addFileToProject("Consumer.php", """
                <?php
                namespace Probe;

                /**
                 * @psalm-import-type user_row from Port as brought
                 */
                interface Consumer
                {
                    /** @psalm-return brought */
                    public function fetch(): array;
                }
                """);
        PsiFile file = myFixture.addFileToProject("consume.php", """
                <?php
                namespace Probe;

                function consume(Consumer $consumer): void
                {
                    $brought = $consumer->fetch();
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertTrue("psalm の綴りだと持ち込んだ形が出ない: " + raw(file, "brought"),
                raw(file, "brought").contains("positive-int"));
    }

    /** 同じ形を PHPStan の綴りで書いた場合。落ちる側と比べるための対照。 */
    public void testImportsAPhpStanAliasFromAnotherFile() {
        myFixture.addFileToProject("Source.php", """
                <?php
                namespace Probe;

                /**
                 * @phpstan-type src_row array{id: positive-int, name: non-empty-string}
                 */
                interface Source
                {
                    /** @phpstan-return src_row */
                    public function one(): array;
                }
                """);
        myFixture.addFileToProject("Taker.php", """
                <?php
                namespace Probe;

                /**
                 * @phpstan-import-type src_row from Source as taken
                 */
                interface Taker
                {
                    /** @phpstan-return taken */
                    public function fetch(): array;
                }
                """);
        PsiFile file = myFixture.addFileToProject("take.php", """
                <?php
                namespace Probe;

                function take(Taker $taker): void
                {
                    $taken = $taker->fetch();
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertTrue("phpstan の綴りでも持ち込んだ形が出ない: " + raw(file, "taken"),
                raw(file, "taken").contains("positive-int"));
    }

    /** どちら側が効いていないかを切り分ける: psalm の宣言を phpstan の import で持ち込む。 */
    public void testImportsWithPhpStanTagFromAPsalmDeclaration() {
        myFixture.addFileToProject("Mixer.php", """
                <?php
                namespace Probe;

                /**
                 * @phpstan-import-type user_row from Port as mixed_in
                 */
                interface Mixer
                {
                    /** @phpstan-return mixed_in */
                    public function fetch(): array;
                }
                """);
        PsiFile file = probe("    $unused = 1;");
        PsiFile use = myFixture.addFileToProject("mix.php", """
                <?php
                namespace Probe;

                function mix(Mixer $mixer): void
                {
                    $mixedIn = $mixer->fetch();
                }
                """);
        myFixture.openFileInEditor(use.getVirtualFile());

        assertTrue("psalm の宣言を phpstan の import で持ち込めない: " + raw(use, "mixedIn"),
                raw(use, "mixedIn").contains("positive-int"));
    }
}
