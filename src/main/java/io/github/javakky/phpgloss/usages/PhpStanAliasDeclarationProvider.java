package io.github.javakky.phpgloss.usages;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolDeclarationProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * PHPDoc のエイリアス宣言名を platform に「宣言」として見せる。
 *
 * <p>これがあると Ctrl+B (Go to Declaration or Usages) が、宣言位置では移動でなく
 * 使用箇所検索に切り替わる。PHPDoc 内の名前には宣言として認識される PSI が無いため、
 * テキスト範囲を自前で宣言として申告する。
 */
public final class PhpStanAliasDeclarationProvider implements PsiSymbolDeclarationProvider {
    @Override
    public @NotNull Collection<? extends PsiSymbolDeclaration> getDeclarations(
            @NotNull PsiElement element,
            int offsetInElement
    ) {
        TextRange elementRange = element.getTextRange();
        if (elementRange == null) {
            return List.of();
        }
        int offset = elementRange.getStartOffset() + offsetInElement;
        return PhpStanAliasDeclarations.at(element.getContainingFile(), offset)
                .filter(declaration -> elementRange.containsRange(
                        declaration.nameRange().startOffset(), declaration.nameRange().endOffset()))
                .<PsiSymbolDeclaration>map(declaration -> new AliasDeclaration(
                        element,
                        new TextRange(
                                declaration.nameRange().startOffset() - elementRange.getStartOffset(),
                                declaration.nameRange().endOffset() - elementRange.getStartOffset()),
                        declaration.symbol()))
                .map(List::of)
                .orElseGet(List::of);
    }

    private record AliasDeclaration(PsiElement element, TextRange rangeInElement, Symbol symbol)
            implements PsiSymbolDeclaration {

        @Override
        public @NotNull PsiElement getDeclaringElement() {
            return element;
        }

        @Override
        public @NotNull TextRange getRangeInDeclaringElement() {
            return rangeInElement;
        }

        @Override
        public @NotNull Symbol getSymbol() {
            return symbol;
        }
    }
}
