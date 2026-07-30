package dev.javakky.phpstantypedoc;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import dev.javakky.phpstantypedoc.usages.PhpStanAliasDeclarations;
import org.jetbrains.annotations.Nullable;

public final class PhpStanTypeGotoDeclarationHandler implements GotoDeclarationHandler {
    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @Nullable PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        PsiFile sourceFile = sourceElement.getContainingFile();
        // 宣言名の上では移動先を出さない。自分自身へ移動しても意味がなく、ここで何も返さないことで
        // platform が使用箇所検索へ切り替える (Go to Declaration or Usages の標準挙動)
        if (PhpStanAliasDeclarations.at(sourceFile, offset).isPresent()) {
            return null;
        }
        return resolver.resolveAt(sourceFile, offset)
                .map(target -> {
                    if (target.resolvedType().definitionNavigationRange() == null || target.definitionFile() == null) {
                        return null;
                    }
                    PsiFile definitionPsi = PsiManager.getInstance(sourceFile.getProject()).findFile(target.definitionFile());
                    if (definitionPsi == null || !definitionPsi.isValid()) {
                        return null;
                    }
                    PsiElement element = definitionPsi.findElementAt(target.resolvedType().definitionNavigationRange().startOffset());
                    return element == null ? null : new PsiElement[]{element};
                })
                .orElse(null);
    }
}
