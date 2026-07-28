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

    public void testShowsVariableWithTypeBodyAndClassLink() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.lastIndexOf("$payload") + 2;

        String html = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();

        // `$payload: CreateUserData` の形で、型本体も併せて出る
        assertTrue("変数名が出ていない: " + html, html.contains("$payload"));
        assertTrue("エイリアス名が出ていない: " + html, html.contains("CreateUserData"));
        assertTrue("型本体が出ていない: " + html, html.contains("id: positive-int"));
        // エイリアス名は定義元クラスへのリンクになっている
        assertTrue("定義元クラスへのリンクが無い: " + html, html.contains("psi_element://\\App\\User"));
    }

    public void testProvidesDocumentationOnVariable() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.lastIndexOf("$payload") + 2;

        var targets = new PhpStanTypeDocumentationTargetProvider().documentationTargets(file, offset);

        assertSize(1, targets);
        assertNotNull(targets.getFirst().computeDocumentation());
    }
}
