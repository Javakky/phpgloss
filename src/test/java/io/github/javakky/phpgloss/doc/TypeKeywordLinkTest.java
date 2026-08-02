package io.github.javakky.phpgloss.doc;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import io.github.javakky.phpgloss.keyword.PhpStanKeywordDefinition;

/**
 * 型キーワードがリンクになり、説明と定義位置に辿り着けることを確認する。
 */
public final class TypeKeywordLinkTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace App\\Domain;

            /** @phpstan-type Payload array{users: non-empty-list<Account>, label: non-empty-string} */
            final class Holder
            {
                /** @return Payload */
                public function get(): array
                {
                    return [];
                }
            }
            """;

    public void testLinksKeywordsInTypeBody() {
        String html = documentationHtml(SOURCE);

        assertTrue("non-empty-list がリンクになっていない: " + html,
                html.contains("psi_element://phpstan-type-keyword/non-empty-list"));
        assertTrue("non-empty-string がリンクになっていない: " + html,
                html.contains("psi_element://phpstan-type-keyword/non-empty-string"));
    }

    /**
     * `array{list: int}` の `list` はキー名。キーワードと同じ綴りでもリンクにしない。
     */
    public void testDoesNotLinkShapeKeys() {
        String html = documentationHtml("""
                <?php
                namespace App\\Domain;

                /** @phpstan-type Payload array{list: int, string: int} */
                final class Holder
                {
                    /** @return Payload */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);

        assertFalse("キー名をキーワードとしてリンクにしている: " + html,
                html.contains("psi_element://phpstan-type-keyword/list"));
        assertFalse("キー名をキーワードとしてリンクにしている: " + html,
                html.contains("psi_element://phpstan-type-keyword/string"));
    }

    /**
     * エイリアス名はキャメルのこともスネークのこともあり、小文字始まりという形だけでは
     * キーワードと区別できない。列挙に載っているものだけをキーワードとして扱う。
     */
    public void testDoesNotTreatLowercaseAliasNamesAsKeywords() {
        myFixture.addFileToProject("Account.php", """
                <?php
                namespace App\\Domain;

                final class Account {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /**
                 * @phpstan-type userProfile array{name: string}
                 * @phpstan-type user_data array{profile: userProfile}
                 */
                final class Holder
                {
                    /** @return user_data */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("user_data");
        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        assertFalse("スネークケースのエイリアス名をキーワード扱いしている: " + html,
                html.contains("phpstan-type-keyword/user_data"));
        assertFalse("キャメルケースのエイリアス名をキーワード扱いしている: " + html,
                html.contains("phpstan-type-keyword/userProfile"));
    }

    /**
     * キーワードに直接カーソルを当てたときも説明を出す。PHP は解決後の型
     * (`\App\Domain\Account[]`) を出すので、キーワード自体の意味は分からない。
     */
    public void testShowsDocumentationWhenTheCaretIsOnTheKeyword() {
        myFixture.addFileToProject("Account.php", """
                <?php
                namespace App\\Domain;

                final class Account {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = file.getText().indexOf("non-empty-list") + 2;

        var targets = new PhpStanTypeDocumentationTargetProvider().documentationTargets(file, offset);

        assertFalse("キーワードの documentation が返らない", targets.isEmpty());
        String html = targets.getFirst().computeDocumentation().toString();
        assertTrue("キーワードの説明が出ていない: " + html, html.contains("non-empty-list"));
        String expected = io.github.javakky.phpgloss.keyword.PhpStanTypeKeywords.find("non-empty-list")
                .orElseThrow()
                .description(com.intellij.DynamicBundle.getLocale());
        assertTrue("説明本文が出ていない: " + html, html.contains(expected));
    }

    /**
     * キーワードの上で Ctrl+B を押したら、解析ツール側の定義へ移動させる。
     */
    public void testGoToDeclarationOnKeyword() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = file.getText().indexOf("non-empty-list") + 2;
        var element = file.findElementAt(offset);

        var targets = new io.github.javakky.phpgloss.PhpStanTypeGotoDeclarationHandler()
                .getGotoDeclarationTargets(element, offset, null);

        assertNotNull("キーワードから移動できない", targets);
        assertTrue("移動先が空", targets.length > 0);
        // 単語表 (TypeTokenizer) でなく、意味を決めている場所 (Atomic::create) へ着地する
        assertEquals("意味を決めている場所を指していない",
                "psalm-Atomic.php", targets[0].getContainingFile().getName());
    }

    public void testResolvesKeywordLinkToDocumentation() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = file.getText().lastIndexOf("Payload");
        var source = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();

        var result = new PhpStanTypeDocumentationLinkHandler()
                .resolveLink(source, "psi_element://phpstan-type-keyword/non-empty-string");

        assertNotNull("キーワードのリンクが解決できていない", result);
    }

    public void testUnknownKeywordDoesNotResolve() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = file.getText().lastIndexOf("Payload");
        var source = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();

        var result = new PhpStanTypeDocumentationLinkHandler()
                .resolveLink(source, "psi_element://phpstan-type-keyword/not-a-keyword");

        assertNull("知らないキーワードを解決している", result);
    }

    /**
     * プロジェクトに Psalm / PHPStan が無くても、同梱したコピーで定義位置に辿り着ける。
     */
    public void testFallsBackToTheBundledDefinition() {
        var location = PhpStanKeywordDefinition.find(getProject(), "non-empty-string").orElseThrow();

        assertTrue("同梱のコピーを指していない: " + location.file().getName(), location.bundled());
        assertTrue("同梱ファイルが読めない", location.file().isValid());
    }

    private String documentationHtml(String source) {
        myFixture.addFileToProject("Account.php", """
                <?php
                namespace App\\Domain;

                final class Account {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        int offset = file.getText().lastIndexOf("Payload");
        return new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();
    }
}
