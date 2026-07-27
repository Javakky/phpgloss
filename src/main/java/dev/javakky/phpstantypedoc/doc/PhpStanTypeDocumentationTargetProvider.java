package dev.javakky.phpstantypedoc.doc;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiFile;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.ResolvedPhpStanTypeTarget;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public final class PhpStanTypeDocumentationTargetProvider implements DocumentationTargetProvider {
    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        Optional<ResolvedPhpStanTypeTarget> resolved = resolver.resolveAt(file, offset);
        return resolved.<List<? extends DocumentationTarget>>map(target -> List.of(new PhpStanTypeDocumentationTarget(file, target)))
                .orElseGet(List::of);
    }
}

