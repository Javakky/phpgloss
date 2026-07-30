package dev.javakky.phpstantypedoc.doc;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import dev.javakky.phpstantypedoc.keyword.PhpStanKeywordDefinition;
import dev.javakky.phpstantypedoc.keyword.PhpStanTypeKeyword;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 型キーワード ({@code non-empty-string} 等) の説明。
 *
 * <p>キーワードは PHP の言語機能ではなく静的解析ツール側の語彙なので、対応する PSI が無い。
 * 説明は同梱のカタログから出し、移動先は解析ツールのソース (あれば) を指す。
 */
final class PhpStanKeywordDocumentationTarget implements DocumentationTarget {
    private final PhpStanTypeKeyword keyword;
    private final PsiElement context;

    PhpStanKeywordDocumentationTarget(PhpStanTypeKeyword keyword, PsiElement context) {
        this.keyword = keyword;
        this.context = context;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(keyword.name()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return definition()
                .map(location -> (Navigatable) new OpenFileDescriptor(
                        project(), location.file(), location.offset()))
                .orElse(null);
    }

    PsiElement contextElement() {
        return context;
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return keyword.description();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        StringBuilder html = new StringBuilder();
        html.append(DocumentationMarkup.DEFINITION_START)
                .append("<code>").append(keyword.name()).append("</code>")
                .append(DocumentationMarkup.DEFINITION_END);
        html.append(DocumentationMarkup.CONTENT_START)
                .append(keyword.description().replace("\n", "<br>"))
                .append(DocumentationMarkup.CONTENT_END);
        html.append(DocumentationMarkup.SECTIONS_START)
                .append(section("対応", String.join(", ", keyword.tools())))
                .append(section("定義", definitionText()))
                .append(DocumentationMarkup.SECTIONS_END);
        return DocumentationResult.documentation(html.toString());
    }

    /**
     * プロジェクトの vendor を見ているのか同梱のコピーなのかは、読む側にとって意味が違う。
     * 前者は実際に使っているバージョンの定義で、後者はこのプラグインが持っている写し。
     */
    private String definitionText() {
        return definition()
                .map(location -> location.file().getName() + (location.bundled() ? " (同梱)" : ""))
                .orElse("見つからない");
    }

    private Optional<PhpStanKeywordDefinition.Location> definition() {
        return PhpStanKeywordDefinition.find(project(), keyword.name());
    }

    private Project project() {
        return context.getProject();
    }

    private String section(String name, String value) {
        return DocumentationMarkup.SECTION_HEADER_START + name + ":"
                + DocumentationMarkup.SECTION_SEPARATOR + value + DocumentationMarkup.SECTION_END;
    }
}
