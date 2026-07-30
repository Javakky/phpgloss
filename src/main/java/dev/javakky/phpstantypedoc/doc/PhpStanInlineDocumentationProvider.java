package dev.javakky.phpstantypedoc.doc;

import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.InlineDocumentation;
import com.intellij.platform.backend.documentation.InlineDocumentationProvider;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.PhpDocumentationProvider;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
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
    private final PhpStanTypeResolverFacade resolver = new PhpStanTypeResolverFacade();
    private final PhpDocumentationProvider provider = new PhpDocumentationProvider();

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
            return Optional.of(new AnnotatedInlineDocumentation(
                    comment, phpClass, html -> PhpDocAliasLinker.linkAliasNames(phpClass, html, resolver, false)));
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
            String html = provider.generateRenderedDoc(comment);
            return html == null ? null : annotate.apply(html);
        }

        @Override
        public @Nullable DocumentationTarget getOwnerTarget() {
            if (owner instanceof Function function) {
                return PhpFunctionDocumentationTarget
                        .at(function.getContainingFile(), function.getTextOffset(), resolver)
                        .orElse(null);
            }
            return null;
        }
    }
}
