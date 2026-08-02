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
