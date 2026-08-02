package io.github.javakky.phpgloss.doc;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

public final class PhpFunctionDocumentationTargetTest extends BasePlatformTestCase {
    private static final String SOURCE = """
                <?php
                namespace App;

                /** @phpstan-type UserData array{id: positive-int} */
                final class User {}

                /**
                 * @phpstan-type LocalData array{label: non-empty-string}
                 * @phpstan-import-type UserData from User as CreateUserData
                 */
                final class LocalTypes {}

                /**
                 * @phpstan-import-type UserData from User as CreateUserData
                 * @phpstan-import-type LocalData from LocalTypes
                 */
                final class CreateUser
                {
                    /**
                     * @param array $payload
                     * @phpstan-param CreateUserData $payload
                     * @param LocalData $local
                     * @param string $plain
                     * @return CreateUserData
                     */
                    public function normalize(array $payload, array $local, string $plain): array
                    {
                        return $payload;
                    }
                }
                """;

    public void testAddsAliasLinksToPhpParametersAndReturns() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.indexOf("normalize") + 2;

        var targets = new PhpStanTypeDocumentationTargetProvider().documentationTargets(file, offset);

        assertSize(1, targets);
        assertInstanceOf(targets.getFirst(), PhpFunctionDocumentationTarget.class);
        String html = targets.getFirst().computeDocumentation().toString();
        assertTrue("PHP の signature が失われた: " + html, html.contains("function"));
        assertTrue("Parameters section が失われた: " + html, html.contains("Parameters:"));
        assertTrue("Returns section が失われた: " + html, html.contains("Returns:"));
        assertTrue("Source section が失われた: " + html, html.contains("Source:"));
        assertTrue("phpstan-param が通常の @param より優先されていない: " + html,
                html.contains(">CreateUserData</a>"));
        assertTrue("複数 parameter の alias が表示されていない: " + html,
                html.contains(">LocalData</a>"));
        assertTrue("return alias が表示されていない: " + html,
                occurrences(html, ">CreateUserData</a>") >= 2);
        // alias 名はエイリアス自身の documentation へのリンクにする
        // alias 名はエイリアス自身の documentation へ遷移するリンクにする
        assertTrue("alias link が生成されていない: " + html,
                html.contains("psi_element://\\App\\CreateUser::CreateUserData"));
        assertTrue("複数 alias の link が生成されていない: " + html,
                html.contains("psi_element://\\App\\CreateUser::LocalData"));
        assertFalse("通常型まで alias link になっている: " + html,
                html.contains("::string"));
    }

    public void testResolvesAliasLinkFromFunctionDocumentation() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        int offset = SOURCE.indexOf("normalize") + 2;
        var source = new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, offset).getFirst();

        var classLink = new PhpStanTypeDocumentationLinkHandler().resolveLink(
                source,
                "psi_element://\\App\\CreateUser"
        );
        assertNotNull("function documentation のクラスリンクを解決できていない", classLink);
    }

    public void testLeavesFunctionsWithoutResolvedAliasesToPhpProvider() {
        String source = """
                <?php
                /** @param string $name */
                function greet(string $name): string
                {
                    $formatter = function (string $value): string {
                        return $value;
                    };
                    return $formatter($name);
                }
                """;
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);

        assertEmpty(new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, source.indexOf("greet") + 2));
        assertEmpty(new PhpStanTypeDocumentationTargetProvider()
                .documentationTargets(file, source.indexOf("function (") + 2));
    }

    private int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
