package io.github.javakky.phpgloss.usages;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.Usage;
import com.intellij.find.usages.api.UsageSearchParameters;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * {@code @phpstan-type} の宣言名で使用箇所検索が成立することを確認する。
 */
public final class PhpStanAliasFindUsagesTest extends BasePlatformTestCase {
    private static final String DEFINITION = """
            <?php
            namespace App;

            /**
             * @phpstan-type UserData array{id: positive-int}
             */
            final class User
            {
                /** @param UserData $data */
                public function accept(array $data): void {}
            }
            """;

    private static final String USAGE = """
            <?php
            namespace App;

            /**
             * @phpstan-import-type UserData from User as CreateUserData
             */
            final class CreateUser
            {
                /**
                 * @param CreateUserData $payload
                 *
                 * @return CreateUserData
                 */
                public function normalize(array $payload): array
                {
                    return $payload;
                }
            }
            """;

    public void testDeclarationIsRecognisedOnTheTypeName() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, DEFINITION);
        int offset = file.getText().indexOf("@phpstan-type UserData") + "@phpstan-type ".length();

        var declaration = PhpStanAliasDeclarations.at(file, offset);

        assertTrue("宣言として認識されていない", declaration.isPresent());
        assertEquals(new PhpStanAliasSymbol("App\\User", "UserData"), declaration.get().symbol());
    }

    public void testReferenceIsNotADeclaration() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, DEFINITION);
        int offset = file.getText().indexOf("@param UserData") + "@param ".length();

        assertTrue("参照位置を宣言として扱っている", PhpStanAliasDeclarations.at(file, offset).isEmpty());
    }

    /**
     * import の別名も宣言。元の定義と同じ Symbol になり、どちらから検索しても一覧が揃う。
     */
    public void testImportedLocalNameIsADeclarationOfTheSameSymbol() {
        myFixture.addFileToProject("User.php", DEFINITION);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, USAGE);
        int offset = file.getText().indexOf("as CreateUserData") + "as ".length();

        var declaration = PhpStanAliasDeclarations.at(file, offset);

        assertTrue("import の別名が宣言として認識されていない", declaration.isPresent());
        assertEquals(new PhpStanAliasSymbol("App\\User", "UserData"), declaration.get().symbol());
    }

    public void testFindsUsagesAcrossFiles() {
        myFixture.addFileToProject("CreateUser.php", USAGE);
        var definitionFile = myFixture.configureByText(PhpFileType.INSTANCE, DEFINITION);

        Collection<? extends Usage> usages = search(
                definitionFile, new PhpStanAliasSymbol("App\\User", "UserData"));

        // 定義側の @param UserData、import 行の元名 UserData、
        // 利用側の @param CreateUserData と @return CreateUserData
        assertEquals("使用箇所の数が合わない: " + describe(usages), 4, usages.size());
    }

    /**
     * 宣言そのものは使用箇所に出さない。宣言は Find Usages の一覧で別枠に出るため。
     */
    public void testExcludesDeclarationsFromUsages() {
        var definitionFile = myFixture.configureByText(PhpFileType.INSTANCE, DEFINITION);

        Collection<? extends Usage> usages = search(
                definitionFile, new PhpStanAliasSymbol("App\\User", "UserData"));

        assertEquals("宣言を使用箇所に含めている: " + describe(usages), 1, usages.size());
    }

    public void testIgnoresUnrelatedSymbols() {
        var definitionFile = myFixture.configureByText(PhpFileType.INSTANCE, DEFINITION);

        Collection<? extends Usage> usages = search(
                definitionFile, new PhpStanAliasSymbol("App\\Other", "UserData"));

        assertTrue("別クラスのエイリアスまで拾っている: " + describe(usages), usages.isEmpty());
    }

    private Collection<? extends Usage> search(PsiFile file, SearchTarget target) {
        return new PhpStanAliasUsageSearcher().collectImmediateResults(
                new Parameters(file.getProject(), target, GlobalSearchScope.allScope(file.getProject())));
    }

    private String describe(Collection<? extends Usage> usages) {
        StringBuilder builder = new StringBuilder();
        for (Usage usage : usages) {
            if (usage instanceof com.intellij.find.usages.api.PsiUsage psiUsage) {
                builder.append(psiUsage.getFile().getName())
                        .append(psiUsage.getRange())
                        .append(' ');
            }
        }
        return builder.toString();
    }

    private record Parameters(Project project, SearchTarget target, SearchScope searchScope)
            implements UsageSearchParameters {

        @Override
        public @NotNull Project getProject() {
            return project;
        }

        @Override
        public @NotNull SearchTarget getTarget() {
            return target;
        }

        @Override
        public @NotNull SearchScope getSearchScope() {
            return searchScope;
        }

        @Override
        public boolean areValid() {
            return true;
        }
    }
}
