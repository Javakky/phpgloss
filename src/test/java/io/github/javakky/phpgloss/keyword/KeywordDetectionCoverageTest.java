package io.github.javakky.phpgloss.keyword;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;

import java.util.ArrayList;
import java.util.List;

/**
 * カタログにある語が、型が書かれた位置で実際に見つかること。
 *
 * <p>これまでのテストは「カタログに載っている」「定義に辿り着ける」を確かめていたが、
 * 「型の位置に置いたときに検出されるか」は一部の書き方でしか見ていなかった。
 */
public final class KeywordDetectionCoverageTest extends BasePlatformTestCase {
    /** 型引数の中でしか意味を持たない語。単独では型の位置に書けない。 */
    private static final List<String> BOUNDS_ONLY = List.of("min", "max");

    public void testEveryKeywordIsFoundWhereATypeIsWritten() {
        List<String> missing = new ArrayList<>();
        for (String keyword : PhpStanTypeKeywords.names()) {
            if (BOUNDS_ONLY.contains(keyword)) {
                continue;
            }
            String source = """
                    <?php
                    /**
                     * @param %s $value
                     */
                    function probe($value): void {}
                    """.formatted(keyword);
            var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
            int offset = source.indexOf("@param " + keyword) + "@param ".length() + 1;
            if (PhpStanKeywordAtCaret.find(file, offset).isEmpty()) {
                missing.add(keyword);
            }
        }
        assertTrue("型の位置に置いても見つからない語がある: " + missing, missing.isEmpty());
    }

    /** 型引数の中でも見つかること。`list<non-empty-string>` の内側など。 */
    public void testKeywordsAreFoundInsideATypeArgument() {
        List<String> missing = new ArrayList<>();
        for (String keyword : List.of("non-empty-string", "positive-int", "class-string",
                "array-key", "literal-string", "int", "string", "mixed")) {
            String source = """
                    <?php
                    /**
                     * @param list<%s> $values
                     */
                    function probe(array $values): void {}
                    """.formatted(keyword);
            var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
            int offset = source.indexOf("list<" + keyword) + "list<".length() + 1;
            if (PhpStanKeywordAtCaret.find(file, offset).isEmpty()) {
                missing.add(keyword);
            }
        }
        assertTrue("型引数の中で見つからない語がある: " + missing, missing.isEmpty());
    }
}
