package io.github.javakky.phpgloss.keyword;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

/**
 * 型の書かれ方が変わっても、キーワードとして検出できることを確認する。
 *
 * <p>union / intersection / 括弧 / 複数行 のいずれも実際に書かれる形。
 */
public final class KeywordTypePositionShapesTest extends BasePlatformTestCase {
    public void testUnion() {
        assertDetected("""
                    /** @return non-empty-string|null */
                """, "non-empty-string");
    }

    public void testIntersection() {
        assertDetected("""
                    /** @return non-empty-array&\\Countable */
                """, "non-empty-array");
    }

    public void testParenthesised() {
        assertDetected("""
                    /** @param (int|non-empty-string) $value */
                """, "non-empty-string");
    }

    public void testMultilineUnion() {
        assertDetected("""
                    /**
                     * @return non-empty-string
                     *     |null
                     */
                """, "non-empty-string");
    }

    public void testMultilineShape() {
        assertDetected("""
                    /**
                     * @return array{
                     *     users: non-empty-list<\\App\\User>
                     * }
                     */
                """, "non-empty-list");
    }

    public void testMultilineAliasBody() {
        assertDetected("""
                    /**
                     * @phpstan-type Payload array{
                     *     label: non-empty-string
                     * }
                     */
                """, "non-empty-string");
    }

    private void assertDetected(String docBlock, String keyword) {
        String source = """
                <?php
                namespace App;

                final class Holder
                {
                %s    public function run(int $value): string
                    {
                        return '';
                    }
                }
                """.formatted(docBlock);
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        int offset = source.indexOf(keyword) + 1;

        var found = PhpStanKeywordAtCaret.find(file, offset);

        assertTrue(keyword + " を検出できない\n" + source, found.isPresent());
        assertEquals(keyword, found.get().name());
    }
}
