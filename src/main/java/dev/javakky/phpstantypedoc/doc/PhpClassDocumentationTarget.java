package dev.javakky.phpstantypedoc.doc;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.PhpDocumentationProvider;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PHP クラスを Quick Documentation 内の遷移先として表示する target。
 *
 * <p>PHP documentation の生成は {@link PhpDocumentationProvider} へ委譲する。
 * platform の internal な PSI documentation target には依存しない。
 */
final class PhpClassDocumentationTarget implements DocumentationTarget {
    private final PhpClass phpClass;
    private final PsiElement sourceElement;
    private final PhpDocumentationProvider provider = new PhpDocumentationProvider();

    PhpClassDocumentationTarget(PhpClass phpClass, PsiElement sourceElement) {
        this.phpClass = phpClass;
        this.sourceElement = sourceElement;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.fileRangePointer(
                phpClass.getContainingFile(),
                phpClass.getTextRange(),
                (file, range) -> {
                    PsiElement element = file.findElementAt(range.getStartOffset());
                    PhpClass restored = PsiTreeUtil.getParentOfType(element, PhpClass.class, false);
                    return restored == null ? null : new PhpClassDocumentationTarget(restored, restored);
                }
        );
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(phpClass.getName()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return phpClass instanceof Navigatable navigatable ? navigatable : null;
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return provider.getQuickNavigateInfo(phpClass, sourceElement);
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        String html = provider.generateDoc(phpClass, sourceElement);
        return html == null ? null : DocumentationResult.documentation(html);
    }

    PsiElement contextElement() {
        return phpClass;
    }
}
