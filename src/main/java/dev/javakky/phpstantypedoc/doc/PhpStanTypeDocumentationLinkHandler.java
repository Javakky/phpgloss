package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.PsiElement;
import com.intellij.platform.backend.documentation.DocumentationLinkHandler;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LinkResolveResult;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * {@link PhpStanTypeDocumentationTarget} 内の {@code psi_element://} リンクを解決する。
 *
 * <p>platform 標準の PSI link handler は、表示中の target が
 * {@code PsiElementDocumentationTarget} の場合だけ処理する。この plugin は独自 target を返すため、
 * 同じ URL 形式でも標準 handler からは無視される。この plugin が生成するリンクは PHP クラスの
 * FQN だけなので {@link PhpIndex} で解決し、PHP の documentation provider を使う独自 target へ
 * 変換する。遷移後の PHP クラス documentation にある同形式のリンクもこの handler が処理する。
 */
public final class PhpStanTypeDocumentationLinkHandler implements DocumentationLinkHandler {
    private static final String PSI_ELEMENT_PROTOCOL = "psi_element://";

    @Override
    public @Nullable LinkResolveResult resolveLink(
            @NotNull DocumentationTarget target,
            @NotNull String url
    ) {
        PsiElement context = contextElement(target);
        if (context == null || !url.startsWith(PSI_ELEMENT_PROTOCOL)) {
            return null;
        }
        String fqn = url.substring(PSI_ELEMENT_PROTOCOL.length());
        Collection<PhpClass> classes =
                PhpIndex.getInstance(context.getProject()).getClassesByFQN(fqn);
        if (classes.size() != 1) {
            return null;
        }
        DocumentationTarget resolved = new PhpClassDocumentationTarget(classes.iterator().next(), context);
        return LinkResolveResult.resolvedTarget(resolved);
    }

    private PsiElement contextElement(DocumentationTarget target) {
        if (target instanceof PhpStanTypeDocumentationTarget phpStanTarget) {
            return phpStanTarget.contextElement();
        }
        if (target instanceof PhpClassDocumentationTarget classTarget) {
            return classTarget.contextElement();
        }
        return null;
    }
}
