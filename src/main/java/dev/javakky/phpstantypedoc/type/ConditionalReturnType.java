package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * `@phpstan-return ($mode is Shape::AS_LIST ? list<V> : array<K, V>)` の形を読む。
 *
 * <p>PhpStorm はこの構文をパースはするが評価しない (YouTrack WI-66465 は "Support *parsing* of ..."
 * であり、WI-85338 / WI-77577 が未解決)。実測でも実引数を変えても結果が変わらず、
 * 両分岐の union が返る。
 *
 * @param subject         条件が見ている仮引数名 (`$` を除く) または型変数名
 * @param templateSubject 主語が仮引数でなく型変数 (`R is null`) か
 * @param negated         `is not` で書かれたか。判定の真偽をそのまま反転する
 */
record ConditionalReturnType(
        String subject,
        boolean templateSubject,
        boolean negated,
        String condition,
        String whenTrue,
        String whenFalse
) {

    /** 要素が並ぶだけの型。キーの型を持たない。 */
    private static final Set<String> LIST_TYPES = Set.of("list", "non-empty-list");
    /** キーと値の 2 つの型引数を持てる型。 */
    private static final Set<String> KEYED_TYPES = Set.of("array", "non-empty-array", "iterable");
    /** リテラルで判定できる型キーワード。 */
    private static final Set<String> LITERAL_TYPES = Set.of(
            "int", "positive-int", "negative-int", "non-negative-int", "non-positive-int", "string",
            "true", "false", "bool");
    /** キーがこの型なら、その配列は list ではない。 */
    private static final Set<String> STRING_TYPES = Set.of(
            "string", "non-empty-string", "class-string", "numeric-string", "literal-string",
            "lowercase-string", "non-falsy-string");
    /** null そのものを表す綴り。条件にも実引数のテキストにも現れる。 */
    private static final Set<String> NULL_TYPES = Set.of("null");
    /** null を取り得るかが分からない型。混ざっていたら null 判定は決めない。 */
    private static final Set<String> UNDECIDED_TYPES = Set.of("mixed", "?", "");

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
         * `(R is null ? ... : ...)` のように、条件が仮引数でなく型変数を見ている場合の主語。
         *
         * @param templateName 型変数の名前
         * @return 決まっている束縛。まだ決まっていなければ null
         */
        @Nullable ConditionalArgument templateOf(String templateName);

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
        if (is < 0) {
            return null;
        }
        boolean templateSubject = !body.startsWith("$");
        String subject = body.substring(templateSubject ? 0 : 1, is).trim();
        if (templateSubject && !isIdentifier(subject)) {
            // 型変数の主語は名前ひとつだけ。それ以外の形は条件型として読まない
            return null;
        }
        int afterNot = wordEndOf(body, is + "is".length(), "not");
        boolean negated = afterNot > 0;
        int conditionStart = negated ? afterNot : is + "is".length();
        int question = topLevelIndexOf(body, '?', conditionStart);
        if (question < 0) {
            return null;
        }
        int colon = topLevelIndexOf(body, ':', question + 1);
        if (colon < 0) {
            return null;
        }
        String condition = body.substring(conditionStart, question).trim();
        String whenTrue = body.substring(question + 1, colon).trim();
        String whenFalse = body.substring(colon + 1).trim();
        if (subject.isEmpty() || condition.isEmpty() || whenTrue.isEmpty() || whenFalse.isEmpty()) {
            return null;
        }
        return new ConditionalReturnType(
                subject, templateSubject, negated, condition, whenTrue, whenFalse);
    }

    /**
     * `is not` の `not`。型名の一部 (`notFalsy`) を否定と読まないよう、後ろが空白であることまで見る。
     *
     * @return `not` の直後の位置。書かれていなければ -1
     */
    private static int wordEndOf(String body, int from, String word) {
        int at = from;
        while (at < body.length() && Character.isWhitespace(body.charAt(at))) {
            at++;
        }
        int end = at + word.length();
        boolean written = body.startsWith(word, at)
                && end < body.length()
                && Character.isWhitespace(body.charAt(end));
        return written ? end : -1;
    }

    private static boolean isIdentifier(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * 実引数がどちらの分岐に当たるかを決める。
     *
     * @return 選ばれた分岐。決められないときは null
     */
    @Nullable String select(@Nullable ConditionalArgument argument, CallSite callSite) {
        Boolean holds = holds(argument, callSite);
        if (holds == null) {
            return null;
        }
        // `is not` は判定の真偽をそのまま反転する。決められない場合は反転しても決められない
        return holds != negated ? whenTrue : whenFalse;
    }

    /**
     * 条件が成り立つか。
     *
     * <p>null 判定、enum ケース、リテラル、実引数の型の順に見る。どれでも決められないときは
     * 何も返さない。誤った分岐を出すより黙る方がよい。
     *
     * @return 成り立つなら true、成り立たないなら false、決められないときは null
     */
    private @Nullable Boolean holds(@Nullable ConditionalArgument argument, CallSite callSite) {
        if (argument == null || argument.text() == null || argument.text().isBlank()) {
            return null;
        }
        String actual = argument.text().trim();
        String expected = condition.trim();
        if (NULL_TYPES.contains(unqualify(expected).toLowerCase(Locale.ROOT))) {
            return holdsNull(argument);
        }
        if (isClassConstant(actual) && isClassConstant(expected)) {
            return holdsEnumCase(actual, callSite.qualifyDeclared(expected));
        }
        Boolean byLiteral = holdsByLiteral(actual, expected);
        return byLiteral != null ? byLiteral : holdsByActualType(expected, argument.type().get());
    }

    /**
     * `R is null` の判定。型キーワードの照合では決まらないので独立して見る。
     *
     * <p>型が null だけなら成り立ち、null を取り得ない型なら成り立たない。`int|null` のように
     * どちらにもなり得る型では決めない。
     */
    private static @Nullable Boolean holdsNull(ConditionalArgument argument) {
        if (NULL_TYPES.contains(unqualify(argument.text().trim()).toLowerCase(Locale.ROOT))) {
            return true;
        }
        Set<String> members = new LinkedHashSet<>();
        for (String member : argument.type().get().getTypes()) {
            members.add(unqualify(member).toLowerCase(Locale.ROOT));
        }
        if (members.isEmpty()) {
            return null;
        }
        if (members.stream().allMatch(NULL_TYPES::contains)) {
            return true;
        }
        boolean undecided = members.stream()
                .anyMatch(member -> NULL_TYPES.contains(member) || UNDECIDED_TYPES.contains(member));
        return undecided ? null : Boolean.FALSE;
    }

    /**
     * enum ケースどうしの比較。
     *
     * <p>短い名前のままでは別 namespace の同名 enum と見分けが付かないため、両方が完全修飾
     * できたときだけ判定する。
     */
    private static @Nullable Boolean holdsEnumCase(String actual, @Nullable String expected) {
        if (expected == null || !isQualified(actual) || !isQualified(expected)) {
            return null;
        }
        if (actual.equals(expected)) {
            return true;
        }
        // 同じ enum の別ケースなら、条件は成り立たない
        return ownerOf(actual).equals(ownerOf(expected)) ? Boolean.FALSE : null;
    }

    /**
     * 書かれた実引数そのもので決める。配列リテラルなら list かどうか、数値・文字列リテラルなら
     * その型キーワードに当たるかを見る。
     */
    private static @Nullable Boolean holdsByLiteral(String argumentText, String conditionType) {
        String base = baseOf(conditionType);
        if (LIST_TYPES.contains(base)) {
            return holdsByArrayLiteral(argumentText, base);
        }
        return LITERAL_TYPES.contains(base) ? holdsByScalarLiteral(argumentText, base) : null;
    }

    /**
     * 値の形による条件 (`$input is list<V>`) を、書かれた配列リテラルから決める。
     *
     * <p>`[1, 2]` のようにキーを書いていなければ list、`['a' => 1]` のようにキーがあれば list ではない。
     */
    private static @Nullable Boolean holdsByArrayLiteral(String argumentText, String conditionBase) {
        String literal = argumentText.trim();
        if (!literal.startsWith("[") || !literal.endsWith("]")) {
            return null;
        }
        String elements = literal.substring(1, literal.length() - 1);
        if (conditionBase.equals("non-empty-list") && elements.isBlank()) {
            return false;
        }
        boolean hasKeys = topLevelIndexOf(elements, '=', 0) >= 0 && literal.contains("=>");
        return !hasKeys;
    }

    /**
     * 型による条件 (`$size is positive-int`) を、書かれた数値・文字列リテラルから決める。
     */
    private static @Nullable Boolean holdsByScalarLiteral(String argumentText, String conditionType) {
        if (isStringLiteral(argumentText)) {
            return conditionType.equals("string");
        }
        // `$strict is true` のように、真偽そのものを条件に書ける
        String trimmed = argumentText.trim();
        if (trimmed.equals("true") || trimmed.equals("false")) {
            return switch (conditionType) {
                case "true" -> trimmed.equals("true");
                case "false" -> trimmed.equals("false");
                case "bool" -> true;
                default -> false;
            };
        }
        Long value = integerValueOf(argumentText);
        return value == null ? null : matchesIntegerType(conditionType, value);
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
    private static @Nullable Boolean holdsByActualType(String conditionType, @Nullable PhpType actual) {
        if (!LIST_TYPES.contains(baseOf(conditionType)) || actual == null || actual.isEmpty()) {
            return null;
        }
        Set<Boolean> decisions = new LinkedHashSet<>();
        for (String typeString : actual.getTypesWithParametrisedParts()) {
            Boolean decision = isListType(typeString);
            if (decision != null) {
                decisions.add(decision);
            }
        }
        // union の中で食い違うなら、どちらとも決められない
        return decisions.size() == 1 ? decisions.iterator().next() : null;
    }

    private static @Nullable Boolean isListType(String typeString) {
        // 解決済みの型は `\array<...>` のように完全修飾されている
        String base = unqualify(baseOf(typeString));
        if (LIST_TYPES.contains(base)) {
            return true;
        }
        if (!KEYED_TYPES.contains(base)) {
            return null;
        }
        List<String> arguments = PhpType.getParametrizedParts(typeString);
        return arguments.size() == 2 && STRING_TYPES.contains(unqualify(arguments.getFirst()))
                ? Boolean.FALSE
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
     * <p>実際のコードは 3 段まで重なる 。1 段しか読まないと、
     * else 側に残った `($mode is ... )` という文字列をそのまま型として扱ってしまう。
     *
     * @return 条件型でなくなった型。途中で決められなくなったら null
     */
    static @Nullable String resolve(String declared, CallSite callSite) {
        String current = declared;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            ConditionalReturnType conditional = parse(current);
            if (conditional == null) {
                String plain = withoutRedundantParentheses(current);
                return looksConditional(plain) ? null : plain;
            }
            String selected = conditional.select(conditional.subjectOf(callSite), callSite);
            if (selected == null) {
                return null;
            }
            current = selected;
        }
        return null;
    }

    /**
     * 条件の主語を呼び出し側から引く。仮引数なら実引数、型変数なら決まっている束縛。
     */
    private @Nullable ConditionalArgument subjectOf(CallSite callSite) {
        return templateSubject ? callSite.templateOf(subject) : callSite.argumentOf(subject);
    }

    /**
     * 分岐に書かれた `(?V)` の括弧を落とす。型として出すと存在しないクラス名になる。
     */
    private static String withoutRedundantParentheses(String type) {
        String trimmed = type.trim();
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
            return trimmed;
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        // 先頭と末尾が対応していない形 (`(A|B)|(C)`) から剥がすと型が壊れる
        return isBalanced(body) ? withoutRedundantParentheses(body) : trimmed;
    }

    private static boolean isBalanced(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    /**
     * 条件型として読めなかったのに条件型に見えるもの。型として出すと意味不明な型になる。
     */
    static boolean looksConditional(String type) {
        return type.trim().startsWith("(") && indexOfWord(type, "is") >= 0;
    }
}
