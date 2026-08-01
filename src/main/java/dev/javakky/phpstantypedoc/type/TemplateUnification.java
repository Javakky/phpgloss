package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        List<String> alternatives = PhpTypeText.splitTopLevelUnion(trimmed);
        if (alternatives.size() > 1) {
            // `non-empty-list<V>|array<K, V>` のような union。どの枝からでも型変数は決まりうる
            alternatives.forEach(alternative -> unify(alternative, actual, templates, bindings));
            return;
        }
        List<String> members = TypeUnionSimplifier.intersectionMembersOf(trimmed);
        if (members.size() > 1) {
            unifyIntersection(members, actual, templates, bindings);
            return;
        }
        if (templates.contains(trimmed)) {
            bindings.putIfAbsent(trimmed, join(actual));
            return;
        }
        String element = elementOf(trimmed);
        if (element == null) {
            return;
        }
        // `array<K, V>` のキー側。実引数の型がキーの型を持っていればそれを使う。
        // 取れないときは埋めない (`@template K of array-key` の上界で埋まる)
        String key = keyOf(trimmed);
        String actualKey = key != null && templates.contains(key) ? keyTypeOf(actual) : null;
        if (actualKey != null) {
            bindings.putIfAbsent(key, actualKey);
        }
        PhpType actualElements = elementsOf(actual);
        if (!actualElements.isEmpty()) {
            unify(element, actualElements, templates, bindings);
        }
    }

    /**
     * `T&Countable` のような交差型を突き合わせる。
     *
     * <p>型変数でない成分 (`Countable`) は照合条件として使う。実引数の型が同じ成分を持っていれば
     * 差し引き、残りを型変数に束縛する。名前だけでは実装関係を判定できないため、成分が見つから
     * なくても束縛は止めない (`\App\Bag` は `Countable` を実装していても型名からは分からない)。
     *
     * <p>型変数が 2 つ以上ある交差 (`T&U`) では、どちらに何を束縛するか決められないので何もしない。
     */
    private static void unifyIntersection(
            List<String> members,
            PhpType actual,
            Collection<String> templates,
            Map<String, String> bindings
    ) {
        List<String> variables = members.stream().filter(templates::contains).toList();
        List<String> constraints = members.stream().filter(member -> !templates.contains(member)).toList();
        // 照合条件の中に埋まった型変数 (`Countable&iterable<V>` の V) はそのまま突き合わせる
        constraints.forEach(constraint -> unify(constraint, actual, templates, bindings));
        if (variables.size() != 1) {
            return;
        }
        PhpType remainder = withoutConstraints(actual, constraints);
        if (!remainder.isEmpty()) {
            bindings.putIfAbsent(variables.getFirst(), join(remainder));
        }
    }

    /**
     * 実引数の型から、照合条件と一致する成分を差し引く。実引数自身が交差型
     * (`\App\Bag&\Countable`) のとき、条件に書かれた側を残すと型変数が二重になる。
     */
    private static PhpType withoutConstraints(PhpType actual, Collection<String> constraints) {
        Set<String> excluded = new LinkedHashSet<>();
        constraints.forEach(constraint -> excluded.add(unqualified(constraint)));
        PhpType remainder = new PhpType();
        for (String typeString : actual.getTypesWithParametrisedParts()) {
            String kept = TypeUnionSimplifier.intersectionMembersOf(typeString).stream()
                    .filter(member -> !excluded.contains(unqualified(member)))
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("");
            if (!kept.isEmpty()) {
                remainder.add(kept);
            }
        }
        return remainder;
    }

    /**
     * 比較のために先頭の `\` を落とす。PHPDoc には短い名前で書かれ、解決済みの型は修飾されている。
     */
    private static String unqualified(String type) {
        String trimmed = type.trim();
        return trimmed.startsWith("\\") ? trimmed.substring(1) : trimmed;
    }

    /**
     * 実引数の型からキーの型を取り出す。`['a' => 1]` の global 型 `array<\string, \int>` の先頭。
     *
     * <p>候補が割れる union では決めない。誤ったキー型を出すより、上界で埋めた方がまだ正しい。
     *
     * @return キーの型。取れないときは null
     */
    private static String keyTypeOf(PhpType actual) {
        Set<String> keys = new LinkedHashSet<>();
        for (String typeString : actual.getTypesWithParametrisedParts()) {
            List<String> arguments = PhpType.getParametrizedParts(typeString);
            if (arguments.size() == 2) {
                keys.add(arguments.getFirst());
            }
        }
        return keys.size() == 1 ? keys.iterator().next() : null;
    }

    /**
     * `array<K, V>` の `K`。型引数が 1 つしかない総称型には無い。
     */
    private static String keyOf(String declared) {
        int angle = declared.indexOf('<');
        if (angle < 0 || !declared.endsWith(">")) {
            return null;
        }
        String arguments = declared.substring(angle + 1, declared.length() - 1);
        int depth = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '<' || c == '{') {
                depth++;
            } else if (c == '>' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return arguments.substring(0, i).trim();
            }
        }
        return null;
    }

    /**
     * 書かれた型どうしを構造で照合する。`list<V>` × `list<positive-int>` → `V = positive-int`。
     *
     * <p>PhpStorm の型を経由すると `positive-int` のような精密化が `int` に潰れるため、
     * `@var` に書かれた型はテキストのまま照合する。基底名が一致する総称型だけを対応させ、
     * 判断できない形では何も束縛しない。
     */
    static void unifyText(
            String declared,
            String written,
            Collection<String> templates,
            Map<String, String> bindings
    ) {
        if (declared == null || written == null) {
            return;
        }
        String declaredTrimmed = declared.trim();
        String writtenTrimmed = written.trim();
        List<String> alternatives = PhpTypeText.splitTopLevelUnion(declaredTrimmed);
        if (alternatives.size() > 1) {
            alternatives.forEach(alternative -> unifyText(alternative, writtenTrimmed, templates, bindings));
            return;
        }
        if (templates.contains(declaredTrimmed)) {
            bindings.putIfAbsent(declaredTrimmed, writtenTrimmed);
            return;
        }
        TypeUnionSimplifier.ArrayShape declaredShape = TypeUnionSimplifier.arrayShapeOf(declaredTrimmed);
        TypeUnionSimplifier.ArrayShape writtenShape = TypeUnionSimplifier.arrayShapeOf(writtenTrimmed);
        if (declaredShape == null || writtenShape == null
                || !declaredShape.base().equals(writtenShape.base())
                || declaredShape.arguments().size() != writtenShape.arguments().size()) {
            return;
        }
        for (int i = 0; i < declaredShape.arguments().size(); i++) {
            unifyText(declaredShape.arguments().get(i), writtenShape.arguments().get(i), templates, bindings);
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
