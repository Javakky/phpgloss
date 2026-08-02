package io.github.javakky.phpgloss.doc;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.jetbrains.php.lang.PhpFileType;

/**
 * 型本体に現れるクラス名が `psi_element://` リンクになることを確認する。
 * `array{users: non-empty-list<User>}` の `User` からクラス定義へ飛べるようにするため。
 */
public final class TypeBodyClassLinkTest extends BasePlatformTestCase {
    public void testLinksClassNameInTypeBody() {
        myFixture.addFileToProject("Account.php", """
                <?php
                namespace App\\Domain;

                final class Account {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /** @phpstan-type Payload array{accounts: non-empty-list<Account>, label: non-empty-string} */
                final class Holder
                {
                    /** @return Payload */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = myFixture.getFile().getText().lastIndexOf("Payload");

        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        assertTrue("Account がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\Account"));
        // PHPStan の型キーワードはリンクにしない (すべて小文字なので候補にならない)
        assertFalse("型キーワードがリンクになっている: " + html,
                html.contains("psi_element://\\App\\Domain\\non-empty-list"));
    }

    /**
     * PHP は配列型のリンクを要素クラス + {@code []} で出す。素のクラス名として引けないと
     * クリックが「不正な形式の URL です」になる。
     */
    public void testResolvesArrayTypeLink() {
        myFixture.addFileToProject("Account.php", """
                <?php
                namespace App\\Domain;

                /** Account documentation. */
                final class Account {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /** @phpstan-type Payload array{accounts: non-empty-list<Account>} */
                final class Holder
                {
                    /** @return Payload */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("Payload");
        DocumentationTarget source = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();

        var result = new PhpStanTypeDocumentationLinkHandler()
                .resolveLink(source, "psi_element://\\App\\Domain\\Account[]");

        assertNotNull("配列型のリンクが解決できていない", result);
    }

    public void testResolvesClassLinkFromCustomDocumentationTarget() {
        myFixture.addFileToProject("Account.php", """
                <?php
                namespace App\\Domain;

                /** Account documentation. */
                final class Account {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /** @phpstan-type Payload array{account: Account} */
                final class Holder
                {
                    /** @return Payload */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("Payload");
        DocumentationTarget source = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();

        var result = new PhpStanTypeDocumentationLinkHandler()
                .resolveLink(source, "psi_element://\\App\\Domain\\Account");

        assertNotNull("独自 documentation target から PSI link を解決できていない", result);
    }
}
