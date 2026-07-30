package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * クラスの PHPDoc を描画した HTML の中で、型エイリアス名をリンクにする。
 *
 * <p>PHP は `@phpstan-import-type UserData from User as CreateUserData` を描画するとき
 * `from` の後のクラス名だけをリンクにし、エイリアス名 (`UserData` / `CreateUserData`) は
 * 素の {@code <span>} で出す。そこを、そのエイリアスの documentation へのリンクに差し替える。
 *
 * <p>置換は {@code <span>} の中身が名前と完全一致するものだけを対象にする。部分文字列
 * (`CreateUserData` の中の `UserData`) や、既にリンクになっている要素を巻き込まないため。
 */
final class PhpDocAliasLinker {
    private PhpDocAliasLinker() {
    }

    static String linkAliasNames(PhpClass phpClass, String html, PhpStanTypeResolverFacade resolver) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Map<String, String> urls = aliasUrls(phpClass, resolver);
        String result = html;
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            result = replaceSpanText(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * @return エイリアス名 → リンク URL。import は別名と元名の両方を対象にし、
     *         元名は定義元クラスを owner にしたリンクへ向ける
     */
    private static Map<String, String> aliasUrls(PhpClass phpClass, PhpStanTypeResolverFacade resolver) {
        Map<String, String> urls = new LinkedHashMap<>();
        PsiFile file = phpClass.getContainingFile();
        if (file == null || phpClass.getFQN() == null) {
            return urls;
        }
        String classFqn = phpClass.getFQN().startsWith("\\")
                ? phpClass.getFQN().substring(1) : phpClass.getFQN();
        PhpFileModel fileModel = PhpFileModelCache.of(file);
        var block = fileModel.findClass(classFqn);
        if (block.isEmpty()) {
            return urls;
        }
        for (var alias : block.get().aliases()) {
            urls.put(alias.aliasName(), PhpStanTypeDocumentationLink.forAlias(classFqn, alias.aliasName()));
        }
        for (var typeImport : block.get().imports()) {
            urls.put(typeImport.localName(),
                    PhpStanTypeDocumentationLink.forAlias(classFqn, typeImport.localName()));
            resolver.resolveInClass(file, classFqn, typeImport.localName())
                    .map(PhpStanResolvedType::definingClassFqcn)
                    .filter(fqcn -> fqcn != null && !fqcn.isBlank())
                    .ifPresent(definingFqcn -> urls.put(typeImport.originalName(),
                            PhpStanTypeDocumentationLink.forAlias(definingFqcn, typeImport.originalName())));
        }
        return urls;
    }

    private static String replaceSpanText(String html, String name, String url) {
        Pattern pattern = Pattern.compile("(<span[^>]*>)(" + Pattern.quote(name) + ")(</span>)");
        Matcher matcher = pattern.matcher(html);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(
                    matcher.group(1) + "<a href='" + url + "'>" + matcher.group(2) + "</a>" + matcher.group(3)));
        }
        return matcher.appendTail(builder).toString();
    }
}
