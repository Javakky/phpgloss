package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * `@phpstan-return ($mode is Shape::AS_LIST ? list<V> : array<K, V>)` の形を読む。
 *
 * <p>PhpStorm はこの構文をパースはするが評価しない (YouTrack WI-66465 は "Support *parsing* of ..."
 * であり、WI-85338 / WI-77577 が未解決)。実測でも実引数を変えても結果が変わらず、
 * 両分岐の union が返る。
 */
record ConditionalReturnType(String subject, String condition, String whenTrue, String whenFalse) {

    /** 要素が並ぶだけの型。キーの型を持たない。 */
    private static final Set<String> LIST_TYPES = Set.of("list", "non-empty-list");
    /** キーと値の 2 つの型引数を持てる型。 */
    private static final Set<String> KEYED_TYPES = Set.of("array", "non-empty-array", "iterable");
    /** リテラルで判定できる型キーワード。 */
    private static final Set<String> LITERAL_TYPES = Set.of(
            "int", "positive-int", "negative-int", "non-negative-int", "non-positive-int", "string");
    /** キーがこの型なら、その配列は list ではない。 */
    private static final Set<String> STRING_TYPES = Set.of(
            "string", "non-empty-string", "class-string", "numeric-string", "literal-string",
            "lowercase-string", "non-falsy-string");

    /**
     * 条件型を畳むのに要る、呼び出し側の情報。
     */
    interface CallSite {
        /**
         * @param parameterName 条件が見ている仮引数の名前 (`$` を除く)
         * @return 呼び出しに書かれた実引数。書かれておらず既定値も無ければ null
         */
        @Nullable ConditionalArgument argumentOf(String parameterName);

        /**
         * 条件側に書かれたクラス定数を、宣言が書かれたファイルの namespace / use で完全修飾する。
         *
         * @return `\Probe\Shape::AS_LIST` の形。解決できないときは null
         */
        @Nullable String qualifyDeclared(String classConstant);
    }

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
     * <p>enum ケース、リテラル、実引数の型の順に見る。どれでも決められないときは何も返さない。
     * 誤った分岐を出すより黙る方がよい。
     *
     * @return 選ばれた分岐。決められないときは null
     */
    @Nullable String select(@Nullable ConditionalArgument argument, CallSite callSite) {
        if (argument == null || argument.text() == null || argument.text().isBlank()) {
            return null;
        }
        String actual = argument.text().trim();
        String expected = condition.trim();
        if (isClassConstant(actual) && isClassConstant(expected)) {
            return selectByEnumCase(actual, callSite.qualifyDeclared(expected));
        }
        String byLiteral = selectByLiteral(actual, expected);
        return byLiteral != null ? byLiteral : selectByActualType(expected, argument.type().get());
    }

    /**
     * enum ケースどうしの比較。
     *
     * <p>短い名前のままでは別 namespace の同名 enum と見分けが付かないため、両方が完全修飾
     * できたときだけ判定する。
     */
    private @Nullable String selectByEnumCase(String actual, @Nullable String expected) {
        if (expected == null || !isQualified(actual) || !isQualified(expected)) {
            return null;
        }
        if (actual.equals(expected)) {
            return whenTrue;
        }
        // 同じ enum の別ケースなら、条件は成り立たない
        return ownerOf(actual).equals(ownerOf(expected)) ? whenFalse : null;
    }

    /**
     * 書かれた実引数そのもので決める。配列リテラルなら list かどうか、数値・文字列リテラルなら
     * その型キーワードに当たるかを見る。
     */
    private @Nullable String selectByLiteral(String argumentText, String conditionType) {
        String base = baseOf(conditionType);
        if (LIST_TYPES.contains(base)) {
            return selectByArrayLiteral(argumentText, base);
        }
        return LITERAL_TYPES.contains(base) ? selectByScalarLiteral(argumentText, base) : null;
    }

    /**
     * 値の形による条件 (`$input is list<V>`) を、書かれた配列リテラルから決める。
     *
     * <p>`[1, 2]` のようにキーを書いていなければ list、`['a' => 1]` のようにキーがあれば list ではない。
     */
    private @Nullable String selectByArrayLiteral(String argumentText, String conditionBase) {
        String literal = argumentText.trim();
        if (!literal.startsWith("[") || !literal.endsWith("]")) {
            return null;
        }
        String elements = literal.substring(1, literal.length() - 1);
        if (conditionBase.equals("non-empty-list") && elements.isBlank()) {
            return whenFalse;
        }
        boolean hasKeys = topLevelIndexOf(elements, '=', 0) >= 0 && literal.contains("=>");
        return hasKeys ? whenFalse : whenTrue;
    }

    /**
     * 型による条件 (`$size is positive-int`) を、書かれた数値・文字列リテラルから決める。
     */
    private @Nullable String selectByScalarLiteral(String argumentText, String conditionType) {
        if (isStringLiteral(argumentText)) {
            return conditionType.equals("string") ? whenTrue : whenFalse;
        }
        Long value = integerValueOf(argumentText);
        if (value == null) {
            return null;
        }
        return matchesIntegerType(conditionType, value) ? whenTrue : whenFalse;
    }

    private static boolean matchesIntegerType(String conditionType, long value) {
        return switch (conditionType) {
            case "int" -> true;
            case "positive-int" -> value > 0;
            case "negative-int" -> value < 0;
            case "non-negative-int" -> value >= 0;
            case "non-positive-int" -> value <= 0;
            default -> false;
        };
    }

    /**
     * 書かれた実引数で決まらないときに、実引数の型で決める。
     *
     * <p>`list<...>` なら list、キーが文字列の `array<...>` なら list ではない。それ以外の型は、
     * list かどうかを決めない (`array<int, ...>` は list とは限らない)。
     */
    private @Nullable String selectByActualType(String conditionType, @Nullable PhpType actual) {
        if (!LIST_TYPES.contains(baseOf(conditionType)) || actual == null || actual.isEmpty()) {
            return null;
        }
        Set<String> branches = new LinkedHashSet<>();
        for (String typeString : actual.getTypesWithParametrisedParts()) {
            String branch = branchOf(typeString);
            if (branch != null) {
                branches.add(branch);
            }
        }
        // union の中で食い違うなら、どちらとも決められない
        return branches.size() == 1 ? branches.iterator().next() : null;
    }

    private @Nullable String branchOf(String typeString) {
        // 解決済みの型は `\array<...>` のように完全修飾されている
        String base = unqualify(baseOf(typeString));
        if (LIST_TYPES.contains(base)) {
            return whenTrue;
        }
        if (!KEYED_TYPES.contains(base)) {
            return null;
        }
        List<String> arguments = PhpType.getParametrizedParts(typeString);
        return arguments.size() == 2 && STRING_TYPES.contains(unqualify(arguments.getFirst()))
                ? whenFalse
                : null;
    }

    private static String unqualify(String type) {
        String trimmed = type.trim();
        return trimmed.startsWith("\\") ? trimmed.substring(1) : trimmed;
    }

    /**
     * `non-empty-list<V>` の `non-empty-list`。型引数を持たない型はそのまま。
     */
    private static String baseOf(String type) {
        int angle = type.indexOf('<');
        return (angle < 0 ? type : type.substring(0, angle)).trim();
    }

    private static boolean isStringLiteral(String text) {
        return text.length() >= 2
                && (text.charAt(0) == '\'' || text.charAt(0) == '"')
                && text.charAt(text.length() - 1) == text.charAt(0);
    }

    private static @Nullable Long integerValueOf(String text) {
        try {
            return Long.valueOf(text.startsWith("+") ? text.substring(1) : text);
        } catch (NumberFormatException notAnInteger) {
            return null;
        }
    }

    private static boolean isClassConstant(String text) {
        int separator = text.indexOf("::");
        return separator > 0 && separator + 2 < text.length();
    }

    private static boolean isQualified(String classConstant) {
        return classConstant.startsWith("\\");
    }

    private static String ownerOf(String classConstant) {
        return classConstant.substring(0, classConstant.indexOf("::"));
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

    private static final int MAX_DEPTH = 8;

    /**
     * 入れ子になった条件型を、決められなくなるまで畳む。
     *
     * <p>実際のコードは 3 段まで重なる (the sample project の `filter` がそう)。1 段しか読まないと、
     * else 側に残った `($mode is ... )` という文字列をそのまま型として扱ってしまう。
     *
     * @return 条件型でなくなった型。途中で決められなくなったら null
     */
    static @Nullable String resolve(String declared, CallSite callSite) {
        String current = declared;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            ConditionalReturnType conditional = parse(current);
            if (conditional == null) {
                return looksConditional(current) ? null : current;
            }
            String selected = conditional.select(callSite.argumentOf(conditional.subject()), callSite);
            if (selected == null) {
                return null;
            }
            current = selected;
        }
        return null;
    }

    /**
     * 条件型として読めなかったのに条件型に見えるもの。型として出すと意味不明な型になる。
     */
    private static boolean looksConditional(String type) {
        return type.trim().startsWith("(") && indexOfWord(type, "is") >= 0;
    }
}
