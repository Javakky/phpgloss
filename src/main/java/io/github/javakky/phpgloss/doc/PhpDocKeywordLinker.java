package io.github.javakky.phpgloss.doc;

import io.github.javakky.phpgloss.keyword.PhpStanTypeKeywords;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHP が生成した HTML の中で、型キーワードに張られたリンクの行き先を差し替える。
 *
 * <p>PHP は `non-empty-list<User>` を `User[]` に解決し、キーワードの文字列に要素クラスへの
 * リンクを張る。読み手が押したいのはキーワードの意味であって要素クラスではないので、
 * キーワードの説明へ向け直す。
 */
final class PhpDocKeywordLinker {
    private static final Pattern ANCHOR = Pattern.compile("<a href=['\"]psi_element://[^'\"]*['\"]>([^<]+)</a>");

    private PhpDocKeywordLinker() {
    }

    private static final Pattern ANY_ANCHOR = Pattern.compile("(?s)<a\\s[^>]*>.*?</a>");
    private static final Pattern SPANNED_WORD =
            Pattern.compile("(<span[^>]*>)([A-Za-z][A-Za-z0-9_-]*)(</span>)");

    /**
     * リンクになっていないキーワードをリンクにする。
     *
     * <p>{@link #relinkKeywords} は既にあるリンクの行き先を差し替えるだけで、PHP が色を付けた
     * だけの語には効かない。エディタ内のレンダリング表示では `list&lt;positive-int&gt;` がまるごと
     * 色付きの文字列として来るので、こちらでリンクを足す。
     *
     * <p>既にリンクの中にある語は触らない。エイリアス名のリンクを、同じ綴りのキーワードで
     * 上書きしてしまう。
     */
    static String linkBareKeywords(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher anchors = ANY_ANCHOR.matcher(html);
        StringBuilder builder = new StringBuilder();
        int handled = 0;
        while (anchors.find()) {
            builder.append(linkOutsideAnchors(html.substring(handled, anchors.start())));
            builder.append(anchors.group());
            handled = anchors.end();
        }
        return builder.append(linkOutsideAnchors(html.substring(handled))).toString();
    }

    private static String linkOutsideAnchors(String html) {
        Matcher matcher = SPANNED_WORD.matcher(html);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String word = matcher.group(2);
            String replacement = PhpStanTypeKeywords.find(word)
                    .filter(keyword -> !isShapeKey(html, matcher.end()))
                    .map(keyword -> matcher.group(1)
                            + "<a href='" + PhpStanTypeDocumentationLink.forKeyword(keyword.name()) + "'>"
                            + word + "</a>" + matcher.group(3))
                    .orElse(matcher.group());
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(builder).toString();
    }

    /**
     * `array{list: int}` の `list` はキー名であってキーワードではない。次に来る可視文字が
     * `:` なら鍵とみなす。記号もタグで包まれて来るので、タグを飛ばして中身を見る。
     */
    private static boolean isShapeKey(String html, int from) {
        for (int at = from; at < html.length(); at++) {
            char here = html.charAt(at);
            if (here == '<') {
                int close = html.indexOf('>', at);
                if (close < 0) {
                    return false;
                }
                at = close;
            } else if (here == '&') {
                int close = html.indexOf(';', at);
                return close >= 0 && "&nbsp;".contentEquals(html.subSequence(at, close + 1))
                        && isShapeKey(html, close + 1);
            } else if (!Character.isWhitespace(here)) {
                return here == ':';
            }
        }
        return false;
    }

    static String relinkKeywords(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher matcher = ANCHOR.matcher(html);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String text = matcher.group(1);
            String replacement = PhpStanTypeKeywords.find(text)
                    .map(keyword -> "<a href='" + PhpStanTypeDocumentationLink.forKeyword(keyword.name())
                            + "'>" + text + "</a>")
                    .orElse(matcher.group());
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(builder).toString();
    }
}
