package io.github.javakky.phpgloss.doc;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

/**
 * 型本体に現れるエイリアス名が、そのエイリアスの documentation へのリンクになることを確認する。
 * `Mark = Bold|Italic` の `Bold` や `list<Mark>` の `Mark` から中身を辿れるようにするため。
 */
public final class TypeBodyAliasLinkTest extends BasePlatformTestCase {
    public void testLinksAliasNamesInUnion() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /**
                 * @phpstan-type Bold array{type: 'bold'}
                 * @phpstan-type Italic array{type: 'italic'}
                 * @phpstan-type Mark Bold|Italic
                 */
                final class Holder
                {
                    /** @return Mark */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("Mark");

        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        assertTrue("Bold がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\Holder::Bold"));
        assertTrue("Italic がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\Holder::Italic"));
    }

    /** 送り側が作った URL を受け側がそのまま解決できることを確認する。 */
    public void testAliasLinkFromTypeBodyResolves() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /**
                 * @phpstan-type Bold array{type: 'bold'}
                 * @phpstan-type Italic array{type: 'italic'}
                 * @phpstan-type Mark Bold|Italic
                 */
                final class Holder
                {
                    /** @return Mark */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("Mark");
        DocumentationTarget source = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();
        String html = source.computeDocumentation().toString();
        assertTrue("前提となる Bold のリンクが無い: " + html,
                html.contains("psi_element://\\App\\Domain\\Holder::Bold"));

        var result = new PhpStanTypeDocumentationLinkHandler()
                .resolveLink(source, "psi_element://\\App\\Domain\\Holder::Bold");

        assertNotNull("型本体のエイリアスリンクが解決できていない", result);
    }

    public void testLinksAliasInsideGenericsButNotShapeKeys() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /**
                 * @phpstan-type Bold array{type: 'bold'}
                 * @phpstan-type Italic array{type: 'italic'}
                 * @phpstan-type Mark Bold|Italic
                 * @phpstan-type TextNode array{type: 'text', text: string, marks?: list<Mark>}
                 */
                final class Holder
                {
                    /** @return TextNode */
                    public function get(): array
                    {
                        return [];
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("TextNode");

        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        assertTrue("ジェネリクス引数の Mark がリンクになっていない: " + html,
                html.contains("psi_element://\\App\\Domain\\Holder::Mark"));
        assertFalse("shape key の type がリンクになっている: " + html, html.contains("Holder::type"));
        assertFalse("shape key の text がリンクになっている: " + html, html.contains("Holder::text"));
        assertFalse("shape key の marks がリンクになっている: " + html, html.contains("Holder::marks"));
    }

    /** import したエイリアスでは、リンクの owner は import 元ではなく定義元クラスになる。 */
    public void testLinksAliasOfImportedTypeWithDefiningOwner() {
        myFixture.addFileToProject("User.php", """
                <?php
                namespace App;

                /**
                 * @phpstan-type Bold array{type: 'bold'}
                 * @phpstan-type Italic array{type: 'italic'}
                 * @phpstan-type Mark Bold|Italic
                 */
                final class User {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App;

                /**
                 * @phpstan-import-type Mark from User
                 */
                final class CreateUser
                {
                    /** @param Mark $m */
                    public function run(array $m): void
                    {
                    }
                }
                """);
        int offset = file.getText().lastIndexOf("Mark");

        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        assertTrue("定義元クラスを owner にしたリンクになっていない: " + html,
                html.contains("psi_element://\\App\\User::Bold"));
    }

    /** エイリアスにもクラスにも該当しない名前はリンクにしない (現状維持)。 */
    public void testDoesNotLinkUnknownName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Domain;

                /** @phpstan-type Payload array{node: Unknown} */
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

        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        assertTrue("型本体が描画されていない: " + html, html.contains("Unknown"));
        assertFalse("未知の名前がエイリアスリンクになっている: " + html,
                html.contains("Holder::Unknown"));
        assertFalse("未知の名前がクラスリンクになっている: " + html,
                html.contains("psi_element://\\App\\Domain\\Unknown"));
    }
}
