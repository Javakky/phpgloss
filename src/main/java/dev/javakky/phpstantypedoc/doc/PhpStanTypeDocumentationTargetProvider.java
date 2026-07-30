package dev.javakky.phpstantypedoc.doc;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.ResolvedPhpStanTypeTarget;
import dev.javakky.phpstantypedoc.keyword.PhpStanKeywordAtCaret;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class PhpStanTypeDocumentationTargetProvider implements DocumentationTargetProvider {
    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        Optional<ResolvedPhpStanTypeTarget> resolved = resolver.resolveAt(file, offset);
        if (resolved.isPresent()) {
            return List.of(new PhpStanTypeDocumentationTarget(file, resolved.get()));
        }
        // キーワードに直接カーソルを当てた場合。PHP は解決後の型 (`\App\User[]` 等) を出すので、
        // キーワード自体の意味は分からないまま
        Optional<? extends DocumentationTarget> keyword = PhpStanKeywordAtCaret.find(file, offset)
                .map(found -> new PhpStanKeywordDocumentationTarget(found, keywordContext(file, offset)));
        if (keyword.isPresent()) {
            return List.of(keyword.get());
        }
        return PhpFunctionDocumentationTarget.at(file, offset, resolver)
                .<List<? extends DocumentationTarget>>map(List::of)
                .orElseGet(List::of);
    }

    private PsiElement keywordContext(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        return element == null ? file : element;
    }
}
