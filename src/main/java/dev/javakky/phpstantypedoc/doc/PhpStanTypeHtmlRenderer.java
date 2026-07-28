package dev.javakky.phpstantypedoc.doc;

import com.intellij.lang.documentation.DocumentationMarkup;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;

public final class PhpStanTypeHtmlRenderer {
    /** 変数表示からエイリアスの中身へ展開するリンク。{@code PhpStanTypeLinkHandler} が処理する。 */
    public static final String EXPAND_LINK = "phpstan-type://expand";

    /**
     * 変数にキャレットがある場合の表示。PHP の変数ドキュメント (`$user_id: int`) に合わせて
     * `$payload: CreateUserData` の形にし、型名はクリックで中身へ展開できるリンクにする。
     */
    public String renderVariable(String variableName, PhpStanResolvedType resolvedType) {
        if (resolvedType.circular()) {
            return render(resolvedType);
        }
        return DocumentationMarkup.DEFINITION_START
                + "$" + escape(variableName) + ": "
                + "<a href=\"" + EXPAND_LINK + "\">" + escape(resolvedType.requestedName()) + "</a>"
                + DocumentationMarkup.DEFINITION_END;
    }

    public String render(PhpStanResolvedType resolvedType) {
        if (resolvedType.circular()) {
            return DocumentationMarkup.DEFINITION_START
                    + escape(resolvedType.requestedName())
                    + DocumentationMarkup.DEFINITION_END
                    + DocumentationMarkup.CONTENT_START
                    + "Circular PHPStan type alias reference detected."
                    + DocumentationMarkup.CONTENT_END;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(DocumentationMarkup.DEFINITION_START)
                .append(escape(resolvedType.requestedName()))
                .append(DocumentationMarkup.DEFINITION_END);
        builder.append(DocumentationMarkup.CONTENT_START);
        if (resolvedType.imported()) {
            builder.append("<p>Imported type: ")
                    .append(escape(resolvedType.originalName()))
                    .append("</p>");
            builder.append("<p>Defined in ")
                    .append(escape(resolvedType.definingClassFqcn()))
                    .append("</p>");
        }
        builder.append("<pre><code>")
                .append(escape(resolvedType.typeBody()))
                .append("</code></pre>");
        if (!resolvedType.imported()) {
            builder.append("<p>Defined in ")
                    .append(escape(resolvedType.definingClassFqcn()))
                    .append("</p>");
        }
        builder.append(DocumentationMarkup.CONTENT_END);
        return builder.toString();
    }

    public String renderHint(PhpStanResolvedType resolvedType) {
        if (resolvedType.circular()) {
            return "Circular PHPStan type alias reference detected.";
        }
        return escape(resolvedType.requestedName()) + " = " + escape(resolvedType.typeBody());
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '&' -> builder.append("&amp;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }
}

