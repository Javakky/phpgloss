package dev.javakky.phpstantypedoc.doc;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.documentation.PhpDocumentationProvider;
import com.jetbrains.php.lang.psi.elements.PhpNamespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PHP が documentation に出す名前空間リンクの遷移先。
 *
 * <p>この plugin の target を表示している間は platform 標準の PSI handler が働かないため、
 * 名前空間リンクもここで受ける必要がある。
 */
final class PhpNamespaceDocumentationTarget implements DocumentationTarget {
    private final PhpNamespace namespace;
    private final PsiElement sourceElement;
    private final PhpDocumentationProvider provider = new PhpDocumentationProvider();

    PhpNamespaceDocumentationTarget(PhpNamespace namespace, PsiElement sourceElement) {
        this.namespace = namespace;
        this.sourceElement = sourceElement;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.hardPointer(this);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(namespace.getFQN()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return namespace instanceof Navigatable navigatable ? navigatable : null;
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        String html = provider.generateDoc(namespace, sourceElement);
        return html == null ? null : DocumentationResult.documentation(html);
    }

    PsiElement contextElement() {
        return namespace;
    }
}
