package dev.javakky.phpstantypedoc.keyword;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

/**
 * キーワードとして扱う位置が、実際に型が書かれている場所に絞られていることを確認する。
 *
 * <p>PHPDoc の中というだけで拾うと、説明文の `list` や import の `as` まで対象になる。
 */
public final class PhpStanKeywordAtCaretTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace App;

            final class User {}

            /**
             * @phpstan-type UserData array{users: non-empty-list<User>}
             */
            final class Holder
            {
                /**
                 * 引数の list は max まで受け付ける。
                 *
                 * @param int<1, max> $limit
                 *
                 * @return non-empty-string
                 */
                public function run(int $limit): string
                {
                    return '';
                }
            }
            """;

    public void testFindsKeywordInAliasBody() {
        assertKeyword("non-empty-list<User>", "non-empty-list");
    }

    public void testFindsKeywordInParamType() {
        assertKeyword("max> $limit", "max");
    }

    public void testFindsKeywordInReturnType() {
        assertKeyword("non-empty-string\n", "non-empty-string");
    }

    /**
     * 説明文に同じ綴りが出ても型ではない。
     */
    public void testIgnoresProse() {
        assertNoKeyword("list は max まで");
        assertNoKeyword("max まで受け付ける");
    }

    public void testIgnoresImportAlias() {
        String source = """
                <?php
                namespace App;

                /**
                 * @phpstan-type UserData array{id: int}
                 */
                final class User {}

                /**
                 * @phpstan-import-type UserData from User as list
                 */
                final class Holder {}
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        int offset = source.indexOf("as list") + "as ".length();

        assertTrue("import の別名を型キーワードとして扱っている",
                PhpStanKeywordAtCaret.find(file, offset).isEmpty());
    }

    private void assertKeyword(String marker, String expected) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.indexOf(marker) + 1;

        var keyword = PhpStanKeywordAtCaret.find(file, offset);

        assertTrue(expected + " を検出できない (marker=" + marker + ")", keyword.isPresent());
        assertEquals(expected, keyword.get().name());
    }

    private void assertNoKeyword(String marker) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.indexOf(marker) + 1;

        assertTrue("説明文の語を型キーワードとして扱っている (marker=" + marker + ")",
                PhpStanKeywordAtCaret.find(file, offset).isEmpty());
    }
}
