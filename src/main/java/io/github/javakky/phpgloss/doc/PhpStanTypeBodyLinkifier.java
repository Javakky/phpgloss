package io.github.javakky.phpgloss.doc;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import io.github.javakky.phpgloss.keyword.PhpStanTypeKeywords;
import io.github.javakky.phpgloss.parser.PhpFileModel;
import io.github.javakky.phpgloss.parser.PhpFileModelCache;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 型本体に現れる名前を Quick Documentation 内のリンクにする。
 *
 * <p>`array{users: non-empty-list<User>}` なら、`User` はクラス定義へ、`non-empty-list` は
 * 型キーワードの説明へ飛ばす。
 *
 * <p>大文字始まりならクラス名として扱い、`psi_element://` 形式で PHP プラグインと同じ経路に乗せる。
 * 名前で引いて 1 クラスに定まらない場合は定義元と同じ名前空間のものを採り、それでも決まらなければ
 * リンクにしない。小文字始まりは同梱カタログに載っているものだけをキーワードとして扱う。
 *
 * <p>定義元クラスが宣言 / import しているエイリアス名は、同名クラスより先に見る。定義元に
 * 明示的に書かれた名前なので、そちらを指していることが確実なため。
 */
final class PhpStanTypeBodyLinkifier {
    private static final Pattern IDENTIFIER = Pattern.compile("\\\\?[A-Z][A-Za-z0-9_]*(?:\\\\[A-Z][A-Za-z0-9_]*)*");
    /** クラス名とキーワードをまとめて拾う。どちらとして扱うかは {@link #linkFor} で決める。 */
    private static final Pattern TOKEN = Pattern.compile("\\\\?[A-Za-z_][A-Za-z0-9_-]*(?:\\\\[A-Za-z_][A-Za-z0-9_]*)*");

    private final Project project;
    private final String definingClassFqcn;
    private final PsiFile contextFile;
    /** 走査のたびにファイルを引き直さないよう、最初に必要になった時点で作る。 */
    private Set<String> aliasNames;

    /**
     * @param contextFile hover 元のファイル。定義元クラスがここに無ければ index から辿る
     */
    PhpStanTypeBodyLinkifier(Project project, String definingClassFqcn, PsiFile contextFile) {
        this.project = project;
        this.definingClassFqcn = definingClassFqcn;
        this.contextFile = contextFile;
    }

    /**
     * @param escapedTypeBody HTML escape 済みの型本体
     */
    String linkify(String escapedTypeBody) {
        if (escapedTypeBody == null || escapedTypeBody.isEmpty() || DumbService.isDumb(project)) {
            return escapedTypeBody;
        }
        StringBuilder builder = new StringBuilder(escapedTypeBody.length());
        Matcher matcher = TOKEN.matcher(escapedTypeBody);
        int cursor = 0;
        while (matcher.find()) {
            String url = linkFor(escapedTypeBody, matcher);
            if (url == null) {
                continue;
            }
            builder.append(escapedTypeBody, cursor, matcher.start())
                    .append("<a href=\"").append(url).append("\">")
                    .append(matcher.group())
                    .append("</a>");
            cursor = matcher.end();
        }
        return builder.append(escapedTypeBody, cursor, escapedTypeBody.length()).toString();
    }

    /**
     * @return このトークンに張るリンク。張らない場合は null
     */
    private String linkFor(String escapedTypeBody, Matcher matcher) {
        String token = matcher.group();
        // キーワードかエイリアスかは綴りの形でなく、列挙されているかで決める。エイリアス名は
        // キャメルのこともスネークのこともあり、形から他と区別できない
        if (isShapeKey(escapedTypeBody, matcher.end())) {
            return null;
        }
        if (aliasNames().contains(token)) {
            return PhpStanTypeDocumentationLink.forAlias(definingClassFqcn, token);
        }
        if (IDENTIFIER.matcher(token).matches()) {
            String fqn = resolveClassFqn(token);
            return fqn == null ? null : "psi_element://" + fqn;
        }
        return PhpStanTypeKeywords.find(token)
                .map(keyword -> PhpStanTypeDocumentationLink.forKeyword(keyword.name()))
                .orElse(null);
    }

    /**
     * @return 定義元クラスが宣言 / import しているエイリアス名。辿れない場合は空
     */
    private Set<String> aliasNames() {
        if (aliasNames == null) {
            aliasNames = collectAliasNames();
        }
        return aliasNames;
    }

    private Set<String> collectAliasNames() {
        if (definingClassFqcn == null || definingClassFqcn.isBlank()) {
            return Set.of();
        }
        String fqcn = definingClassFqcn.startsWith("\\")
                ? definingClassFqcn.substring(1) : definingClassFqcn;
        Optional<PhpFileModel.ClassLikeDocBlock> block = definingClassBlock(fqcn);
        if (block.isEmpty()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        block.get().aliases().forEach(alias -> names.add(alias.aliasName()));
        block.get().imports().forEach(typeImport -> names.add(typeImport.localName()));
        return names;
    }

    /**
     * import されたエイリアスの hover では定義元クラスが別ファイルにあるので、
     * hover 元で見つからなければ index からそのクラスのファイルを引く。
     */
    private Optional<PhpFileModel.ClassLikeDocBlock> definingClassBlock(String fqcn) {
        if (contextFile != null) {
            Optional<PhpFileModel.ClassLikeDocBlock> inContext =
                    PhpFileModelCache.of(contextFile).findClass(fqcn);
            if (inContext.isPresent()) {
                return inContext;
            }
        }
        for (PhpClass phpClass : PhpIndex.getInstance(project).getAnyByFQN("\\" + fqcn)) {
            PsiFile ownerFile = phpClass.getContainingFile();
            if (ownerFile == null) {
                continue;
            }
            Optional<PhpFileModel.ClassLikeDocBlock> inOwner =
                    PhpFileModelCache.of(ownerFile).findClass(fqcn);
            if (inOwner.isPresent()) {
                return inOwner;
            }
        }
        return Optional.empty();
    }

    /**
     * `array{list: int}` の `list` はキー名であってキーワードではない。直後の `:` で見分ける。
     */
    private boolean isShapeKey(String text, int end) {
        int cursor = end;
        while (cursor < text.length() && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '?')) {
            cursor++;
        }
        return cursor < text.length() && text.charAt(cursor) == ':';
    }

    /**
     * @return リンクにできる FQN (先頭に {@code \} を付けた形)。決められない場合は null
     */
    private String resolveClassFqn(String name) {
        PhpIndex index = PhpIndex.getInstance(project);
        if (name.contains("\\")) {
            String fqn = name.startsWith("\\") ? name : "\\" + name;
            return index.getAnyByFQN(fqn).isEmpty() ? null : fqn;
        }
        Collection<PhpClass> candidates = index.getClassesByName(name);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next().getFQN();
        }
        return sameNamespaceCandidate(candidates);
    }

    /** 同名クラスが複数ある場合は、エイリアスを定義しているクラスと同じ名前空間のものを採る。 */
    private String sameNamespaceCandidate(Collection<PhpClass> candidates) {
        String namespace = namespaceOf(definingClassFqcn);
        if (namespace == null) {
            return null;
        }
        String match = null;
        for (PhpClass candidate : candidates) {
            if (namespace.equals(namespaceOf(candidate.getFQN()))) {
                if (match != null) {
                    return null;
                }
                match = candidate.getFQN();
            }
        }
        return match;
    }

    private String namespaceOf(String fqcn) {
        if (fqcn == null) {
            return null;
        }
        String normalized = fqcn.startsWith("\\") ? fqcn.substring(1) : fqcn;
        int separator = normalized.lastIndexOf('\\');
        return separator < 0 ? "" : normalized.substring(0, separator);
    }
}
