package dev.javakky.phpstantypedoc.inspection;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocParamTag;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.inspections.phpdoc.PhpDocSignatureInspection;

public final class PhpStanTypeAliasInspectionSuppressorTest extends BasePlatformTestCase {
    private static final String DOMAIN = """
            <?php
            namespace App\\Domain;

            /** @phpstan-type UserData array{id: positive-int, name: non-empty-string} */
            final class User {}
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(PhpDocSignatureInspection.class);
    }

    public void testSuppressesCompatibleCrossFileImportedArrayShape() {
        myFixture.addFileToProject("Domain.php", DOMAIN);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Service;

                use App\\Domain\\User;

                /** @phpstan-import-type UserData from User as CreateUserData */
                final class CreateUser
                {
                    /** @param CreateUserData $payload */
                    public function run(array $payload): void {}
                }
                """);

        PhpDocParamTag tag = PsiTreeUtil.findChildOfType(file, PhpDocParamTag.class);
        assertNotNull(tag);
        String shortName = new PhpDocSignatureInspection().getShortName();
        assertEquals("PhpDocSignatureInspection", shortName);
        assertTrue(new PhpStanTypeAliasInspectionSuppressor().isSuppressedFor(tag, shortName));
        myFixture.checkHighlighting(false, false, true);
    }

    public void testKeepsWarningForIncompatibleNativeType() {
        myFixture.addFileToProject("Domain.php", DOMAIN);
        myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Service;

                use App\\Domain\\User;

                /** @phpstan-import-type UserData from User as CreateUserData */
                final class CreateUser
                {
                    /** <weak_warning descr="Argument type does not match the declared">@param CreateUserData $payload</weak_warning> */
                    public function run(string $payload): void {}
                }
                """);

        myFixture.checkHighlighting(false, false, true);
    }

    public void testSuppressesCompatibleLocalArrayShape() {
        myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App;

                /** @phpstan-type Payload array{id: positive-int} */
                final class Service
                {
                    /** @param Payload $payload */
                    public function run(array $payload): void {}
                }
                """);

        myFixture.checkHighlighting(false, false, true);
    }
}
