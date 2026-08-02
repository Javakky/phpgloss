package io.github.javakky.phpgloss.shape;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

/**
 * shape の値がクラス型のとき、キーアクセスからメンバ補完が出ることを確認する。
 *
 * <p>キーアクセスの型には PHP が素の型として `mixed` を混ぜるため
 * `{\App\Domain\Account, \mixed, ?}` になるが、メンバ補完はクラス型から出る。
 * `mixed` を取り除く必要はないことの根拠になる。
 */
public final class ShapeValueMemberCompletionTest extends BasePlatformTestCase {
    private static final String DOMAIN = """
            <?php
            namespace App\\Domain;

            final class Account
            {
                public function displayName(): string { return ''; }
                public function isActive(): bool { return true; }
            }

            /** @phpstan-type UserData array{account: Account, name: non-empty-string} */
            final class User {}
            """;

    public void testDiagnoseMemberCompletionOnImportedShapeValue() {
        myFixture.addFileToProject("Domain.php", DOMAIN);
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
                        $payload['account']-><caret>;
                    }
                }
                """);

        var access = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
                myFixture.getFile(), com.jetbrains.php.lang.psi.elements.ArrayAccessExpression.class)
                .iterator().next();

        assertTrue("Account に解決されていない: " + access.getType().global(getProject()).getTypes(),
                access.getType().global(getProject()).getTypes().contains("\\App\\Domain\\Account"));

        myFixture.completeBasic();

        assertContainsElements(myFixture.getLookupElementStrings(), "displayName", "isActive");
    }
}
