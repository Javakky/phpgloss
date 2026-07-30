package dev.javakky.phpstantypedoc.doc;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

import java.util.List;

/**
 * 変数のホバーで、解けた型と同じ union に並ぶ抽象型が落ちることを確認する。
 *
 * <p>PHP 標準の表示は実測で `$result: array|int[]|list` になる。union の成分はプロバイダ側から
 * 消せないので、表示の側で整理している。
 */
public final class VariableTypeUnionHoverTest extends BasePlatformTestCase {
    private static final String GENERIC = """
            <?php
            namespace Probe;

            /**
             * @template V
             * @param list<V> $items
             * @phpstan-return list<V>
             */
            function pass(array $items): array
            {
                return $items;
            }

            function probe(): void
            {
                $result = pass([1, 2, 3]);
                $plain = 1;
            }
            """;

    private static final String DOMAIN = """
            <?php
            namespace App\\Domain;

            /** @phpstan-type UserData array{name: non-empty-string} */
            final class User {}
            """;

    private static final String SHAPE = """
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
            """;

    /** 実測の `{list<\int>, \array, \int[]}` が `list<int>` だけになる。 */
    public void testDropsTheAbstractTypesFromAResolvedList() {
        PsiFile file = myFixture.configureByText(PhpFileType.INSTANCE, GENERIC);

        String html = documentationAt(file, GENERIC.indexOf("$result") + 2);

        assertTrue("変数名が出ていない: " + html, html.contains("$result"));
        assertTrue("解けた型が出ていない: " + html, html.contains("list&lt;int&gt;"));
        assertFalse("抽象型が残っている: " + html, html.contains("array"));
        assertFalse("複数形が残っている: " + html, html.contains("int[]"));
    }

    /** 型が 1 つだけなら整理しても減らない。PHP 標準の表示に任せる。 */
    public void testLeavesTheStandardHoverAloneWhenNothingIsDropped() {
        PsiFile file = myFixture.configureByText(PhpFileType.INSTANCE, GENERIC);

        List<? extends DocumentationTarget> targets = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, GENERIC.indexOf("$plain") + 2);

        assertEmpty(targets);
    }

    /** キーアクセスに PhpStorm が常に足す `mixed` が落ちる。 */
    public void testDropsMixedFromAShapeKeyAccess() {
        myFixture.addFileToProject("Domain.php", DOMAIN);
        PsiFile file = myFixture.configureByText(PhpFileType.INSTANCE, SHAPE);

        String html = documentationAt(file, SHAPE.indexOf("$name") + 2);

        assertTrue("変数名が出ていない: " + html, html.contains("$name"));
        assertTrue("shape の値の型が出ていない: " + html, html.contains("string"));
        assertFalse("mixed が残っている: " + html, html.contains("mixed"));
    }

    /** エイリアスが引ける変数は従来どおりエイリアスの表示になる。 */
    public void testKeepsTheAliasHoverForAnAliasedVariable() {
        myFixture.addFileToProject("Domain.php", DOMAIN);
        PsiFile file = myFixture.configureByText(PhpFileType.INSTANCE, SHAPE);

        String html = documentationAt(file, SHAPE.lastIndexOf("$payload") + 2);

        assertTrue("エイリアス名が出ていない: " + html, html.contains("CreateUserData"));
        assertTrue("型本体が出ていない: " + html, html.contains("name: ") && html.contains("non-empty-string"));
    }

    private String documentationAt(PsiFile file, int offset) {
        return new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst()
                .computeDocumentation()
                .toString();
    }
}
