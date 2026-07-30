package dev.javakky.phpstantypedoc.keyword;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * カタログのすべてのキーワードから、定義とみなせる場所へ辿り着けることを確認する。
 *
 * <p>飛び先は「その語の意味を決めている場所」であってほしい。Psalm の
 * {@code TypeTokenizer::PSALM_RESERVED_WORDS} はトークナイザ用の単語表でしかないので、
 * そこにしか着地しない語は改善の余地として数える。
 */
public final class KeywordDefinitionCoverageTest extends BasePlatformTestCase {
    public void testEveryKeywordHasADefinition() {
        List<String> missing = new ArrayList<>();
        for (String keyword : PhpStanTypeKeywords.names()) {
            if (PhpStanKeywordDefinition.find(getProject(), keyword).isEmpty()) {
                missing.add(keyword);
            }
        }
        assertTrue("定義へ辿り着けないキーワードがある: " + missing, missing.isEmpty());
    }

    /**
     * 単語表にしか着地しない語を固定する。増えたら飛び先の選定が劣化している。
     *
     * <p>残っている 3 語には理由がある。`real` は TypeTokenizer 自身が `float` へ正規化しており
     * その意味でこのファイルが正しい。`pure-Closure` は ParseTreeCreator の列挙に現れるだけで、
     * どちらも単語の並びでしかない。`non-empty-countable` は型でなく assertion 用の語。
     */
    public void testFewKeywordsLandOnTheWordListOnly() {
        List<String> wordListOnly = new ArrayList<>();
        for (String keyword : PhpStanTypeKeywords.names()) {
            PhpStanKeywordDefinition.find(getProject(), keyword)
                    .filter(location -> location.file().getName().contains("TypeTokenizer"))
                    .ifPresent(location -> wordListOnly.add(keyword));
        }
        assertEquals("単語表にしか着地しない語が変わった: " + wordListOnly,
                java.util.List.of("non-empty-countable", "pure-Closure", "real"),
                wordListOnly.stream().sorted().toList());
    }
}
