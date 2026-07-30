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
    private static final String PARAMETERS_LABEL = "Parameters:";
    private static final String RETURNS_LABEL = "Returns:";

    private final List<ParameterAlias> parameterAliases;
    private final ResolvedAlias returnAlias;
    private final String ownerClassFqn;

    private PhpDocAliasAnnotator(
            List<ParameterAlias> parameterAliases,
            ResolvedAlias returnAlias,
            String ownerClassFqn
    ) {
        this.parameterAliases = parameterAliases;
        this.returnAlias = returnAlias;
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
            String typeName = PhpDocDeclaredTypeReader.typeNameFor(
                    function.getDocComment(), PhpDocDeclaredTypeReader.PARAM_TAGS, parameter.getName());
            resolve(file, ownerClass.getFQN(), typeName, resolver)
                    .ifPresent(alias -> parameters.add(new ParameterAlias(parameter.getName(), alias)));
        }
        String returnTypeName = PhpDocDeclaredTypeReader.typeNameFor(
                function.getDocComment(), PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        ResolvedAlias resolvedReturn =
                resolve(file, ownerClass.getFQN(), returnTypeName, resolver).orElse(null);
        if (parameters.isEmpty() && resolvedReturn == null) {
            return Optional.empty();
        }
        return Optional.of(new PhpDocAliasAnnotator(
                List.copyOf(parameters), resolvedReturn, ownerClass.getFQN()));
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
        if (returnAlias == null) {
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
        return html.substring(0, valueCellEnd)
                + aliasName(returnAlias)
                + html.substring(valueCellEnd);
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
                    + aliasName(parameterAlias.alias())
                    + section.substring(insertion);
        }
        return html.substring(0, parameters) + section + html.substring(sectionEnd);
    }

    /** エイリアス名をエイリアス自身の documentation へのリンクにする。 */
    private String aliasName(ResolvedAlias alias) {
        String name = PhpStanTypeHtmlRenderer.escape(alias.typeName());
        String url = PhpStanTypeDocumentationLink.forAlias(ownerClassFqn, alias.typeName());
        return "&nbsp;<code>(<a href='" + url + "'>" + name + "</a>)</code>";
    }

    private record ParameterAlias(String parameterName, ResolvedAlias alias) {
    }

    private record ResolvedAlias(String typeName, PhpStanResolvedType resolvedType) {
    }
}
