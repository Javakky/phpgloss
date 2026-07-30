package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpClass;

/**
 * エイリアス名のリンク先となるクラスの documentation に、そのクラスの型エイリアスが載ることを
 * 確認する。エイリアス専用の表示へ遷移する手段が platform に無いため、行き先で中身を読ませる。
 */
public final class ClassAliasDocumentationTest extends BasePlatformTestCase {
    public void testClassDocumentationCarriesItsAliases() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App;

                /**
                 * @phpstan-type UserData array{id: positive-int, name: non-empty-string}
                 */
                final class User {}
                """);
        PhpClass phpClass = PsiTreeUtil.findChildOfType(file, PhpClass.class);

        var target = new PhpStanClassDocumentationTargetProvider()
                .documentationTarget(phpClass, phpClass);

        assertNotNull("クラスの documentation target が返らない", target);
        String html = target.computeDocumentation().toString();
        assertTrue("エイリアス名が出ていない: " + html, html.contains("UserData"));
        // キーワードはリンクになるため、素の文字列でなく本文中の出現で見る
        assertTrue("型本体が出ていない: " + html, html.contains("id: ") && html.contains("positive-int"));
    }

    public void testImportedAliasIsShownOnTheImportingClass() {
        myFixture.addFileToProject("Domain.php", """
                <?php
                namespace App\\Domain;

                /** @phpstan-type UserData array{id: positive-int} */
                final class User {}
                """);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App\\Service;

                use App\\Domain\\User;

                /**
                 * @phpstan-import-type UserData from User as CreateUserData
                 */
                final class CreateUser {}
                """);
        PhpClass phpClass = PsiTreeUtil.findChildOfType(file, PhpClass.class);

        var target = new PhpStanClassDocumentationTargetProvider()
                .documentationTarget(phpClass, phpClass);

        assertNotNull("import を持つクラスの documentation target が返らない", target);
        String html = target.computeDocumentation().toString();
        assertTrue("import した別名が出ていない: " + html, html.contains("CreateUserData"));
        assertTrue("import 元が出ていない: " + html, html.contains("Imported type: UserData"));
        // キーワードはリンクになるため、素の文字列でなく本文中の出現で見る
        assertTrue("型本体が出ていない: " + html, html.contains("id: ") && html.contains("positive-int"));
    }

    public void testLeavesClassesWithoutAliasesToThePhpProvider() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace App;

                /** Plain class. */
                final class Plain {}
                """);
        PhpClass phpClass = PsiTreeUtil.findChildOfType(file, PhpClass.class);

        assertNull("エイリアスの無いクラスまで奪っている",
                new PhpStanClassDocumentationTargetProvider().documentationTarget(phpClass, phpClass));
    }
}
