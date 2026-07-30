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
        // 別名は利用側クラスの alias、元名は定義元クラスの alias へ向ける
        assertTrue("別名がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Service\\CreateUser::CreateUserData'>CreateUserData</a>"));
        assertTrue("元名がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\User::UserData'>UserData</a>"));
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
                html.contains("::UserData'>UserData</a>"));
    }
}
