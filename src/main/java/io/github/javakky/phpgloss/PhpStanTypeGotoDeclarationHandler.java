package io.github.javakky.phpgloss;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.javakky.phpgloss.keyword.PhpStanKeywordAtCaret;
import io.github.javakky.phpgloss.keyword.PhpStanKeywordDefinition;
import io.github.javakky.phpgloss.usages.PhpStanAliasDeclarations;
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
        PsiElement[] keywordTarget = keywordDefinition(sourceFile, offset);
        if (keywordTarget != null) {
            return keywordTarget;
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

    /**
     * 型キーワード ({@code non-empty-list} 等) の上なら、解析ツール側の定義へ移動させる。
     * PHP から見ればただの文字列なので、ここで拾わないと「移動先となる宣言が見つかりません」になる。
     */
    private PsiElement @Nullable [] keywordDefinition(PsiFile sourceFile, int offset) {
        return PhpStanKeywordAtCaret.find(sourceFile, offset)
                .flatMap(keyword -> PhpStanKeywordDefinition.find(sourceFile.getProject(), keyword.name()))
                .map(location -> {
                    PsiFile definitionPsi =
                            PsiManager.getInstance(sourceFile.getProject()).findFile(location.file());
                    if (definitionPsi == null) {
                        return null;
                    }
                    // 該当行へ着地させる。ファイルを返すと先頭に飛んでしまう
                    PsiElement element = definitionPsi.findElementAt(location.offset());
                    return new PsiElement[]{element == null ? definitionPsi : element};
                })
                .orElse(null);
    }
}
