package dev.javakky.phpstantypedoc.doc;

import com.intellij.model.Pointer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.ResolvedPhpStanTypeTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PhpStanTypeDocumentationTarget implements DocumentationTarget {
    private final PsiFile file;
    private final ResolvedPhpStanTypeTarget target;
    private final PhpStanTypeHtmlRenderer renderer = new PhpStanTypeHtmlRenderer();

    PhpStanTypeDocumentationTarget(PsiFile file, ResolvedPhpStanTypeTarget target) {
        this.file = file;
        this.target = target;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        TextRange range = new TextRange(target.reference().range().startOffset(), target.reference().range().endOffset());
        return Pointer.fileRangePointer(file, range, (restoredFile, restoredRange) ->
                new PhpStanTypeResolverFacade().resolveAt(restoredFile, restoredRange.getStartOffset())
                        .map(restored -> new PhpStanTypeDocumentationTarget(restoredFile, restored))
                        .orElse(null));
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(target.resolvedType().requestedName()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        if (target.definitionFile() == null || target.resolvedType().definitionNavigationRange() == null) {
            return null;
        }
        return new OpenFileDescriptor(file.getProject(), target.definitionFile(), target.resolvedType().definitionNavigationRange().startOffset());
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return renderer.renderHint(target.resolvedType());
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        var linkifier = new PhpStanTypeBodyLinkifier(
                file.getProject(), target.resolvedType().definingClassFqcn());
        if (target.variableName() != null) {
            return DocumentationResult.documentation(
                    renderer.renderVariable(target.variableName(), target.resolvedType(), linkifier));
        }
        return DocumentationResult.documentation(renderer.render(target.resolvedType(), linkifier));
    }
}

