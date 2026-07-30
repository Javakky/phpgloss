package dev.javakky.phpstantypedoc.doc;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;

import java.util.ArrayList;
import java.util.List;

/**
 * クラスの documentation に、そのクラスが宣言・import している型エイリアスの中身を並べる。
 *
 * <p>documentation のリンクからエイリアス専用の表示へ遷移する手段が platform に無い。
 * 独自 scheme のリンクは link handler へ渡らず、{@code psi_element://} に fragment を付けると
 * platform が PSI 解決を試みて失敗する。実在する PSI 要素への遷移だけが動くため、
 * エイリアス名のリンク先は定義元クラスにし、その行き先で中身が読めるようにする。
 */
final class PhpClassAliasDetails {
    private PhpClassAliasDetails() {
    }

    /**
     * @return 追記する HTML。エイリアスを 1 つも持たないクラスでは空文字
     */
    static String of(PhpClass phpClass, PhpStanTypeResolverFacade resolver) {
        PsiFile file = phpClass.getContainingFile();
        if (file == null || phpClass.getFQN() == null) {
            return "";
        }
        PhpFileModel fileModel = PhpFileModelCache.of(file);
        String fqcn = phpClass.getFQN().startsWith("\\")
                ? phpClass.getFQN().substring(1) : phpClass.getFQN();
        var block = fileModel.findClass(fqcn);
        if (block.isEmpty()) {
            return "";
        }

        List<String> names = new ArrayList<>();
        block.get().aliases().forEach(alias -> names.add(alias.aliasName()));
        block.get().imports().forEach(typeImport -> names.add(typeImport.localName()));
        if (names.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            resolver.resolveInClass(file, fqcn, name)
                    .ifPresent(resolved -> builder.append(section(file.getProject(), name, resolved)));
        }
        return builder.toString();
    }

    private static String section(Project project, String name, PhpStanResolvedType resolved) {
        var linkifier = new PhpStanTypeBodyLinkifier(project, resolved.definingClassFqcn());
        StringBuilder builder = new StringBuilder();
        builder.append(DocumentationMarkup.CONTENT_START)
                .append("<p><b>")
                .append(PhpStanTypeHtmlRenderer.escape(name))
                .append("</b></p>");
        if (resolved.imported()) {
            builder.append("<p>Imported type: ")
                    .append(PhpStanTypeHtmlRenderer.escape(resolved.originalName()))
                    .append("</p>");
        }
        builder.append("<pre><code>")
                .append(linkifier.linkify(PhpStanTypeHtmlRenderer.escape(resolved.typeBody())))
                .append("</code></pre>");
        String definingClassFqcn = resolved.definingClassFqcn();
        if (definingClassFqcn != null && !definingClassFqcn.isBlank()) {
            builder.append("<p>Defined in <a href='")
                    .append(PhpStanTypeDocumentationLink.forClass(definingClassFqcn))
                    .append("'>")
                    .append(PhpStanTypeHtmlRenderer.escape(definingClassFqcn))
                    .append("</a></p>");
        }
        return builder.append(DocumentationMarkup.CONTENT_END).toString();
    }
}