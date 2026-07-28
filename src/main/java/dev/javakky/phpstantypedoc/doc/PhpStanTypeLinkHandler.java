package dev.javakky.phpstantypedoc.doc;

import com.intellij.platform.backend.documentation.DocumentationLinkHandler;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LinkResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 変数のドキュメントに出したエイリアス名のリンクを処理する。
 *
 * <p>`$payload: CreateUserData` の型名をクリックすると、同じ解決結果のまま
 * 型本体を出す target へ差し替える。エイリアスは PSI 要素ではないので
 * `psi_element://` スキームには乗らず、自前のリンクを持つ。
 */
public final class PhpStanTypeLinkHandler implements DocumentationLinkHandler {
    @Override
    public @Nullable LinkResolveResult resolveLink(@NotNull DocumentationTarget target, @NotNull String url) {
        if (!PhpStanTypeHtmlRenderer.EXPAND_LINK.equals(url)
                || !(target instanceof PhpStanTypeDocumentationTarget phpStanTarget)) {
            return null;
        }
        return LinkResolveResult.resolvedTarget(phpStanTarget.expanded());
    }
}
