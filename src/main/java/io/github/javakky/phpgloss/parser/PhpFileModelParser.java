package io.github.javakky.phpgloss.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.javakky.phpgloss.model.SourceRange;

public final class PhpFileModelParser {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("(?m)^\\s*namespace\\s+([^;{]+)\\s*[;{]");
    private static final Pattern USE_PATTERN = Pattern.compile("(?m)^\\s*use\\s+([^;]+);");
    private static final Pattern GROUP_USE_PATTERN = Pattern.compile("(?s)^(.+?)\\\\\\{(.*)\\}$");
    private static final Pattern AS_PATTERN =
            Pattern.compile("^(.+?)\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)$", Pattern.CASE_INSENSITIVE);
    /**
     * 宣言と、その直前の PHPDoc。
     *
     * <p>コメントの中身を `.*?` で書くと、直後に宣言が来ないときに次の `*&#47;` まで伸びる。伸びた先に
     * 別のクラスがあると、そのクラスの PHPDoc としてメンバのコメントが読まれ、本来の宣言 PHPDoc は
     * 読まれない。`*&#47;` を含まない、と書いて伸びる余地をなくす。
     */
    private static final Pattern CLASS_LIKE_PATTERN = Pattern.compile(
            "(?s)/\\*\\*(?:(?!\\*/).)*\\*/\\s*"
                    + "(?:#\\[(?:[^\\[\\]]|\\[[^\\[\\]]*\\])*\\]\\s*)*"
                    + "(?:(?:final|abstract|readonly)\\s+)*"
                    + "(class|interface|trait|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final PhpDocAliasParser docParser = new PhpDocAliasParser();

    public PhpFileModel parse(String text) {
        List<Declared> namespaces = parseNamespaces(text);
        List<Declared> imports = parseUses(text);
        String namespace = namespaces.isEmpty() ? null : namespaces.get(0).value();
        Map<String, String> uses = usesBefore(imports, text.length(), namespaces);
        List<PhpFileModel.ClassLikeDocBlock> blocks = new ArrayList<>();
        Matcher matcher = CLASS_LIKE_PATTERN.matcher(text);
        while (matcher.find()) {
            int docStart = text.indexOf("/**", matcher.start());
            int docEnd = text.indexOf("*/", docStart);
            if (docStart < 0 || docEnd < 0 || docEnd > matcher.end()) {
                continue;
            }
            docEnd += 2;
            String shortName = matcher.group(2);
            String own = namespaceAt(namespaces, docStart);
            String fqcn = own == null || own.isBlank() ? shortName : own + "\\" + shortName;
            String rawDoc = text.substring(docStart, docEnd);
            ParsedPhpDoc parsed = docParser.parse(rawDoc, docStart, fqcn);
            blocks.add(new PhpFileModel.ClassLikeDocBlock(
                    fqcn,
                    shortName,
                    own,
                    usesBefore(imports, docStart, namespaces),
                    new SourceRange(docStart, docEnd),
                    rawDoc,
                    parsed.aliases(),
                    parsed.imports()
            ));
        }
        return new PhpFileModel(namespace, uses, List.copyOf(blocks));
    }

    /** ファイルのどこに何が書かれていたか。namespace と use はどちらも位置で効き方が変わる。 */
    private record Declared(int offset, String value, String local) {
    }

    /** 一つのファイルに namespace はいくつでも書ける。宣言はそれぞれ自分より上の最後のものに属する。 */
    private List<Declared> parseNamespaces(String text) {
        List<Declared> found = new ArrayList<>();
        Matcher matcher = NAMESPACE_PATTERN.matcher(text);
        while (matcher.find()) {
            found.add(new Declared(matcher.start(), matcher.group(1).trim().replaceAll("\\s+", ""), null));
        }
        return List.copyOf(found);
    }

    private String namespaceAt(List<Declared> namespaces, int offset) {
        String current = null;
        for (Declared declared : namespaces) {
            if (declared.offset() > offset) {
                break;
            }
            current = declared.value();
        }
        return current;
    }

    /**
     * その位置から見える use。
     *
     * <p>use は自分が書かれた namespace の中でだけ効く。二つめの namespace の宣言が一つめの use を
     * 拾うと、別のクラスへ解決してしまう。
     */
    private Map<String, String> usesBefore(List<Declared> imports, int offset, List<Declared> namespaces) {
        String own = namespaceAt(namespaces, offset);
        Map<String, String> uses = new LinkedHashMap<>();
        for (Declared declared : imports) {
            if (declared.offset() > offset) {
                break;
            }
            if (!Objects.equals(own, namespaceAt(namespaces, declared.offset()))) {
                continue;
            }
            uses.put(declared.local(), declared.value());
        }
        return Map.copyOf(uses);
    }

    private List<Declared> parseUses(String text) {
        List<Declared> imports = new ArrayList<>();
        Matcher matcher = USE_PATTERN.matcher(text);
        while (matcher.find()) {
            String statement = matcher.group(1).trim();
            if (statement.startsWith("function ") || statement.startsWith("const ")) {
                continue;
            }
            for (String part : splitImport(statement)) {
                addImport(imports, matcher.start(), part);
            }
        }
        return List.copyOf(imports);
    }

    /** `use A\{B, C as D};` を、一つずつ書いたときと同じ形に開く。 */
    private List<String> splitImport(String statement) {
        Matcher grouped = GROUP_USE_PATTERN.matcher(statement);
        if (!grouped.find()) {
            return List.of(statement.split(","));
        }
        String prefix = cleanupFqcn(grouped.group(1));
        List<String> parts = new ArrayList<>();
        for (String rawPart : grouped.group(2).split(",")) {
            String part = rawPart.trim();
            if (part.isEmpty() || part.startsWith("function ") || part.startsWith("const ")) {
                continue;
            }
            parts.add(prefix + "\\" + part);
        }
        return List.copyOf(parts);
    }

    private void addImport(List<Declared> imports, int offset, String rawPart) {
        String part = rawPart.trim();
        Matcher alias = AS_PATTERN.matcher(part);
        String fqcn;
        String local;
        if (alias.find()) {
            fqcn = cleanupFqcn(alias.group(1));
            local = alias.group(2);
        } else {
            fqcn = cleanupFqcn(part);
            int slash = fqcn.lastIndexOf('\\');
            local = slash < 0 ? fqcn : fqcn.substring(slash + 1);
        }
        if (!local.isBlank() && !fqcn.isBlank()) {
            imports.add(new Declared(offset, fqcn, local));
        }
    }

    private String cleanupFqcn(String value) {
        String cleaned = value.trim().replaceAll("\\s+", "");
        return cleaned.startsWith("\\") ? cleaned.substring(1) : cleaned;
    }
}
