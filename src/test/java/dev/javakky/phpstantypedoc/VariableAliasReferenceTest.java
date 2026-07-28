package dev.javakky.phpstantypedoc;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import dev.javakky.phpstantypedoc.doc.PhpStanTypeDocumentationTargetProvider;

/**
 * 変数にキャレットを当てたとき、その変数を宣言している `@param` のエイリアスが引けることを確認する。
 * PHP プラグインは解決済みの型 (`array`) しか出さないので、エイリアス名は変数側から辿れなかった。
 */
public final class VariableAliasReferenceTest extends BasePlatformTestCase {
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
                /** @param CreateUserData $payload */
                public function run(array $payload): void
                {
                    $copy = $payload;
                }
            }
            """;

    public void testResolvesAliasFromVariableUsage() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.lastIndexOf("$payload") + 2;

        var resolved = new PhpStanTypeResolverFacade().resolveAt(file, offset).orElseThrow();

        assertEquals("CreateUserData", resolved.resolvedType().requestedName());
        assertEquals("UserData", resolved.resolvedType().originalName());
        assertTrue(resolved.resolvedType().typeBody().contains("id: positive-int"));
        assertEquals("App\\User", resolved.resolvedType().definingClassFqcn());
    }

    public void testResolvesAliasFromParameterDeclaration() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.indexOf("array $payload): void") + "array $".length();

        var resolved = new PhpStanTypeResolverFacade().resolveAt(file, offset).orElseThrow();

        assertEquals("CreateUserData", resolved.resolvedType().requestedName());
    }

    public void testShowsVariableWithClickableAliasName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.lastIndexOf("$payload") + 2;

        var target = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();
        String html = target.computeDocumentation().toString();

        // `$payload: CreateUserData` の形で、型名がリンクになっている
        assertTrue("変数名が出ていない: " + html, html.contains("$payload"));
        assertTrue("エイリアス名が出ていない: " + html, html.contains("CreateUserData"));
        assertTrue("展開リンクが無い: " + html,
                html.contains(dev.javakky.phpstantypedoc.doc.PhpStanTypeHtmlRenderer.EXPAND_LINK));
        // 初期表示では型本体を出さない (クリックで展開する)
        assertFalse("初期表示に型本体が出ている: " + html, html.contains("id: positive-int"));
    }

    public void testExpandLinkShowsTypeBody() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.lastIndexOf("$payload") + 2;

        var target = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();
        var resolvedLink = new dev.javakky.phpstantypedoc.doc.PhpStanTypeLinkHandler()
                .resolveLink(target, dev.javakky.phpstantypedoc.doc.PhpStanTypeHtmlRenderer.EXPAND_LINK);
        assertNotNull("リンクが解決されない", resolvedLink);

        var expanded = ((dev.javakky.phpstantypedoc.doc.PhpStanTypeDocumentationTarget) target).expanded();
        assertTrue("展開後に型本体が出ていない",
                expanded.computeDocumentation().toString().contains("id: positive-int"));
    }

    public void testProvidesDocumentationOnVariable() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.lastIndexOf("$payload") + 2;

        var targets = new PhpStanTypeDocumentationTargetProvider().documentationTargets(file, offset);

        assertSize(1, targets);
        assertNotNull(targets.getFirst().computeDocumentation());
    }
}
