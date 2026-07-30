package dev.javakky.phpstantypedoc.keyword;

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
        assertTrue("説明が読めていない: " + keyword.description(), keyword.description().contains("空文字列"));
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

    public void testEveryKeywordHasToolsAndDescription() {
        for (String name : PhpStanTypeKeywords.names()) {
            var keyword = PhpStanTypeKeywords.find(name).orElseThrow();
            assertFalse(name + " に対応ツールが無い", keyword.tools().isEmpty());
            assertFalse(name + " に説明が無い", keyword.description().isBlank());
        }
    }
}
