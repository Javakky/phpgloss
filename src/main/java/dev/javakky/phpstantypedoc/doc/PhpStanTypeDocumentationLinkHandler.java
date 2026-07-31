package dev.javakky.phpstantypedoc.doc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.platform.backend.documentation.DocumentationLinkHandler;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LinkResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.ResolvedPhpStanTypeTarget;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.model.SourceRange;
import dev.javakky.phpstantypedoc.parser.DetectedTypeReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * この plugin の documentation target 内のリンクを解決する。
 *
 * <p>platform 標準の PSI link handler は、表示中の target が
 * {@code PsiElementDocumentationTarget} の場合だけ処理する。この plugin は独自 target を返すため、
 * 同じ {@code psi_element://} 形式でも標準 handler からは無視され、ここで処理する。
 *
 * <p>リンクは 2 種類ある。クラス FQN だけのものはそのクラスの documentation へ、
 * alias 名を fragment に持つものはエイリアス自身の documentation へ遷移させる。
 * どこで解決に失敗したかを実機で追えるよう、失敗経路にはログを残す。
 */
public final class PhpStanTypeDocumentationLinkHandler implements DocumentationLinkHandler {
    private static final Logger LOG = Logger.getInstance(PhpStanTypeDocumentationLinkHandler.class);

    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @Nullable LinkResolveResult resolveLink(
            @NotNull DocumentationTarget target,
            @NotNull String url
    ) {
        // 実機でどの URL が handler に届いているかを追うため、入口で必ず記録する。
        LOG.warn("PHPStan doc link: resolveLink"
                + " (url=" + url + ", target=" + target.getClass().getSimpleName() + ")");
        if (!url.startsWith(PhpStanTypeDocumentationLink.PSI_ELEMENT_PROTOCOL)) {
            return null;
        }
        var keyword = PhpStanTypeDocumentationLink.parseKeyword(url);
        if (keyword.isPresent()) {
            PsiElement keywordContext = contextElement(target);
            return keywordContext == null ? null : keyword
                    .flatMap(dev.javakky.phpstantypedoc.keyword.PhpStanTypeKeywords::find)
                    .map(found -> LinkResolveResult.resolvedTarget(
                            new PhpStanKeywordDocumentationTarget(found, keywordContext)))
                    .orElse(null);
        }
        var aliasReferenceOpt = PhpStanTypeDocumentationLink.parseAlias(url);
        boolean aliasLink = aliasReferenceOpt.isPresent();
        PsiElement context = contextElement(target);
        if (context == null) {
            LOG.warn("PHPStan doc link: context element not available"
                    + " (target=" + target.getClass().getSimpleName() + ", url=" + url + ")");
            return null;
        }
        if (!aliasLink) {
            return resolveClass(context, url.substring(
                    PhpStanTypeDocumentationLink.PSI_ELEMENT_PROTOCOL.length()));
        }
        var aliasReference = aliasReferenceOpt;
        LinkResolveResult alias = resolveAlias(context, aliasReference.get());
        if (alias != null) {
            return alias;
        }
        // エイリアスとして解決できないときは owner クラスへ送る。何も返さないと
        // 「ドキュメントが見つかりません」になり、リンクが死ぬだけになるため。
        LOG.warn("PHPStan doc link: alias not resolved, falling back to owner class (url=" + url + ")");
        return resolveClass(context, aliasReference.get().ownerClassFqn());
    }

    /**
     * クラス、または名前空間へのリンクを解決する。
     *
     * <p>PHP が生成する documentation には名前空間へのリンク ({@code psi_element://\App\Domain\})
     * も含まれる。この plugin の target を表示している間は platform 標準の PSI handler が働かないため、
     * ここで面倒を見ないとクリックが「ドキュメントが見つかりません」になる。
     */
    private @Nullable LinkResolveResult resolveClass(PsiElement context, String rawFqn) {
        String fqn = stripArraySuffix(rawFqn);
        PhpIndex index = PhpIndex.getInstance(context.getProject());
        Collection<PhpClass> classes = index.getClassesByFQN(fqn);
        if (classes.size() == 1) {
            return LinkResolveResult.resolvedTarget(
                    new PhpClassDocumentationTarget(classes.iterator().next(), context));
        }
        var namespace = index.getNamespacesByName(stripTrailingSeparator(fqn)).stream().findFirst();
        if (namespace.isPresent()) {
            return LinkResolveResult.resolvedTarget(
                    new PhpNamespaceDocumentationTarget(namespace.get(), context));
        }
        LOG.warn("PHPStan doc link: neither class nor namespace resolved"
                + " (fqn=" + fqn + ", classes=" + classes.size() + ")");
        return null;
    }

    /**
     * PHP は配列型のリンクを要素クラスに {@code []} を付けた形で出す
     * ({@code non-empty-list<User>} → {@code psi_element://\App\Domain\User[]})。
     * そのままではクラスとして引けず、クリックが「不正な形式の URL です」になる。
     */
    private String stripArraySuffix(String fqn) {
        String stripped = fqn;
        while (stripped.endsWith("[]")) {
            stripped = stripped.substring(0, stripped.length() - 2);
        }
        return stripped;
    }

    private String stripTrailingSeparator(String fqn) {
        String trimmed = fqn.endsWith("\\") ? fqn.substring(0, fqn.length() - 1) : fqn;
        return trimmed.startsWith("\\") ? trimmed.substring(1) : trimmed;
    }

    private @Nullable LinkResolveResult resolveAlias(
            PsiElement context,
            PhpStanTypeDocumentationLink.AliasReference reference
    ) {
        PsiFile file = context.getContainingFile();
        if (file == null) {
            LOG.warn("PHPStan doc link: containing file not available for alias " + reference.aliasName());
            return null;
        }
        Optional<PhpStanResolvedType> resolved = resolveInAnyFile(
                context, file, reference.ownerClassFqn(), reference.aliasName());
        if (resolved.isEmpty()) {
            LOG.warn("PHPStan doc link: alias not resolvable in class"
                    + " (owner=" + reference.ownerClassFqn() + ", alias=" + reference.aliasName()
                    + ", file=" + file.getName() + ")");
            return null;
        }
        SourceRange contextRange = new SourceRange(
                context.getTextRange().getStartOffset(), context.getTextRange().getEndOffset());
        var aliasTarget = new ResolvedPhpStanTypeTarget(
                resolved.get(),
                new DetectedTypeReference(reference.aliasName(), contextRange),
                definitionFile(context, resolved.get())
        );
        // リンク由来の target は範囲から復元できないので hard pointer で保持する
        return LinkResolveResult.resolvedTarget(
                new PhpStanTypeDocumentationTarget(file, aliasTarget, false));
    }

    /**
     * owner クラスが現在のファイルに無い場合 (元名のリンクなど) は、{@link PhpIndex} で
     * そのクラスのファイルを引いてから解決する。
     */
    private Optional<PhpStanResolvedType> resolveInAnyFile(
            PsiElement context,
            PsiFile currentFile,
            String ownerClassFqn,
            String aliasName
    ) {
        Optional<PhpStanResolvedType> inCurrentFile =
                resolver.resolveInClass(currentFile, ownerClassFqn, aliasName);
        if (inCurrentFile.isPresent()) {
            return inCurrentFile;
        }
        String normalized = ownerClassFqn.startsWith("\\") ? ownerClassFqn : "\\" + ownerClassFqn;
        for (PhpClass phpClass : PhpIndex.getInstance(context.getProject()).getClassesByFQN(normalized)) {
            PsiFile ownerFile = phpClass.getContainingFile();
            if (ownerFile == null) {
                continue;
            }
            Optional<PhpStanResolvedType> resolved =
                    resolver.resolveInClass(ownerFile, ownerClassFqn, aliasName);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private com.intellij.openapi.vfs.VirtualFile definitionFile(
            PsiElement context,
            PhpStanResolvedType resolved
    ) {
        String definingClassFqcn = resolved.definingClassFqcn();
        if (definingClassFqcn != null) {
            String normalized = definingClassFqcn.startsWith("\\")
                    ? definingClassFqcn : "\\" + definingClassFqcn;
            Collection<PhpClass> classes =
                    PhpIndex.getInstance(context.getProject()).getClassesByFQN(normalized);
            if (classes.size() == 1) {
                return classes.iterator().next().getContainingFile().getVirtualFile();
            }
        }
        return context.getContainingFile().getVirtualFile();
    }

    private PsiElement contextElement(DocumentationTarget target) {
        if (target instanceof PhpStanTypeDocumentationTarget phpStanTarget) {
            return phpStanTarget.contextElement();
        }
        if (target instanceof PhpClassDocumentationTarget classTarget) {
            return classTarget.contextElement();
        }
        if (target instanceof PhpFunctionDocumentationTarget functionTarget) {
            return functionTarget.contextElement();
        }
        if (target instanceof PhpNamespaceDocumentationTarget namespaceTarget) {
            return namespaceTarget.contextElement();
        }
        if (target instanceof PhpStanKeywordDocumentationTarget keywordTarget) {
            return keywordTarget.contextElement();
        }
        if (target instanceof SimplifiedVariableTypeTarget variableTarget) {
            // これが無いと変数ホバーの中のキーワードリンクが「押せるのに何も起きない」になる
            return variableTarget.contextElement();
        }
        return null;
    }
}
