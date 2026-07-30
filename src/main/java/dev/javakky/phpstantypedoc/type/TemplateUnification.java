package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宣言された型と実引数の型を突き合わせて、型変数の中身を決める。
 *
 * <p>`@param non-empty-list<V> $input` に `\App\User[]` が渡されたなら `V = \App\User` になる。
 * PhpStorm は戻り値がそのまま型変数の場合しか解かず、`Op<V>` のように型引数の中に埋まった
 * 型変数は未解決のまま残す (実測: `\App\Op<#ᢐV.>`)。その欠けている段を埋める。
 *
 * <p>置換そのものは PhpStorm 内部の実装に任せる ({@code substituteTypesWithMapping})。
 * ここで作るのは対応表だけ。
 */
final class TemplateUnification {
    private TemplateUnification() {
    }

    /**
     * @param declared  PHPDoc に書かれた型 (`non-empty-list<V>`)
     * @param actual    実引数の型
     * @param templates 解きたい型変数の名前
     * @param bindings  結果の書き込み先。既に決まっている型変数は上書きしない
     */
    static void unify(
            String declared,
            PhpType actual,
            Collection<String> templates,
            Map<String, String> bindings
    ) {
        if (declared == null || declared.isBlank() || actual.isEmpty()) {
            return;
        }
        String trimmed = declared.trim();
        if (templates.contains(trimmed)) {
            bindings.putIfAbsent(trimmed, join(actual));
            return;
        }
        String element = elementOf(trimmed);
        if (element == null) {
            return;
        }
        PhpType actualElements = elementsOf(actual);
        if (!actualElements.isEmpty()) {
            unify(element, actualElements, templates, bindings);
        }
    }

    static Map<String, String> empty() {
        return new LinkedHashMap<>();
    }

    /**
     * `non-empty-list<V>` の `V`、`array<K, V>` の `V` を返す。総称でなければ null。
     * キーと値がある場合は値側を採る。
     */
    private static String elementOf(String declared) {
        int angle = declared.indexOf('<');
        if (angle < 0 || !declared.endsWith(">")) {
            return null;
        }
        String arguments = declared.substring(angle + 1, declared.length() - 1);
        int depth = 0;
        int start = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '<' || c == '{') {
                depth++;
            } else if (c == '>' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                start = i + 1;
            }
        }
        return arguments.substring(start).trim();
    }

    /**
     * 実引数の型から要素型を取り出す。`\App\User[]` なら `\App\User`、
     * `list<\App\User>` のように型引数を持つならその最後の引数。
     */
    private static PhpType elementsOf(PhpType actual) {
        PhpType result = new PhpType();
        for (String typeString : actual.getTypesWithParametrisedParts()) {
            List<String> parts = PhpType.getParametrizedParts(typeString);
            if (!parts.isEmpty()) {
                result.add(parts.getLast());
                continue;
            }
            if (typeString.endsWith("[]")) {
                result.add(typeString.substring(0, typeString.length() - 2));
            }
        }
        return result;
    }

    private static String join(PhpType type) {
        return String.join("|", type.getTypes());
    }
}
