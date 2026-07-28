package dev.javakky.phpstantypedoc.doc;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.PhpClass;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 型本体に現れるクラス名を Quick Documentation 内のリンクにする。
 *
 * <p>`array{users: non-empty-list<User>}` の `User` からクラス定義へ飛べるようにする。
 * リンクは PHP プラグインが使っている `psi_element://` 形式で、解決は platform の
 * {@code PsiDocumentationLinkHandler} に任せる。
 *
 * <p>候補は大文字始まりの識別子に限る。PHPStan の型キーワード (`int` / `non-empty-list` 等) は
 * すべて小文字なので、これで実質的に除外できる。名前で引いて 1 クラスに定まらない場合は
 * 定義元と同じ名前空間のものを採り、それでも決まらなければリンクにしない。
 */
final class PhpStanTypeBodyLinkifier {
    private static final Pattern IDENTIFIER = Pattern.compile("\\\\?[A-Z][A-Za-z0-9_]*(?:\\\\[A-Z][A-Za-z0-9_]*)*");

    private final Project project;
    private final String definingClassFqcn;

    PhpStanTypeBodyLinkifier(Project project, String definingClassFqcn) {
        this.project = project;
        this.definingClassFqcn = definingClassFqcn;
    }

    /**
     * @param escapedTypeBody HTML escape 済みの型本体
     */
    String linkify(String escapedTypeBody) {
        if (escapedTypeBody == null || escapedTypeBody.isEmpty() || DumbService.isDumb(project)) {
            return escapedTypeBody;
        }
        StringBuilder builder = new StringBuilder(escapedTypeBody.length());
        Matcher matcher = IDENTIFIER.matcher(escapedTypeBody);
        int cursor = 0;
        while (matcher.find()) {
            String fqn = resolveClassFqn(matcher.group());
            if (fqn == null) {
                continue;
            }
            builder.append(escapedTypeBody, cursor, matcher.start())
                    .append("<a href=\"psi_element://").append(fqn).append("\">")
                    .append(matcher.group())
                    .append("</a>");
            cursor = matcher.end();
        }
        return builder.append(escapedTypeBody, cursor, escapedTypeBody.length()).toString();
    }

    /**
     * @return リンクにできる FQN (先頭に {@code \} を付けた形)。決められない場合は null
     */
    private String resolveClassFqn(String name) {
        PhpIndex index = PhpIndex.getInstance(project);
        if (name.contains("\\")) {
            String fqn = name.startsWith("\\") ? name : "\\" + name;
            return index.getClassesByFQN(fqn).isEmpty() ? null : fqn;
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
