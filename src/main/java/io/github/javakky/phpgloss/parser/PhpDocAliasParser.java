package io.github.javakky.phpgloss.parser;

import io.github.javakky.phpgloss.model.PhpStanTypeAliasDefinition;
import io.github.javakky.phpgloss.model.PhpStanTypeImport;
import io.github.javakky.phpgloss.model.SourceRange;

import java.util.ArrayList;
import java.util.List;

public final class PhpDocAliasParser {
    public ParsedPhpDoc parse(String rawPhpDoc, int baseOffset, String containingClassFqcn) {
        try {
            NormalizedDoc normalized = normalize(rawPhpDoc, baseOffset);
            List<PhpStanTypeAliasDefinition> aliases = new ArrayList<>();
            List<PhpStanTypeImport> imports = new ArrayList<>();
            String text = normalized.text();
            int offset = 0;
            while (offset < text.length()) {
                int tag = text.indexOf("@phpstan-", offset);
                if (tag < 0) {
                    break;
                }
                if (PhpStanDocUtil.startsWithWord(text, tag, "@phpstan-type")) {
                    AliasParseResult result = parseAlias(normalized, tag, containingClassFqcn);
                    if (result.alias() != null) {
                        aliases.add(result.alias());
                    }
                    offset = Math.max(tag + 1, result.nextOffset());
                } else if (PhpStanDocUtil.startsWithWord(text, tag, "@phpstan-import-type")) {
                    ImportParseResult result = parseImport(normalized, tag);
                    if (result.typeImport() != null) {
                        imports.add(result.typeImport());
                    }
                    offset = Math.max(tag + 1, result.nextOffset());
                } else {
                    offset = tag + 1;
                }
            }
            return new ParsedPhpDoc(List.copyOf(aliases), List.copyOf(imports));
        } catch (RuntimeException ignored) {
            return new ParsedPhpDoc(List.of(), List.of());
        }
    }

    public ParsedPhpDoc parse(String rawPhpDoc) {
        return parse(rawPhpDoc, 0, null);
    }

    private AliasParseResult parseAlias(NormalizedDoc doc, int tagStart, String containingClassFqcn) {
        String text = doc.text();
        int cursor = skipHorizontalWhitespace(text, tagStart + "@phpstan-type".length());
        int nameStart = cursor;
        if (nameStart >= text.length() || !PhpStanDocUtil.isIdentifierStart(text.charAt(nameStart))) {
            return new AliasParseResult(null, tagStart + 1);
        }
        cursor++;
        while (cursor < text.length() && PhpStanDocUtil.isIdentifierPart(text.charAt(cursor))) {
            cursor++;
        }
        int nameEnd = cursor;
        String name = text.substring(nameStart, nameEnd);
        int typeStart = skipHorizontalWhitespace(text, cursor);
        int declarationEnd = findDeclarationEnd(text, typeStart);
        String typeExpression = trimTrailingLineWhitespace(text.substring(typeStart, declarationEnd));
        int typeEnd = typeStart + typeExpression.length();
        if (typeExpression.isBlank()) {
            return new AliasParseResult(null, declarationEnd);
        }
        PhpStanTypeAliasDefinition alias = new PhpStanTypeAliasDefinition(
                name,
                typeExpression,
                containingClassFqcn,
                doc.range(nameStart, nameEnd),
                doc.range(tagStart, declarationEnd),
                doc.range(typeStart, typeEnd)
        );
        return new AliasParseResult(alias, declarationEnd);
    }

    private ImportParseResult parseImport(NormalizedDoc doc, int tagStart) {
        String text = doc.text();
        int cursor = skipHorizontalWhitespace(text, tagStart + "@phpstan-import-type".length());
        int originalStart = cursor;
        if (originalStart >= text.length() || !PhpStanDocUtil.isIdentifierStart(text.charAt(originalStart))) {
            return new ImportParseResult(null, tagStart + 1);
        }
        cursor++;
        while (cursor < text.length() && PhpStanDocUtil.isIdentifierPart(text.charAt(cursor))) {
            cursor++;
        }
        int originalEnd = cursor;
        cursor = skipHorizontalWhitespace(text, cursor);
        if (!PhpStanDocUtil.startsWithWord(text, cursor, "from")) {
            return new ImportParseResult(null, cursor);
        }
        cursor = skipHorizontalWhitespace(text, cursor + "from".length());
        int sourceStart = cursor;
        while (cursor < text.length()) {
            char c = text.charAt(cursor);
            if (Character.isWhitespace(c)) {
                break;
            }
            cursor++;
        }
        int sourceEnd = cursor;
        if (sourceEnd == sourceStart) {
            return new ImportParseResult(null, cursor);
        }
        cursor = skipHorizontalWhitespace(text, cursor);
        String localName = text.substring(originalStart, originalEnd);
        SourceRange localNameRange = doc.range(originalStart, originalEnd);
        if (PhpStanDocUtil.startsWithWord(text, cursor, "as")) {
            cursor = skipHorizontalWhitespace(text, cursor + 2);
            int localStart = cursor;
            if (localStart < text.length() && PhpStanDocUtil.isIdentifierStart(text.charAt(localStart))) {
                cursor++;
                while (cursor < text.length() && PhpStanDocUtil.isIdentifierPart(text.charAt(cursor))) {
                    cursor++;
                }
                localName = text.substring(localStart, cursor);
                localNameRange = doc.range(localStart, cursor);
            }
        }
        int declarationEnd = lineEnd(text, cursor);
        PhpStanTypeImport typeImport = new PhpStanTypeImport(
                text.substring(originalStart, originalEnd),
                text.substring(sourceStart, sourceEnd),
                localName,
                doc.range(originalStart, originalEnd),
                doc.range(sourceStart, sourceEnd),
                localNameRange,
                doc.range(tagStart, declarationEnd)
        );
        return new ImportParseResult(typeImport, declarationEnd);
    }

    private int findDeclarationEnd(String text, int typeStart) {
        int curly = 0;
        int round = 0;
        int angle = 0;
        int square = 0;
        char quote = 0;
        boolean escaped = false;
        int lastParsable = typeStart;
        int i = typeStart;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quote != 0) {
                lastParsable = i + 1;
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                lastParsable = i + 1;
                i++;
                continue;
            }
            switch (c) {
                case '{' -> curly++;
                case '}' -> curly = Math.max(0, curly - 1);
                case '(' -> round++;
                case ')' -> round = Math.max(0, round - 1);
                case '<' -> angle++;
                case '>' -> angle = Math.max(0, angle - 1);
                case '[' -> square++;
                case ']' -> square = Math.max(0, square - 1);
                default -> {
                }
            }
            if (curly == 0 && round == 0 && angle == 0 && square == 0 && c == '\n') {
                int next = skipHorizontalWhitespace(text, i + 1);
                if (next >= text.length() || text.charAt(next) == '@') {
                    return i;
                }
            }
            lastParsable = i + 1;
            i++;
        }
        return lastParsable;
    }

    private int skipHorizontalWhitespace(String text, int offset) {
        int cursor = offset;
        while (cursor < text.length()) {
            char c = text.charAt(cursor);
            if (c != ' ' && c != '\t') {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    private int lineEnd(String text, int offset) {
        int newline = text.indexOf('\n', offset);
        return newline < 0 ? text.length() : newline;
    }

    private String trimTrailingLineWhitespace(String value) {
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    private NormalizedDoc normalize(String raw, int baseOffset) {
        StringBuilder text = new StringBuilder(raw.length());
        List<Integer> originalOffsets = new ArrayList<>(raw.length());
        int lineStart = 0;
        while (lineStart < raw.length()) {
            int lineEnd = raw.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = raw.length();
            }
            int contentStart = lineStart;
            while (contentStart < lineEnd && (raw.charAt(contentStart) == ' ' || raw.charAt(contentStart) == '\t')) {
                contentStart++;
            }
            if (contentStart + 1 < lineEnd && raw.startsWith("*/", contentStart)) {
                contentStart = lineEnd;
            } else if (contentStart < lineEnd && raw.charAt(contentStart) == '*') {
                contentStart++;
                if (contentStart < lineEnd && raw.charAt(contentStart) == ' ') {
                    contentStart++;
                }
            }
            if (contentStart + 2 < lineEnd && raw.startsWith("/**", contentStart)) {
                contentStart += 3;
                if (contentStart < lineEnd && raw.charAt(contentStart) == ' ') {
                    contentStart++;
                }
            }
            int contentEnd = lineEnd;
            int closing = raw.lastIndexOf("*/", lineEnd);
            if (closing >= contentStart && closing < lineEnd) {
                contentEnd = closing;
                while (contentEnd > contentStart && (raw.charAt(contentEnd - 1) == ' ' || raw.charAt(contentEnd - 1) == '\t')) {
                    contentEnd--;
                }
            }
            for (int i = contentStart; i < contentEnd; i++) {
                text.append(raw.charAt(i));
                originalOffsets.add(baseOffset + i);
            }
            if (lineEnd < raw.length()) {
                text.append('\n');
                originalOffsets.add(baseOffset + lineEnd);
            }
            lineStart = lineEnd + 1;
        }
        return new NormalizedDoc(text.toString(), originalOffsets);
    }

    private record NormalizedDoc(String text, List<Integer> originalOffsets) {
        SourceRange range(int normalizedStart, int normalizedEnd) {
            if (originalOffsets.isEmpty()) {
                return new SourceRange(0, 0);
            }
            int start = originalOffsets.get(Math.min(normalizedStart, originalOffsets.size() - 1));
            int end;
            if (normalizedEnd <= 0) {
                end = start;
            } else if (normalizedEnd >= originalOffsets.size()) {
                end = originalOffsets.get(originalOffsets.size() - 1) + 1;
            } else {
                end = originalOffsets.get(normalizedEnd - 1) + 1;
            }
            return new SourceRange(start, end);
        }
    }

    private record AliasParseResult(PhpStanTypeAliasDefinition alias, int nextOffset) {
    }

    private record ImportParseResult(PhpStanTypeImport typeImport, int nextOffset) {
    }
}
