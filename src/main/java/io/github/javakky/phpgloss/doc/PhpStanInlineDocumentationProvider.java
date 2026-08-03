package io.github.javakky.phpgloss.doc;

import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.platform.backend.documentation.InlineDocumentationProvider;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import io.github.javakky.phpgloss.PhpStanTypeResolverFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * エディタ内に PHPDoc をレンダリング表示する経路 (鉛筆 / 罫線の切り替え) に型エイリアス名を出す。
 *
 * <p>Quick Documentation は {@code targetProvider} から来るが、レンダリング表示は
 * {@code inlineDocumentationProvider} から来る別経路で、PHP プラグインはこの拡張点を実装していない。
 * platform の互換実装が PHP の {@code generateRenderedDoc} を呼んでいるので、同じ HTML を得て
 * エイリアス名を追記したものを返す。
 */
public final class PhpStanInlineDocumentationProvider implements InlineDocumentationProvider {
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(PhpStanInlineDocumentationProvider.class);

    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();

    @Override
    public @NotNull Collection<InlineDocumentation> inlineDocumentationItems(PsiFile file) {
        if (file == null) {
            return List.of();
        }
        List<InlineDocumentation> items = new ArrayList<>();
        for (PhpDocComment comment : PsiTreeUtil.findChildrenOfType(file, PhpDocComment.class)) {
            annotatedDocumentation(file, comment).ifPresent(items::add);
        }
        return items;
    }

    @Override
    public @Nullable InlineDocumentation findInlineDocumentation(PsiFile file, TextRange textRange) {
        if (file == null || textRange == null) {
            return null;
        }
        // findElementOfClassAtRange は範囲がちょうど一致する要素しか返さない。platform が渡す
        // 範囲は PHPDoc の範囲と一致するとは限らないので、開始位置から親を辿る。
        var element = file.findElementAt(textRange.getStartOffset());
        var comment = PsiTreeUtil.getParentOfType(element, PhpDocComment.class, false);
        return comment == null ? null : annotatedDocumentation(file, comment).orElse(null);
    }

    /**
     * @return この PHPDoc が持つ関数の型エイリアスを解決できた場合だけ返す。
     *         解決できないときは何も返さず、platform の互換実装が出す PHP 標準の表示に任せる
     */
    private Optional<InlineDocumentation> annotatedDocumentation(PsiFile file, PhpDocComment comment) {
        // 所有者は PsiDocCommentBase#getOwner で取る。PHPDoc が所有者の子か前の兄弟かは
        // 要素の種類で変わるため、PSI を自分で辿らない。
        var owner = comment.getOwner();
        if (owner instanceof PhpClass phpClass) {
            // エイリアスを持たないクラスは PHP 標準の表示に任せる。加工する内容が無いのに
            // 描画を引き受けると、他プラグインが PHP のドキュメントへ足した内容まで落ちる
            if (!PhpDocAliasLinker.hasAliases(phpClass)) {
                return Optional.empty();
            }
            return Optional.of(new AnnotatedInlineDocumentation(
                    comment, phpClass, html -> PhpDocAliasLinker.linkAliasNames(phpClass, html, resolver, true)));
        }
        Function function = owner instanceof Function ownerFunction ? ownerFunction : null;
        if (function == null || function.isClosure()) {
            return Optional.empty();
        }
        return PhpDocAliasAnnotator.of(file, function, resolver)
                .map(annotator -> new AnnotatedInlineDocumentation(comment, function, annotator::annotate));
    }

    private final class AnnotatedInlineDocumentation implements InlineDocumentation {
        private final PsiDocCommentBase comment;
        private final PhpNamedElement owner;
        private final java.util.function.UnaryOperator<String> annotate;

        private AnnotatedInlineDocumentation(
                PsiDocCommentBase comment,
                PhpNamedElement owner,
                java.util.function.UnaryOperator<String> annotate
        ) {
            this.comment = comment;
            this.owner = owner;
            this.annotate = annotate;
        }

        @Override
        public @NotNull TextRange getDocumentationRange() {
            return comment.getTextRange();
        }

        @Override
        public @Nullable TextRange getDocumentationOwnerRange() {
            return owner.getTextRange();
        }

        @Override
        public @Nullable String renderText() {
            String html = baseHtml();
            // PHP が色を付けただけのキーワードはリンクになっていない。エイリアス名を差し込んだ
            // あとで、残りのキーワードにもリンクを足す
            String result = html == null ? null
                    : PhpDocKeywordLinker.linkBareKeywords(annotate.apply(html));
            // 罫線モードの描画結果は MCP から取得できない。実機で何が出ているかを追うため記録する。
            LOG.warn("PHPStan rendered doc: owner=" + owner.getName()
                    + " kind=" + owner.getClass().getSimpleName()
                    + " aliasLinks=" + countOccurrences(result, "phpgloss")
                    + " psiLinks=" + countOccurrences(result, "psi_element://")
                    + " html=" + (result == null ? "null" : result));
            return result;
        }

        /**
         * 加工の土台になる HTML を、この言語に登録された documentation provider から取る。
         *
         * <p>PHP の provider を直接 new すると、他プラグインが {@code lang.documentationProvider} で
         * PHP のドキュメントに足した内容が落ちる。platform の互換実装は同じことを
         * {@code DocumentationManager#getProviderFromElement} でやっているが、そちらは削除予定。
         */
        private @Nullable String baseHtml() {
            DocumentationProvider provider =
                    LanguageDocumentation.INSTANCE.forLanguage(comment.getLanguage());
            return provider == null ? null : provider.generateRenderedDoc(comment);
        }

        private int countOccurrences(String text, String needle) {
            if (text == null) {
                return -1;
            }
            int count = 0;
            int offset = 0;
            while ((offset = text.indexOf(needle, offset)) >= 0) {
                count++;
                offset += needle.length();
            }
            return count;
        }

        /**
         * レンダリング表示のリンクは、ここで返した target を起点に解決される
         * ({@code DocRenderDefaultLinkActivationHandler})。null を返すとリンクが解決先を
         * 持たず外部ブラウザに回され、「不正な形式の URL です」になる。
         */
        @Override
        public @Nullable DocumentationTarget getOwnerTarget() {
            if (owner instanceof Function function) {
                return PhpFunctionDocumentationTarget
                        .at(function.getContainingFile(), function.getTextOffset(), resolver)
                        .orElse(null);
            }
            if (owner instanceof PhpClass phpClass) {
                return new PhpStanClassDocumentationTargetProvider()
                        .documentationTarget(phpClass, phpClass);
            }
            return null;
        }
    }
}
