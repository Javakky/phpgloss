package io.github.javakky.phpgloss.shape;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

/**
 * array shape のキー補完を確認する。ローカル定義は PHP プラグインが出すので、
 * 別ファイル import で同じ候補が並ぶことがこのプラグインの寄与になる。
 */
public final class ShapeKeyCompletionTest extends BasePlatformTestCase {
    public void testCompletesKeysForLocalShape() {
        myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App;

                /** @phpstan-type LocalShape array{alpha: int, beta: string} */
                final class Local
                {
                    /** @param LocalShape $shape */
                    public function consume(array $shape): void
                    {
                        $x = $shape['<caret>'];
                    }
                }
                """);

        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "alpha", "beta");
    }

    public void testCompletesKeysForCrossFileImport() {
        myFixture.addFileToProject("Domain.php", """
                <?php
                namespace App\\Domain;

                /** @phpstan-type UserData array{alpha: int, beta: string} */
                final class User {}
                """);
        myFixture.configureByText(PhpFileType.INSTANCE, """
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
                        $x = $payload['<caret>'];
                    }
                }
                """);

        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "alpha", "beta");
    }
}
