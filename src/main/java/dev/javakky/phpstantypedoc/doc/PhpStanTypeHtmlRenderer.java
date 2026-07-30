package dev.javakky.phpstantypedoc.doc;

import com.intellij.lang.documentation.DocumentationMarkup;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;

public final class PhpStanTypeHtmlRenderer {
    /**
     * 変数にキャレットがある場合の表示。PHP の変数ドキュメント (`$user_id: int`) に合わせて
     * `$payload: CreateUserData` の形にし、続けて型本体を出す。
     */
    public String renderVariable(
            String variableName,
            PhpStanResolvedType resolvedType,
            PhpStanTypeBodyLinkifier linkifier
    ) {
        return render(variableName, resolvedType, linkifier);
    }

    /**
     * エイリアスを持たない変数の表示。PHP 標準の変数ホバーと同じ最小構成
     * (`$result: list<int>` と定義元ファイル) にし、型だけを整理したものに差し替える。
     */
    public String renderVariableType(String variableName, String type, String sourceFileName) {
        return DocumentationMarkup.DEFINITION_START
                + "$" + escape(variableName) + ": " + escape(type)
                + DocumentationMarkup.DEFINITION_END
                + DocumentationMarkup.SECTIONS_START
                + DocumentationMarkup.SECTION_HEADER_START + "Source:"
                + DocumentationMarkup.SECTION_SEPARATOR + escape(sourceFileName)
                + DocumentationMarkup.SECTION_END
                + DocumentationMarkup.SECTIONS_END;
    }

    public String render(PhpStanResolvedType resolvedType, PhpStanTypeBodyLinkifier linkifier) {
        return render(null, resolvedType, linkifier);
    }

    /**
     * @param variableName 変数名 (`$` を除く)。null なら型名だけを見出しにする
     * @param linkifier    型本体のクラス名をリンクにする。null なら素の表示のまま
     */
    private String render(
            String variableName,
            PhpStanResolvedType resolvedType,
            PhpStanTypeBodyLinkifier linkifier
    ) {
        String heading = variableName == null
                ? aliasHeading(resolvedType)
                : "$" + escape(variableName) + ": " + aliasHeading(resolvedType);
        if (resolvedType.circular()) {
            return DocumentationMarkup.DEFINITION_START
                    + heading
                    + DocumentationMarkup.DEFINITION_END
                    + DocumentationMarkup.CONTENT_START
                    + "Circular PHPStan type alias reference detected."
                    + DocumentationMarkup.CONTENT_END;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(DocumentationMarkup.DEFINITION_START)
                .append(heading)
                .append(DocumentationMarkup.DEFINITION_END);
        builder.append(DocumentationMarkup.CONTENT_START);
        if (resolvedType.imported()) {
            builder.append("<p>Imported type: ")
                    .append(escape(resolvedType.originalName()))
                    .append("</p>");
        }
        String typeBody = escape(resolvedType.typeBody());
        builder.append("<pre><code>")
                .append(linkifier == null ? typeBody : linkifier.linkify(typeBody))
                .append("</code></pre>");
        builder.append("<p>Defined in ")
                .append(classLink(resolvedType.definingClassFqcn()))
                .append("</p>");
        builder.append(DocumentationMarkup.CONTENT_END);
        return builder.toString();
    }

    /** エイリアス名。定義元クラスが分かる場合はそのクラスへのリンクにする。 */
    private String aliasHeading(PhpStanResolvedType resolvedType) {
        String name = escape(resolvedType.requestedName());
        String fqcn = resolvedType.definingClassFqcn();
        if (fqcn == null || fqcn.isBlank()) {
            return name;
        }
        return "<a href=\"psi_element://" + normalizeFqcn(fqcn) + "\">" + name + "</a>";
    }

    private String classLink(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) {
            return "";
        }
        return "<a href=\"psi_element://" + normalizeFqcn(fqcn) + "\">" + escape(fqcn) + "</a>";
    }

    private String normalizeFqcn(String fqcn) {
        return fqcn.startsWith("\\") ? fqcn : "\\" + fqcn;
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
