package dev.javakky.phpstantypedoc.doc;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import dev.javakky.phpstantypedoc.type.TypeUnionSimplifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * エイリアスに当たらない変数のホバー。global 型の union から、他の型に呑まれているものを落として出す。
 *
 * <p>PHP 標準の表示は解けた型と抽象型を並べる (実測: `$result: array|int[]|int[]|list<int>`)。
 * union の成分はプロバイダ側から消せないため、表示のときに整理する。整理して減らないときは target を
 * 出さず、PHP 標準の表示に任せる。
 */
final class SimplifiedVariableTypeTarget implements DocumentationTarget {
    private final PhpNamedElement variable;
    private final List<String> types;
    private final PhpStanTypeHtmlRenderer renderer = new PhpStanTypeHtmlRenderer();

    private SimplifiedVariableTypeTarget(PhpNamedElement variable, List<String> types) {
        this.variable = variable;
        this.types = types;
    }

    static Optional<SimplifiedVariableTypeTarget> at(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        PhpNamedElement variable = PsiTreeUtil.getParentOfType(element, PhpNamedElement.class, false);
        if (!(variable instanceof Variable) && !(variable instanceof Parameter)) {
            return Optional.empty();
        }
        return TypeUnionSimplifier.simplify(variable.getType().global(file.getProject()).getTypesWithParametrisedParts())
                .map(simplified -> new SimplifiedVariableTypeTarget(variable, simplified));
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.fileRangePointer(
                variable.getContainingFile(),
                variable.getTextRange(),
                (file, range) -> at(file, range.getStartOffset()).orElse(null));
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(name()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return variable;
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return name() + ": " + typeText();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        // PHP 標準の HTML を土台にし、型の部分だけを整理後の型に差し替える。丸ごと自前で
        // 組み直すと、名前空間のリンクや Source 行の体裁が失われる (実機で指摘を受けた)
        String base = standardHtml();
        String patched = base == null ? null : replaceTypeText(base);
        if (patched != null) {
            return DocumentationResult.documentation(patched);
        }
        return DocumentationResult.documentation(renderer.renderVariableType(
                variable.getName(), typeText(), variable.getContainingFile().getName()));
    }

    private @Nullable String standardHtml() {
        DocumentationProvider provider =
                LanguageDocumentation.INSTANCE.forLanguage(variable.getLanguage());
        return provider == null ? null : provider.generateDoc(variable, variable);
    }

    /**
     * `$name: <型>` の型の部分を差し替える。標準の HTML は型の各要素をリンク付きで並べるため、
     * 名前の直後から定義ブロックの閉じまでを、整理後の型 (クラス名はリンク化) に置き換える。
     */
    private @Nullable String replaceTypeText(String html) {
        String marker = name() + ":";
        int at = html.indexOf(marker);
        if (at < 0) {
            return null;
        }
        int end = html.indexOf("</pre>", at);
        if (end < 0) {
            end = html.indexOf("</div>", at);
        }
        if (end < 0) {
            return null;
        }
        var linkifier = new PhpStanTypeBodyLinkifier(variable.getProject(), null);
        String linked = linkifier.linkify(escape(typeText()));
        return html.substring(0, at) + marker + " " + linked + html.substring(end);
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String name() {
        return "$" + variable.getName();
    }

    private String typeText() {
        return String.join("|", types);
    }
}
