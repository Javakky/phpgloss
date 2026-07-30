package dev.javakky.phpstantypedoc.type;

import java.util.Map;

/**
 * PHPDoc に書かれた型の中の型変数名を、決まった実引数に差し替える。
 *
 * <p>PhpStorm 内部の {@code substituteTypesWithMapping} は、型変数が内部エンコード形
 * (`#ᢐV.`) になっているものにしか効かない (実測)。PHPDoc から読んだ生の `V` はそのまま残るため、
 * 文字列の段階で差し替える。
 *
 * <p>`non-empty-list<V>` は `non-empty-list<\App\User>` になる。要素型だけを取り出して
 * `\App\User[]` に落とすようなことはしない。list であることや空でないことが失われるため。
 */
final class TemplateSubstitution {
    private TemplateSubstitution() {
    }

    /**
     * @return 型変数を差し替えた型。差し替えるものが無ければ元のまま
     */
    static String apply(String declared, Map<String, String> bindings) {
        if (declared == null || declared.isBlank() || bindings.isEmpty()) {
            return declared;
        }
        StringBuilder result = new StringBuilder(declared.length());
        int cursor = 0;
        while (cursor < declared.length()) {
            char c = declared.charAt(cursor);
            if (!isIdentifierPart(c)) {
                result.append(c);
                cursor++;
                continue;
            }
            int end = cursor;
            while (end < declared.length() && isIdentifierPart(declared.charAt(end))) {
                end++;
            }
            String word = declared.substring(cursor, end);
            result.append(bindings.getOrDefault(word, word));
            cursor = end;
        }
        return result.toString();
    }

    /**
     * 差し替えられていない型変数が残っているか。残ったまま型として出すと、
     * `non-empty-list<V>` のような読み手に意味のない型になる。
     */
    static boolean hasUnresolved(String type, Iterable<String> templates) {
        for (String template : templates) {
            int at = type.indexOf(template);
            while (at >= 0) {
                boolean leftFree = at == 0 || !isIdentifierPart(type.charAt(at - 1));
                int right = at + template.length();
                boolean rightFree = right >= type.length() || !isIdentifierPart(type.charAt(right));
                if (leftFree && rightFree) {
                    return true;
                }
                at = type.indexOf(template, at + 1);
            }
        }
        return false;
    }

    /**
     * 総称の集合型に、要素アクセスが解決できる形を足す。
     *
     * <p>`list<User>` は PhpStorm の要素アクセスが読めず、`$xs[0]` が一段前の型になる (実測)。
     * `User[]` を**足す**ことで要素型が取れるようにする。`list<User>` 自体は残すので、
     * list であることや空でないことは失われない。
     *
     * <p>総称形と同じ型になる複数形 (`array<User>` に対する `User[]`) は足さない。足しても要素型は
     * 増えず、表示に同じ型が二重に並ぶだけになる。
     *
     * @return 足すべき型。足すものが無ければ null
     */
    static String pluralFormOf(String type) {
        TypeUnionSimplifier.ArrayShape shape = TypeUnionSimplifier.arrayShapeOf(type);
        if (shape == null || shape.isPlural()) {
            return null;
        }
        String plural = shape.element() + "[]";
        return shape.element().contains("<") || TypeUnionSimplifier.sameType(plural, type) ? null : plural;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '\\';
    }
}
