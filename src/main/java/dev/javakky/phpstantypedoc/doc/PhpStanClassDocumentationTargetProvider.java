package dev.javakky.phpstantypedoc.doc;

import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PHP クラスの documentation に、そのクラスが持つ型エイリアスの中身を足す。
 *
 * <p>エイリアス名のリンクは定義元クラスへ飛ぶ。その行き先で中身が読めないと、リンクを辿る
 * 意味がないため、クラスの documentation 側にエイリアスを載せる。
 *
 * <p>エイリアスを持たないクラスでは何も返さず、PHP 標準の表示に任せる。
 */
public final class PhpStanClassDocumentationTargetProvider implements PsiDocumentationTargetProvider {
    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @Nullable DocumentationTarget documentationTarget(
            @NotNull PsiElement element,
            @Nullable PsiElement originalElement
    ) {
        if (!(element instanceof PhpClass phpClass)) {
            return null;
        }
        String details = PhpClassAliasDetails.of(phpClass, resolver);
        if (details.isEmpty()) {
            return null;
        }
        return new PhpClassDocumentationTarget(
                phpClass, originalElement == null ? element : originalElement, details);
    }
}
