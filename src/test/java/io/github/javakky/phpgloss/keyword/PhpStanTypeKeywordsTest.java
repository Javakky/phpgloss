package io.github.javakky.phpgloss.keyword;

import junit.framework.TestCase;

/**
 * 型キーワードのカタログが読めることと、両ツールの語彙を取りこぼしていないことを確認する。
 */
public final class PhpStanTypeKeywordsTest extends TestCase {
    public void testLoadsDescription() {
        var keyword = PhpStanTypeKeywords.find("non-empty-string").orElseThrow();

        assertEquals("non-empty-string", keyword.name());
        assertTrue("対応ツールが読めていない: " + keyword.tools(), keyword.tools().contains("psalm"));
        assertTrue("対応ツールが読めていない: " + keyword.tools(), keyword.tools().contains("phpstan"));
        assertTrue("日本語が読めていない: " + keyword.japanese(), keyword.japanese().contains("空文字列"));
        assertTrue("英語原文が読めていない: " + keyword.english(),
                keyword.english().startsWith("Denotes a string"));
    }

    public void testDistinguishesToolSpecificKeywords() {
        assertEquals(java.util.List.of("phpstan"),
                PhpStanTypeKeywords.find("uppercase-string").orElseThrow().tools());
        assertEquals(java.util.List.of("psalm"),
                PhpStanTypeKeywords.find("interface-string").orElseThrow().tools());
    }

    public void testHeadingTextIsNotMistakenForAKeyword() {
        assertTrue(PhpStanTypeKeywords.find("PHPStan").isEmpty());
    }

    /**
     * 実際に書かれうるキーワードを代表で押さえる。ユーザー報告で出たものと、
     * 説明が無いと意味を取りにくいものを選んでいる。
     */
    public void testCoversTheKeywordsSeenInPractice() {
        for (String name : new String[]{
                "non-empty-list", "positive-int", "non-empty-string", "class-string",
                "literal-string", "array-key", "list", "never", "mixed", "key-of", "value-of"}) {
            assertTrue(name + " がカタログに無い", PhpStanTypeKeywords.find(name).isPresent());
        }
    }

    public void testEveryKeywordHasToolsAndBothLanguages() {
        for (String name : PhpStanTypeKeywords.names()) {
            var keyword = PhpStanTypeKeywords.find(name).orElseThrow();
            assertFalse(name + " に対応ツールが無い", keyword.tools().isEmpty());
            assertFalse(name + " に英語の説明が無い", keyword.english().isBlank());
            assertFalse(name + " に日本語の説明が無い", keyword.japanese().isBlank());
        }
    }

    /**
     * 表示は IDE の言語で切り替える。日本語訳は独自なので、英語環境では上流の原文を出す。
     */
    public void testPicksTheDescriptionByLocale() {
        var keyword = PhpStanTypeKeywords.find("non-empty-string").orElseThrow();

        assertEquals(keyword.japanese(), keyword.description(java.util.Locale.JAPANESE));
        assertEquals(keyword.english(), keyword.description(java.util.Locale.ENGLISH));
    }

    /**
     * 日本語訳は言い差しや体言止めで終えない。読み手に係り先を委ねる形になるため。
     */
    public void testJapaneseSentencesEndWithAVerb() {
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (String name : PhpStanTypeKeywords.names()) {
            String japanese = PhpStanTypeKeywords.find(name).orElseThrow().japanese();
            for (String sentence : japanese.split("。")) {
                String trimmed = sentence.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                char last = trimmed.charAt(trimmed.length() - 1);
                if (last == 'る' || last == 'う' || last == 'く' || last == 'す'
                        || last == 'つ' || last == 'ぬ' || last == 'ぶ' || last == 'む'
                        || last == 'だ' || last == 'い' || last == 'ず' || last == 'ん') {
                    continue;
                }
                offenders.add(name + ": " + trimmed);
            }
        }
        assertTrue("動詞で終わっていない日本語がある: " + offenders, offenders.isEmpty());
    }
}
