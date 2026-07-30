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
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PHP 標準の function documentation に PHPStan type alias link を補う target。
 */
final class PhpFunctionDocumentationTarget implements DocumentationTarget {
    private final Function function;
    private final PhpDocAliasAnnotator annotator;
    private final PhpDocumentationProvider provider = new PhpDocumentationProvider();

    private PhpFunctionDocumentationTarget(Function function, PhpDocAliasAnnotator annotator) {
        this.function = function;
        this.annotator = annotator;
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
        return PhpDocAliasAnnotator.of(file, function, resolver)
                .map(annotator -> new PhpFunctionDocumentationTarget(function, annotator));
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
        return html == null ? null : DocumentationResult.documentation(annotator.annotate(html));
    }

    PsiElement contextElement() {
        return function;
    }





}
