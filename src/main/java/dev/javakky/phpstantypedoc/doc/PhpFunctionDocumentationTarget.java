package dev.javakky.phpstantypedoc.doc;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.PhpDocumentationProvider;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PHP 標準の function documentation に PHPStan type alias link を補う target。
 */
final class PhpFunctionDocumentationTarget implements DocumentationTarget {
    private static final String PARAMETERS_LABEL = "Parameters:";
    private static final String RETURNS_LABEL = "Returns:";

    private final Function function;
    private final String ownerClassFqn;
    private final List<ParameterAlias> parameterAliases;
    private final ResolvedAlias returnAlias;
    private final PhpDocumentationProvider provider = new PhpDocumentationProvider();

    private PhpFunctionDocumentationTarget(
            Function function,
            String ownerClassFqn,
            List<ParameterAlias> parameterAliases,
            ResolvedAlias returnAlias
    ) {
        this.function = function;
        this.ownerClassFqn = ownerClassFqn;
        this.parameterAliases = parameterAliases;
        this.returnAlias = returnAlias;
    }

    static Optional<PhpFunctionDocumentationTarget> at(
            PsiFile file,
            int offset,
            PhpStanTypeResolverFacade resolver
    ) {
        PsiElement element = file.findElementAt(offset);
        Function function = PsiTreeUtil.getParentOfType(element, Function.class, false);
        if (function == null || function.isClosure() || function.getNameIdentifier() == null
                || !function.getNameIdentifier().getTextRange().containsOffset(offset)) {
            return Optional.empty();
        }
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
        ResolvedAlias resolvedReturn = resolve(file, ownerClass.getFQN(), returnTypeName, resolver).orElse(null);
        if (parameters.isEmpty() && resolvedReturn == null) {
            return Optional.empty();
        }
        return Optional.of(new PhpFunctionDocumentationTarget(
                function, ownerClass.getFQN(), List.copyOf(parameters), resolvedReturn));
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

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.fileRangePointer(
                function.getContainingFile(),
                function.getTextRange(),
                (file, range) -> {
                    PsiElement element = file.findElementAt(range.getStartOffset());
                    Function restored = PsiTreeUtil.getParentOfType(element, Function.class, false);
                    if (restored == null || restored.getNameIdentifier() == null) {
                        return null;
                    }
                    return at(file, restored.getNameIdentifier().getTextOffset(), new PhpStanTypeResolverFacade())
                            .orElse(null);
                }
        );
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(function.getName()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return function;
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return provider.getQuickNavigateInfo(function, function);
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        String html = provider.generateDoc(function, function);
        return html == null ? null : DocumentationResult.documentation(addAliasLinks(html));
    }

    PsiElement contextElement() {
        return function;
    }

    private String addAliasLinks(String html) {
        String result = addParameterAliases(html);
        if (returnAlias == null) {
            return result;
        }
        int returns = result.indexOf(RETURNS_LABEL);
        if (returns < 0) {
            return result;
        }
        int labelCellEnd = result.indexOf("</td>", returns);
        int valueCellEnd = labelCellEnd < 0 ? -1 : result.indexOf("</td>", labelCellEnd + 5);
        if (valueCellEnd < 0) {
            return result;
        }
        return result.substring(0, valueCellEnd)
                + aliasLink(returnAlias)
                + result.substring(valueCellEnd);
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
                    + aliasLink(parameterAlias.alias())
                    + section.substring(insertion);
        }
        return html.substring(0, parameters) + section + html.substring(sectionEnd);
    }

    /** alias 名は定義元クラスへのリンクにする。飛び先の PHPDoc に `@phpstan-type` の定義がある。 */
    private String aliasLink(ResolvedAlias alias) {
        String name = PhpStanTypeHtmlRenderer.escape(alias.typeName());
        String definingClassFqcn = alias.resolvedType().definingClassFqcn();
        if (definingClassFqcn == null || definingClassFqcn.isBlank()) {
            return "&nbsp;<code>(" + name + ")</code>";
        }
        String url = PhpStanTypeDocumentationLink.forClass(definingClassFqcn);
        return "&nbsp;<code>(<a href='" + url + "'>" + name + "</a>)</code>";
    }

    private record ParameterAlias(String parameterName, ResolvedAlias alias) {
    }

    private record ResolvedAlias(String typeName, PhpStanResolvedType resolvedType) {
    }
}
