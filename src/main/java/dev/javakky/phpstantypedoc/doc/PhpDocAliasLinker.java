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

    /**
     * @param aliasTargets true なら alias 自身の documentation へ、false なら定義元クラスへ向ける。
     *                     エディタ内のレンダリング表示は Quick Documentation と別の経路で
     *                     リンクを処理し、{@code Owner::Alias} 形式を「不正な形式の URL」として
     *                     弾く。そちらでは実在する PSI 要素にしか飛べない
     */
    static String linkAliasNames(
            PhpClass phpClass,
            String html,
            PhpStanTypeResolverFacade resolver,
            boolean aliasTargets
    ) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Map<String, String> urls = aliasUrls(phpClass, resolver, aliasTargets);
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
    private static Map<String, String> aliasUrls(
            PhpClass phpClass,
            PhpStanTypeResolverFacade resolver,
            boolean aliasTargets
    ) {
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
            urls.put(alias.aliasName(), aliasTargets
                    ? PhpStanTypeDocumentationLink.forAlias(classFqn, alias.aliasName())
                    : PhpStanTypeDocumentationLink.forClass(classFqn));
        }
        for (var typeImport : block.get().imports()) {
            String definingFqcn = resolver.resolveInClass(file, classFqn, typeImport.localName())
                    .map(PhpStanResolvedType::definingClassFqcn)
                    .filter(fqcn -> fqcn != null && !fqcn.isBlank())
                    .orElse(null);
            urls.put(typeImport.localName(), aliasTargets
                    ? PhpStanTypeDocumentationLink.forAlias(classFqn, typeImport.localName())
                    : PhpStanTypeDocumentationLink.forClass(
                            definingFqcn == null ? classFqn : definingFqcn));
            if (definingFqcn != null) {
                urls.put(typeImport.originalName(), aliasTargets
                        ? PhpStanTypeDocumentationLink.forAlias(definingFqcn, typeImport.originalName())
                        : PhpStanTypeDocumentationLink.forClass(definingFqcn));
            }
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
