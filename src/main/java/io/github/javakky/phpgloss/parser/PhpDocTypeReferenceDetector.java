package io.github.javakky.phpgloss.parser;

import io.github.javakky.phpgloss.model.SourceRange;

import java.util.Optional;
import java.util.Set;

public final class PhpDocTypeReferenceDetector {
    /**
     * 型を書くタグ。同じことを三通りに書けるので、どれで書かれていても入口を開ける。
     *
     * <p>ここは hover と <kbd>Ctrl</kbd>+<kbd>B</kbd> の入口。型解決だけが綴りを知っていると、
     * 型は出るのに定義へ飛べない、という食い違いになる。
     */
    private static final Set<String> SUPPORTED_TAGS = Set.of(
            "@param",
            "@return",
            "@var",
            "@phpstan-param",
            "@phpstan-return",
            "@phpstan-var",
            "@psalm-param",
            "@psalm-return",
            "@psalm-var"
    );

    public Optional<DetectedTypeReference> detect(String rawPhpDoc, int docStartOffset, int fileOffset) {
        int localOffset = fileOffset - docStartOffset;
        if (localOffset < 0 || localOffset >= rawPhpDoc.length()) {
            return Optional.empty();
        }
        char current = rawPhpDoc.charAt(localOffset);
        if (!PhpStanDocUtil.isIdentifierPart(current)) {
            return Optional.empty();
        }
        int start = localOffset;
        while (start > 0 && PhpStanDocUtil.isIdentifierPart(rawPhpDoc.charAt(start - 1))) {
            start--;
        }
        int end = localOffset + 1;
        while (end < rawPhpDoc.length() && PhpStanDocUtil.isIdentifierPart(rawPhpDoc.charAt(end))) {
            end++;
        }
        if (start > 0 && (rawPhpDoc.charAt(start - 1) == '@' || rawPhpDoc.charAt(start - 1) == '$')) {
            return Optional.empty();
        }
        int lineStart = rawPhpDoc.lastIndexOf('\n', localOffset);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int lineEnd = rawPhpDoc.indexOf('\n', localOffset);
        lineEnd = lineEnd < 0 ? rawPhpDoc.length() : lineEnd;

        int contentStart = skipDocDecoration(rawPhpDoc, lineStart, lineEnd);
        if (contentStart + 2 < lineEnd && rawPhpDoc.startsWith("/**", contentStart)) {
            contentStart += 3;
            if (contentStart < lineEnd && rawPhpDoc.charAt(contentStart) == ' ') {
                contentStart++;
            }
        }
        if (contentStart >= lineEnd || rawPhpDoc.charAt(contentStart) != '@') {
            return Optional.empty();
        }
        int tagEnd = contentStart + 1;
        while (tagEnd < lineEnd) {
            char c = rawPhpDoc.charAt(tagEnd);
            if (!(PhpStanDocUtil.isIdentifierPart(c) || c == '-')) {
                break;
            }
            tagEnd++;
        }
        String tag = rawPhpDoc.substring(contentStart, tagEnd);
        if (!SUPPORTED_TAGS.contains(tag)) {
            return Optional.empty();
        }
        if (start < tagEnd) {
            return Optional.empty();
        }
        int typeStart = skipHorizontal(rawPhpDoc, tagEnd, lineEnd);
        if (typeStart >= lineEnd) {
            return Optional.empty();
        }
        int effectiveLineEnd = stripClosingDelimiter(rawPhpDoc, typeStart, lineEnd);
        int typeEnd = computeTypeEnd(rawPhpDoc, typeStart, effectiveLineEnd);
        if (start < typeStart || end > typeEnd) {
            return Optional.empty();
        }
        return Optional.of(new DetectedTypeReference(rawPhpDoc.substring(start, end), new SourceRange(docStartOffset + start, docStartOffset + end)));
    }

    private int computeTypeEnd(String text, int offset, int limit) {
        int curly = 0;
        int round = 0;
        int angle = 0;
        int square = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = offset; i < limit; i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
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
            if (curly == 0 && round == 0 && angle == 0 && square == 0 && Character.isWhitespace(c)) {
                return i;
            }
            if (c == '$' && curly == 0 && round == 0 && angle == 0 && square == 0) {
                return i;
            }
        }
        return limit;
    }

    private int skipDocDecoration(String text, int start, int end) {
        int cursor = start;
        while (cursor < end && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t')) {
            cursor++;
        }
        if (cursor < end && text.charAt(cursor) == '*') {
            cursor++;
            if (cursor < end && text.charAt(cursor) == ' ') {
                cursor++;
            }
        }
        return cursor;
    }

    private int stripClosingDelimiter(String text, int start, int end) {
        int closing = text.lastIndexOf("*/", end);
        if (closing >= start && closing < end) {
            int stripped = closing;
            while (stripped > start && (text.charAt(stripped - 1) == ' ' || text.charAt(stripped - 1) == '\t')) {
                stripped--;
            }
            return stripped;
        }
        return end;
    }

    private int skipHorizontal(String text, int start, int end) {
        int cursor = start;
        while (cursor < end && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t')) {
            cursor++;
        }
        return cursor;
    }
}
