package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;

/**
 * エディタ内のレンダリング表示 (鉛筆 / 罫線の切り替え) にも型エイリアス名が出ることを確認する。
 * Quick Documentation とは別の拡張点から来るので、別途押さえる必要がある。
 */
public final class InlineDocumentationAliasTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace App;

            /** @phpstan-type UserData array{id: positive-int} */
            final class User {}

            /**
             * @phpstan-import-type UserData from User as CreateUserData
             */
            final class CreateUser
            {
                /**
                 * @param CreateUserData $payload
                 *
                 * @return CreateUserData
                 */
                public function normalize(array $payload): array
                {
                    return $payload;
                }
            }
            """;

    public void testRenderedDocShowsAliasNames() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        var comment = methodDocComment(file);

        var documentation = new PhpStanInlineDocumentationProvider()
                .findInlineDocumentation(file, comment.getTextRange());

        assertNotNull("レンダリング表示の documentation が返らない", documentation);
        String html = documentation.renderText();
        assertNotNull("レンダリング結果が null", html);
        assertTrue("Parameters に alias が出ていない: " + html,
                html.contains("psi_element://\\App\\User'>CreateUserData</a>"));
        assertTrue("Returns にも alias が出ていない: " + html,
                occurrences(html, ">CreateUserData</a>") >= 2);
    }

    public void testLeavesDocsWithoutAliasesToThePlatform() {
        String source = """
                <?php
                namespace App;

                final class Greeter
                {
                    /**
                     * @param string $name
                     *
                     * @return string
                     */
                    public function greet(string $name): string
                    {
                        return $name;
                    }
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        var comment = PsiTreeUtil.findChildrenOfType(file, PhpDocComment.class).iterator().next();

        assertNull("エイリアスが無い PHPDoc まで奪っている",
                new PhpStanInlineDocumentationProvider().findInlineDocumentation(file, comment.getTextRange()));
    }

    private PhpDocComment methodDocComment(com.intellij.psi.PsiFile file) {
        PhpDocComment found = null;
        for (PhpDocComment comment : PsiTreeUtil.findChildrenOfType(file, PhpDocComment.class)) {
            if (comment.getText().contains("@param CreateUserData")) {
                found = comment;
            }
        }
        assertNotNull("メソッドの PHPDoc が見つからない", found);
        return found;
    }

    private int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
