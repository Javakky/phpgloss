package dev.javakky.phpstantypedoc;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import dev.javakky.phpstantypedoc.doc.PhpStanTypeDocumentationTargetProvider;

public final class PhpStanTypeResolverFacadeTest extends BasePlatformTestCase {
    private static final String DECLARATIONS = """
            <?php
            namespace App;

            /** @phpstan-type UserData array{id: positive-int} */
            final class User {}

            /**
             * @phpstan-import-type UserData from User as CreateUserData
             */
            final class CreateUser {}
            """;

    public void testResolvesClassAliasFromMethodPhpDoc() {
        String source = """
                <?php
                namespace App\\Domain;

                /**
                 * @phpstan-type UserData array{
                 *     id: positive-int,
                 *     name: non-empty-string
                 * }
                 */
                final class User
                {
                    /** @param UserData $data */
                    public function update(array $data): void
                    {
                    }
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        int offset = source.lastIndexOf("UserData");

        var resolved = new PhpStanTypeResolverFacade().resolveAt(file, offset);

        assertTrue(resolved.isPresent());
        assertEquals("UserData", resolved.orElseThrow().resolvedType().requestedName());
        assertTrue(resolved.orElseThrow().resolvedType().typeBody().contains("id: positive-int"));
        assertEquals("App\\Domain\\User", resolved.orElseThrow().resolvedType().definingClassFqcn());
    }

    public void testResolvesAliasDeclarationName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, DECLARATIONS);
        int offset = DECLARATIONS.indexOf("UserData");

        var resolved = new PhpStanTypeResolverFacade().resolveAt(file, offset).orElseThrow();

        assertEquals("UserData", resolved.resolvedType().requestedName());
        assertTrue(resolved.resolvedType().typeBody().contains("id: positive-int"));
        assertEquals("App\\User", resolved.resolvedType().definingClassFqcn());
    }

    public void testResolvesImportedAliasByOriginalName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, DECLARATIONS);
        int offset = DECLARATIONS.indexOf("UserData", DECLARATIONS.indexOf("@phpstan-import-type"));

        var resolved = new PhpStanTypeResolverFacade().resolveAt(file, offset).orElseThrow();

        assertEquals("UserData", resolved.resolvedType().requestedName());
        assertTrue(resolved.resolvedType().imported());
        assertEquals("App\\User", resolved.resolvedType().definingClassFqcn());
    }

    public void testResolvesImportedAliasByLocalName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, DECLARATIONS);
        int offset = DECLARATIONS.indexOf("CreateUserData");

        var resolved = new PhpStanTypeResolverFacade().resolveAt(file, offset).orElseThrow();

        assertEquals("CreateUserData", resolved.resolvedType().requestedName());
        assertEquals("UserData", resolved.resolvedType().originalName());
        assertTrue(resolved.resolvedType().imported());
        assertEquals("App\\User", resolved.resolvedType().definingClassFqcn());
    }

    public void testProvidesDocumentationAndDefinitionNavigation() {
        String source = """
                <?php
                /** @phpstan-type UserData array{id: positive-int} */
                final class User
                {
                    /** @return UserData */
                    public function current(): array
                    {
                        return [];
                    }
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        int referenceOffset = source.lastIndexOf("UserData");
        int definitionOffset = source.indexOf("UserData");

        var documentationTargets = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, referenceOffset);
        assertSize(1, documentationTargets);
        assertNotNull(documentationTargets.getFirst().computeDocumentation());
        assertNotNull(documentationTargets.getFirst().getNavigatable());

        var declarationTargets = new PhpStanTypeGotoDeclarationHandler()
                .getGotoDeclarationTargets(file.findElementAt(referenceOffset), referenceOffset, myFixture.getEditor());
        assertNotNull(declarationTargets);
        assertSize(1, declarationTargets);
        assertTrue(declarationTargets[0].getTextRange().contains(definitionOffset));
    }
}
