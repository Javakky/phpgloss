package dev.javakky.phpstantypedoc.type;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * `@phpstan-return ($mode is Shape::AS_LIST ? list<V> : array<K, V>)` の形を読む。
 *
 * <p>PhpStorm はこの構文をパースはするが評価しない (YouTrack WI-66465 は "Support *parsing* of ..."
 * であり、WI-85338 / WI-77577 が未解決)。実測でも実引数を変えても結果が変わらず、
 * 両分岐の union が返る。
 */
record ConditionalReturnType(String subject, String condition, String whenTrue, String whenFalse) {

    /**
     * @param declared `@phpstan-return` に書かれた本文
     * @return 条件型として読めた場合だけ返す
     */
    static @Nullable ConditionalReturnType parse(String declared) {
        if (declared == null) {
            return null;
        }
        String text = declared.trim();
        if (!text.startsWith("(") || !text.endsWith(")")) {
            return null;
        }
        String body = text.substring(1, text.length() - 1).trim();
        int is = indexOfWord(body, "is");
        if (is < 0 || !body.startsWith("$")) {
            return null;
        }
        String subject = body.substring(1, is).trim();
        int question = topLevelIndexOf(body, '?', is);
        if (question < 0) {
            return null;
        }
        int colon = topLevelIndexOf(body, ':', question + 1);
        if (colon < 0) {
            return null;
        }
        String condition = body.substring(is + 2, question).trim();
        String whenTrue = body.substring(question + 1, colon).trim();
        String whenFalse = body.substring(colon + 1).trim();
        if (subject.isEmpty() || condition.isEmpty() || whenTrue.isEmpty() || whenFalse.isEmpty()) {
            return null;
        }
        return new ConditionalReturnType(subject, condition, whenTrue, whenFalse);
    }

    /**
     * 実引数がどちらの分岐に当たるかを決める。
     *
     * <p>決められるのは、条件と実引数がどちらもクラス定数 (enum ケース) で書かれている場合だけ。
     * 値の形による条件 (`$input is list<V>`) は実引数の型からは決められないので何も返さない。
     *
     * @param argumentText 呼び出しに書かれた実引数のテキスト
     * @return 選ばれた分岐。決められないときは null
     */
    @Nullable String select(String argumentText) {
        if (argumentText == null || argumentText.isBlank()) {
            return null;
        }
        String actual = normalize(argumentText);
        String expected = normalize(condition);
        if (!isClassConstant(actual) || !isClassConstant(expected)) {
            return null;
        }
        if (actual.equals(expected)) {
            return whenTrue;
        }
        // 同じ enum の別ケースなら、条件は成り立たない
        return sameOwner(actual, expected) ? whenFalse : null;
    }

    private static boolean isClassConstant(String text) {
        int separator = text.indexOf("::");
        return separator > 0 && separator + 2 < text.length();
    }

    private static boolean sameOwner(String left, String right) {
        return shortOwner(left).equals(shortOwner(right));
    }

    private static String shortOwner(String classConstant) {
        String owner = classConstant.substring(0, classConstant.indexOf("::"));
        int separator = owner.lastIndexOf('\\');
        return separator < 0 ? owner : owner.substring(separator + 1);
    }

    private static String normalize(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("\\") ? trimmed.substring(1) : trimmed;
    }

    /**
     * 語として現れる `is` の位置。型名の一部 (`island`) を拾わないよう前後を見る。
     */
    private static int indexOfWord(String text, String word) {
        for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
            boolean leftFree = at > 0 && Character.isWhitespace(text.charAt(at - 1));
            int right = at + word.length();
            boolean rightFree = right < text.length() && Character.isWhitespace(text.charAt(right));
            if (leftFree && rightFree) {
                return at;
            }
        }
        return -1;
    }

    /**
     * 総称型やネストした条件型の中の記号を拾わないよう、深さ 0 の位置だけを見る。
     */
    private static int topLevelIndexOf(String text, char target, int from) {
        int depth = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<' || c == '{' || c == '(') {
                depth++;
            } else if (c == '>' || c == '}' || c == ')') {
                depth--;
            } else if (c == target && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    static List<String> branchesOf(ConditionalReturnType conditional) {
        return List.of(conditional.whenTrue(), conditional.whenFalse());
    }
}
