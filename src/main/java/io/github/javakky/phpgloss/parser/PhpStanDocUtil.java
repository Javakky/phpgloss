package io.github.javakky.phpgloss.parser;

final class PhpStanDocUtil {
    private PhpStanDocUtil() {
    }

    static boolean isIdentifierStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    static boolean startsWithWord(String text, int offset, String word) {
        if (!text.startsWith(word, offset)) {
            return false;
        }
        int end = offset + word.length();
        return end >= text.length() || !isIdentifierPart(text.charAt(end));
    }
}

