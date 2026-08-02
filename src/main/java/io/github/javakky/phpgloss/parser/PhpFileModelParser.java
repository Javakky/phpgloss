package io.github.javakky.phpgloss.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.javakky.phpgloss.model.SourceRange;

public final class PhpFileModelParser {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("(?m)^\\s*namespace\\s+([^;{]+)\\s*[;{]");
    private static final Pattern USE_PATTERN = Pattern.compile("(?m)^\\s*use\\s+([^;]+);");
    private static final Pattern CLASS_LIKE_PATTERN = Pattern.compile("(?s)/\\*\\*.*?\\*/\\s*(?:final\\s+|abstract\\s+|readonly\\s+)*(class|interface|trait|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final PhpDocAliasParser docParser = new PhpDocAliasParser();

    public PhpFileModel parse(String text) {
        String namespace = parseNamespace(text);
        Map<String, String> uses = parseUses(text);
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
            String fqcn = namespace == null || namespace.isBlank() ? shortName : namespace + "\\" + shortName;
            String rawDoc = text.substring(docStart, docEnd);
            ParsedPhpDoc parsed = docParser.parse(rawDoc, docStart, fqcn);
            blocks.add(new PhpFileModel.ClassLikeDocBlock(
                    fqcn,
                    shortName,
                    namespace,
                    uses,
                    new SourceRange(docStart, docEnd),
                    rawDoc,
                    parsed.aliases(),
                    parsed.imports()
            ));
        }
        return new PhpFileModel(namespace, uses, List.copyOf(blocks));
    }

    private String parseNamespace(String text) {
        Matcher matcher = NAMESPACE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim().replaceAll("\\s+", "");
    }

    private Map<String, String> parseUses(String text) {
        Map<String, String> uses = new LinkedHashMap<>();
        Matcher matcher = USE_PATTERN.matcher(text);
        while (matcher.find()) {
            String statement = matcher.group(1).trim();
            if (statement.startsWith("function ") || statement.startsWith("const ") || statement.contains("{")) {
                continue;
            }
            String[] parts = statement.split(",");
            for (String rawPart : parts) {
                String part = rawPart.trim();
                Matcher alias = Pattern.compile("^(.+?)\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)$", Pattern.CASE_INSENSITIVE).matcher(part);
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
                    uses.put(local, fqcn);
                }
            }
        }
        return Map.copyOf(uses);
    }

    private String cleanupFqcn(String value) {
        String cleaned = value.trim().replaceAll("\\s+", "");
        return cleaned.startsWith("\\") ? cleaned.substring(1) : cleaned;
    }
}
