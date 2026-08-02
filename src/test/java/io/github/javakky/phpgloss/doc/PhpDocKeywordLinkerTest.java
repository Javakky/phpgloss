package io.github.javakky.phpgloss.doc;

import junit.framework.TestCase;

/**
 * PHP が型キーワードに張ったリンクを、キーワードの説明へ向け直せることを確認する。
 *
 * <p>実機では `non-empty-list<User>` の `non-empty-list` が
 * `psi_element://\App\Domain\User[]` を指していた (要素クラス側)。
 */
public final class PhpDocKeywordLinkerTest extends TestCase {
    public void testRedirectsKeywordAnchorToTheKeywordDocumentation() {
        String html = "<code><a href='psi_element://\\App\\Domain\\User[]'>non-empty-list</a></code>";

        String result = PhpDocKeywordLinker.relinkKeywords(html);

        assertTrue("キーワードの説明へ向いていない: " + result,
                result.contains("psi_element://phpstan-type-keyword/non-empty-list"));
        assertFalse("要素クラスへのリンクが残っている: " + result, result.contains("User[]"));
    }

    public void testLeavesClassAnchorsAlone() {
        String html = "<a href='psi_element://\\App\\Domain\\User'>User</a>";

        assertEquals(html, PhpDocKeywordLinker.relinkKeywords(html));
    }
}
