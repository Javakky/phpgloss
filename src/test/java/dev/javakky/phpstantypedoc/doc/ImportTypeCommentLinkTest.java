package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;

/**
 * `@phpstan-import-type` / `@phpstan-type` のコメント自体に出るエイリアス名がリンクになることを
 * 確認する。PHP は `from` の後のクラス名しかリンクにしない。
 */
public final class ImportTypeCommentLinkTest extends BasePlatformTestCase {
    public void testLinksBothNamesOfImportTypeComment() {
        myFixture.addFileToProject("Domain.php", """
                <?php
                namespace App\\Domain;

                /** @phpstan-type UserData array{id: positive-int} */
                final class User {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Service;

                use App\\Domain\\User;

                /**
                 * @phpstan-import-type UserData from User as CreateUserData
                 */
                final class CreateUser {}
                """);
        var comment = PsiTreeUtil.findChildrenOfType(file, PhpDocComment.class).iterator().next();

        var documentation = new PhpStanInlineDocumentationProvider()
                .findInlineDocumentation(file, comment.getTextRange());

        assertNotNull("クラス PHPDoc の documentation が返らない", documentation);
        String html = documentation.renderText();
        assertNotNull("レンダリング結果が null", html);
        // レンダリング表示は Owner::Alias を「不正な形式の URL」として弾くので、定義元クラスへ向ける
        assertTrue("別名がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\User'>CreateUserData</a>"));
        assertTrue("元名がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\User'>UserData</a>"));
        assertFalse("レンダリング表示に Owner::Alias 形式が残っている: " + html, html.contains("::"));
        // PHP が付けたクラスリンクを壊していない
        assertTrue("from のクラスリンクが失われた: " + html,
                html.contains("psi_element://\\App\\Domain\\User'>User</a>"));
    }

    public void testLinksDeclaredAliasName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /**
                 * @phpstan-type UserData array{id: positive-int}
                 */
                final class User {}
                """);
        var comment = PsiTreeUtil.findChildrenOfType(file, PhpDocComment.class).iterator().next();

        var documentation = new PhpStanInlineDocumentationProvider()
                .findInlineDocumentation(file, comment.getTextRange());
        assertNotNull("クラス PHPDoc の documentation が返らない", documentation);
        String html = documentation.renderText();
        assertNotNull("レンダリング結果が null", html);

        assertTrue("宣言名がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\User'>UserData</a>"));
    }
}
