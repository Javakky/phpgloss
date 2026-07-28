package dev.javakky.phpstantypedoc.shape;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression;

import java.util.Set;

/**
 * 別ファイルから `@phpstan-import-type` した array shape のキーアクセスが、
 * 値の型として解決されることを確認する。同一ファイルの定義は PHP プラグインが解決するので、
 * このプラグインの寄与は別ファイル import に現れる。
 */
public final class ImportedArrayShapeTest extends BasePlatformTestCase {
    private static final String DOMAIN = """
            <?php
            namespace App\\Domain;

            /** @phpstan-type UserData array{id: positive-int, name: non-empty-string} */
            final class User {}
            """;

    public void testResolvesKeyTypeForCrossFileImport() {
        myFixture.addFileToProject("Domain.php", DOMAIN);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Service;

                use App\\Domain\\User;

                /**
                 * @phpstan-import-type UserData from User as CreateUserData
                 */
                final class CreateUser
                {
                    /** @param CreateUserData $payload */
                    public function run(array $payload): void
                    {
                        $name = $payload['name'];
                    }
                }
                """);

        Set<String> types = firstKeyAccessTypes(file);

        assertTrue("string に解決されていない: " + types, types.contains("\\string"));
    }

    public void testResolvesKeyTypeForSameFileImport() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App;

                /** @phpstan-type LocalShape array{a: int, b: string} */
                final class Local {}

                /**
                 * @phpstan-import-type LocalShape from Local as ImportedShape
                 */
                final class Consumer
                {
                    /** @param ImportedShape $imported */
                    public function consume(array $imported): void
                    {
                        $value = $imported['b'];
                    }
                }
                """);

        Set<String> types = firstKeyAccessTypes(file);

        assertTrue("string に解決されていない: " + types, types.contains("\\string"));
    }

    private Set<String> firstKeyAccessTypes(com.intellij.psi.PsiFile file) {
        ArrayAccessExpression access =
                PsiTreeUtil.findChildrenOfType(file, ArrayAccessExpression.class).iterator().next();
        return access.getType().global(getProject()).getTypes();
    }
}
