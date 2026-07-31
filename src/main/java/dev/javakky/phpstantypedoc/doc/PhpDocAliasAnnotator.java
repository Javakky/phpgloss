package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PHP が生成した documentation HTML の `Parameters:` / `Returns:` に、解決した型エイリアス名を
 * 追記する。Quick Documentation とエディタ内のレンダリング表示 (鉛筆 / 罫線の切り替え) は
 * 別の拡張点から来るが、HTML はどちらも `&lt;table class='sections'&gt;` の同じ構造なので
 * 加工処理を共有する。
 */
public final class PhpDocAliasAnnotator {
    /** 本体をそのまま並べると表示が崩れる。読み取れる長さで切る。 */
    private static final int BODY_LIMIT = 120;

    private static final String PARAMETERS_LABEL = "Parameters:";
    private static final String RETURNS_LABEL = "Returns:";

    private final List<ParameterAlias> parameterAliases;
    private final List<ResolvedAlias> returnAliases;
    private final String ownerClassFqn;

    private PhpDocAliasAnnotator(
            List<ParameterAlias> parameterAliases,
            List<ResolvedAlias> returnAliases,
            String ownerClassFqn
    ) {
        this.parameterAliases = parameterAliases;
        this.returnAliases = returnAliases;
        this.ownerClassFqn = ownerClassFqn;
    }

    /**
     * @return 追記できるエイリアスが 1 つも無ければ empty。PHP の表示に手を入れる理由がない
     */
    public static Optional<PhpDocAliasAnnotator> of(
            PsiFile file,
            Function function,
            PhpStanTypeResolverFacade resolver
    ) {
        PhpClass ownerClass = PsiTreeUtil.getParentOfType(function, PhpClass.class, false);
        if (ownerClass == null || ownerClass.getFQN() == null || function.getDocComment() == null) {
            return Optional.empty();
        }
        List<ParameterAlias> parameters = new ArrayList<>();
        for (Parameter parameter : function.getParameters()) {
            String typeName = PhpDocDeclaredTypeReader.unionTypeFor(
                    function.getDocComment(), PhpDocDeclaredTypeReader.PARAM_TAGS, parameter.getName());
            List<ResolvedAlias> aliases = resolveAll(file, ownerClass.getFQN(), typeName, resolver);
            if (!aliases.isEmpty()) {
                parameters.add(new ParameterAlias(parameter.getName(), aliases));
            }
        }
        String returnTypeName = PhpDocDeclaredTypeReader.unionTypeFor(
                function.getDocComment(), PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        List<ResolvedAlias> resolvedReturn =
                resolveAll(file, ownerClass.getFQN(), returnTypeName, resolver);
        if (parameters.isEmpty() && resolvedReturn.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PhpDocAliasAnnotator(
                List.copyOf(parameters), resolvedReturn, ownerClass.getFQN()));
    }

    /**
     * 型テキストに現れるエイリアス名を、書かれた順にすべて拾う。
     *
     * <p>`list<row_shape>` のように型引数の中で使われることが多く、型全体を名前として
     * 引いても見つからない (実機で `Returns: list<array>` のまま出ていた)。
     */
    private static List<ResolvedAlias> resolveAll(
            PsiFile file,
            String ownerClassFqn,
            String typeName,
            PhpStanTypeResolverFacade resolver
    ) {
        if (typeName == null) {
            return List.of();
        }
        List<ResolvedAlias> aliases = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String identifier : identifiersOf(typeName)) {
            if (!seen.add(identifier)) {
                continue;
            }
            resolve(file, ownerClassFqn, identifier, resolver).ifPresent(aliases::add);
        }
        return List.copyOf(aliases);
    }

    /** 型テキストから識別子だけを取り出す。array shape のキー (直後が `:`) は名前ではない。 */
    private static List<String> identifiersOf(String type) {
        List<String> identifiers = new ArrayList<>();
        int cursor = 0;
        while (cursor < type.length()) {
            if (!Character.isLetter(type.charAt(cursor)) && type.charAt(cursor) != '_') {
                cursor++;
                continue;
            }
            int end = cursor;
            while (end < type.length()
                    && (Character.isLetterOrDigit(type.charAt(end)) || type.charAt(end) == '_')) {
                end++;
            }
            if (end >= type.length() || type.charAt(end) != ':') {
                identifiers.add(type.substring(cursor, end));
            }
            cursor = end;
        }
        return identifiers;
    }

    private static Optional<ResolvedAlias> resolve(
            PsiFile file,
            String ownerClassFqn,
            String typeName,
            PhpStanTypeResolverFacade resolver
    ) {
        if (typeName == null) {
            return Optional.empty();
        }
        return resolver.resolveInClass(file, ownerClassFqn, typeName)
                .map(resolved -> new ResolvedAlias(typeName, resolved));
    }

    public String annotate(String html) {
        return addReturnAlias(addParameterAliases(html));
    }

    private String addReturnAlias(String html) {
        if (returnAliases.isEmpty()) {
            return html;
        }
        int returns = html.indexOf(RETURNS_LABEL);
        if (returns < 0) {
            return html;
        }
        int labelCellEnd = html.indexOf("</td>", returns);
        int valueCellEnd = labelCellEnd < 0 ? -1 : html.indexOf("</td>", labelCellEnd + 5);
        if (valueCellEnd < 0) {
            return html;
        }
        StringBuilder appended = new StringBuilder();
        returnAliases.forEach(alias -> appended.append(aliasName(alias)));
        return html.substring(0, valueCellEnd) + appended + html.substring(valueCellEnd);
    }


    private String addParameterAliases(String html) {
        int parameters = html.indexOf(PARAMETERS_LABEL);
        if (parameters < 0) {
            return html;
        }
        int sectionEnd = html.indexOf(RETURNS_LABEL, parameters);
        if (sectionEnd < 0) {
            sectionEnd = html.indexOf("Source:", parameters);
        }
        if (sectionEnd < 0) {
            sectionEnd = html.length();
        }
        String section = html.substring(parameters, sectionEnd);
        for (ParameterAlias parameterAlias : parameterAliases) {
            String marker = "$" + parameterAlias.parameterName() + "</span></code>";
            int variableEnd = section.indexOf(marker);
            if (variableEnd < 0) {
                continue;
            }
            int insertion = variableEnd + marker.length();
            section = section.substring(0, insertion)
                    + parameterAlias.aliases().stream().map(this::aliasName).reduce("", String::concat)
                    + section.substring(insertion);
        }
        return html.substring(0, parameters) + section + html.substring(sectionEnd);
    }

    /** エイリアス名を、そのエイリアス自身の documentation へのリンクにする。 */
    private String aliasName(ResolvedAlias alias) {
        String name = PhpStanTypeHtmlRenderer.escape(alias.typeName());
        String labelled = ownerClassFqn == null || ownerClassFqn.isBlank()
                ? name
                : "<a href='" + PhpStanTypeDocumentationLink.forAlias(ownerClassFqn, alias.typeName())
                        + "'>" + name + "</a>";
        return "&nbsp;<code>(" + labelled + bodyOf(alias) + ")</code>";
    }

    /**
     * エイリアス名だけでは、それがどんな形かは分からない。名前に続けて本体を書き添える。
     *
     * <p>PhpStorm は `list<row_shape>` を `list<array>` としか出せないため、名前を添えるだけでは
     * shape が読めないままになる (実機で指摘)。長い本体は途中で切る。
     */
    private String bodyOf(ResolvedAlias alias) {
        String body = alias.resolvedType().typeBody();
        if (body == null || body.isBlank()) {
            return "";
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        String shortened = collapsed.length() > BODY_LIMIT
                ? collapsed.substring(0, BODY_LIMIT) + " …"
                : collapsed;
        return ": " + PhpStanTypeHtmlRenderer.escape(shortened);
    }

    private record ParameterAlias(String parameterName, List<ResolvedAlias> aliases) {
    }

    private record ResolvedAlias(String typeName, PhpStanResolvedType resolvedType) {
    }
}
