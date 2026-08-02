package io.github.javakky.phpgloss.keyword;

import junit.framework.TestCase;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * カタログが Psalm / PHPStan の予約語を取りこぼしていないことを、同梱したソースそのものから確かめる。
 *
 * <p>キーワードかどうかは綴りの形では決められない (エイリアス名もキャメル / スネークで小文字始まりに
 * なる)。列挙が唯一の判断材料なので、その列挙が本家とずれていないことをテストで固定する。
 * 同梱ソースを更新したときに、追記漏れがここで落ちる。
 */
public final class KeywordCatalogCoverageTest extends TestCase {
    private static final Pattern PSALM_RESERVED = Pattern.compile("'([^']+)' => true,");
    private static final Pattern PHPSTAN_CASE = Pattern.compile("case '([^']+)':");

    /**
     * PHPStan が内部で使う型。PHPDoc に書けないのでカタログには載せない。
     */
    private static final Set<String> INTERNAL = Set.of("__stringandstringable", "__stringnotstringable");

    public void testCoversPsalmReservedWords() {
        assertCovered(extract("psalm-TypeTokenizer.php", PSALM_RESERVED));
    }

    public void testCoversPhpStanIdentifiers() {
        assertCovered(extract("phpstan-TypeNodeResolver.php", PHPSTAN_CASE));
    }

    private void assertCovered(Set<String> reserved) {
        assertFalse("同梱ソースから予約語を取り出せていない", reserved.isEmpty());
        List<String> missing = reserved.stream()
                .filter(word -> !INTERNAL.contains(word))
                .filter(word -> PhpStanTypeKeywords.find(word).isEmpty())
                .toList();
        assertTrue("カタログに無い予約語がある: " + missing, missing.isEmpty());
    }

    private Set<String> extract(String fileName, Pattern pattern) {
        String source = PhpStanBundledSource.text(fileName).orElseThrow();
        Set<String> words = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            words.add(matcher.group(1));
        }
        return words;
    }
}
