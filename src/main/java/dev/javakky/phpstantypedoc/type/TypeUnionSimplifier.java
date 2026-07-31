package dev.javakky.phpstantypedoc.type;

import dev.javakky.phpstantypedoc.keyword.PhpStanTypeKeywords;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * union に並んだ型から、同じ union の中のより精密な型に呑まれているものを落とす。
 *
 * <p>変数の型は複数のシグネチャ成分の union になる。こちらが `list<int>` まで解いても、他の成分を
 * 解読した `array` や `mixed` が同じ union に残り `array|int[]|list<int>` と表示される。他の成分は
 * プロバイダ側から消せない (抑止できるのは自分のキーの解読だけ) ので、表示の直前にここで整理する。
 *
 * <p>落とすのは包摂が言える組だけにする。`int|string` のように比べられない組は、どちらが読み手の
 * 求める型か決められないため触らない。
 *
 * <p>もう一つ落とせない組がある。union には「同じ値の重複記述」(各プロバイダの近似が並ぶ) と
 * 「本物の選択肢」(条件が解決できず両分岐が並ぶ) が区別なく混ざり、出所は表示側から見えない。
 * 精密な側を残してよいのは前者だけで、後者で狭い側を残すと嘘になる。`array<int, X>` と `list<X>`
 * のような**どちらも具体的な組**は選択肢として実在する (未解決の条件型の両分岐) ため触らない。
 * 落とすのは `?` / `mixed` / 素の `array` / 要素型だけの複数形のような、単独では選択肢として
 * 書かれることのない抽象側に限る。
 */
public final class TypeUnionSimplifier {
    private static final String UNKNOWN = "?";
    private static final String MIXED = "mixed";
    private static final String ANY_ARRAY = "array";
    private static final String ITERABLE = "iterable";
    /** 複数形 (`X[]`) の基底名。総称形と同じ枠で扱うための印。 */
    private static final String PLURAL = "[]";

    private static final Set<String> GENERIC_COLLECTIONS =
            Set.of("list", "non-empty-list", ANY_ARRAY, "non-empty-array", ITERABLE);

    private TypeUnionSimplifier() {
    }

    /**
     * @param types union に並んだ型 ({@code PhpType#getTypesWithParametrisedParts} の中身)
     * @return 整理した型。1 つも減らないときは empty (呼び出し側は介入せず、標準の表示に任せる)
     */
    public static Optional<List<String>> simplify(Collection<String> types) {
        Set<String> keys = new LinkedHashSet<>();
        for (String type : types) {
            String key = keyOf(type);
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        List<String> union = List.copyOf(keys);
        List<String> kept = union.stream().filter(type -> !isSubsumed(type, union)).toList();
        return kept.isEmpty() || kept.size() >= types.size() ? Optional.empty() : Optional.of(kept);
    }

    /**
     * 確定した型を、union に並ぶより精密な共記述で置き換える。
     *
     * <p>この解決器の束縛は索引時シグネチャ経由のため、PhpStorm 自身の定数追跡
     * (`int<20,20>` 等) より粗いことがある。分岐が確定した状況では union の全成分が同じ値の
     * 記述なので、**同じ構造**で要素だけが精密な成分があればそちらを見せる。構造一致に限る
     * ことで、選択肢どうし (`list<X>` と `array<int, X>`) を混ぜる誤りは起きない。
     */
    public static List<String> refineAgainst(List<String> resolved, Collection<String> union) {
        List<String> unionKeys = union.stream().map(TypeUnionSimplifier::keyOf).toList();
        return resolved.stream().map(part -> {
            String best = part;
            for (String candidate : unionKeys) {
                if (!candidate.equals(best) && refines(candidate, best)) {
                    best = candidate;
                }
            }
            return best;
        }).toList();
    }

    /**
     * `sub` が `sup` の精密化か。同じ値のより狭い記述だけを真にする。
     */
    private static boolean refines(String sub, String sup) {
        if (sub.equals(sup)) {
            return true;
        }
        if (scalarRefines(sub, sup)) {
            return true;
        }
        ArrayShape subShape = arrayShapeOf(sub);
        ArrayShape supShape = arrayShapeOf(sup);
        if (subShape == null || supShape == null
                || subShape.arguments().size() != supShape.arguments().size()) {
            return false;
        }
        boolean baseOk = subShape.base().equals(supShape.base())
                || ("non-empty-" + supShape.base()).equals(subShape.base());
        if (!baseOk) {
            return false;
        }
        for (int i = 0; i < subShape.arguments().size(); i++) {
            if (!refines(subShape.arguments().get(i), supShape.arguments().get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * スカラーの精密化。範囲付き整数 (`int<20, 20>`) と、より狭い型キーワード。
     */
    private static boolean scalarRefines(String sub, String sup) {
        return switch (sup) {
            case "int" -> sub.startsWith("int<")
                    || Set.of("positive-int", "non-negative-int", "negative-int",
                            "non-positive-int", "non-zero-int", "literal-int").contains(sub);
            case "string" -> Set.of("non-empty-string", "non-falsy-string", "truthy-string",
                    "literal-string", "non-empty-literal-string", "numeric-string",
                    "lowercase-string", "class-string", "callable-string").contains(sub);
            default -> false;
        };
    }

    /**
     * 表示用の綴りに揃えて重複を除く。包摂は行わない (確定した型をそのまま見せる用途)。
     */
    public static List<String> normalize(Collection<String> types) {
        Set<String> keys = new LinkedHashSet<>();
        for (String type : types) {
            String key = keyOf(type);
            if (!key.isEmpty() && !UNKNOWN.equals(key)) {
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    /**
     * 表示の上で同じ型か。`array<\App\User>` と `\App\User[]` は同じ意味の別表記になる。
     */
    static boolean sameType(String left, String right) {
        return keyOf(left).equals(keyOf(right));
    }

    /**
     * @param type  判定する型 (キーの形)
     * @param union 同じ union に並ぶ型 (キーの形)。自分自身を含む
     */
    private static boolean isSubsumed(String type, List<String> union) {
        List<String> others = union.stream().filter(other -> !other.equals(type)).toList();
        if (others.isEmpty()) {
            return false;
        }
        return switch (type) {
            // 型が付かなかった印。他に型があるならそれが読み手の求めるもの
            case UNKNOWN -> true;
            case MIXED -> others.stream().anyMatch(TypeUnionSimplifier::isConcrete);
            case ANY_ARRAY, ITERABLE -> others.stream().anyMatch(other -> arrayShapeOf(other) != null);
            default -> isSubsumedArray(type, others);
        };
    }

    private static boolean isConcrete(String type) {
        return !UNKNOWN.equals(type) && !MIXED.equals(type);
    }

    private static boolean isSubsumedArray(String type, List<String> others) {
        ArrayShape shape = arrayShapeOf(type);
        if (shape == null) {
            return false;
        }
        if (shape.isPlural()) {
            return others.stream().anyMatch(other -> holdsElement(other, shape.element()));
        }
        // 空でないと分かっている方が精密
        return "list".equals(shape.base())
                && others.stream().anyMatch(other -> isGenericOf("non-empty-list", other, shape.element()));
    }

    /**
     * `X[]` を呑む型か。要素型が同じ `list<X>` / `non-empty-list<X>` / `array<K, X>` /
     * `non-empty-array<K, X>` が該当する。`iterable<X>` は配列とは限らないので呑まない。
     */
    private static boolean holdsElement(String type, String element) {
        ArrayShape shape = arrayShapeOf(type);
        return shape != null && !shape.isPlural() && !ITERABLE.equals(shape.base())
                && shape.element().equals(element);
    }

    private static boolean isGenericOf(String base, String type, String element) {
        ArrayShape shape = arrayShapeOf(type);
        return shape != null && base.equals(shape.base()) && shape.element().equals(element);
    }

    /**
     * 比べるための形。そのまま表示にも使う。
     *
     * <p>PhpStorm は組み込み型を `\int` と持ち、PHPDoc から読んだ型は `int` のまま入る。同じ型なので、
     * 型キーワードに付いた `\` は落とす。PHP 標準の変数ホバーも `string` と綴るので、表示もそちらに
     * 揃う。`array<X>` はキーを縛らない配列で `X[]` と同じ意味なので、そちらに寄せる。
     */
    private static String keyOf(String type) {
        String stripped = stripKeywordPrefix(type.trim());
        ArrayShape shape = arrayShapeOf(stripped);
        if (shape == null) {
            return stripped;
        }
        if (shape.isPlural()) {
            return keyOf(shape.element()) + PLURAL;
        }
        if (ANY_ARRAY.equals(shape.base())
                && (shape.arguments().size() == 1
                    || (shape.arguments().size() == 2 && "array-key".equals(shape.arguments().getFirst())))) {
            // `array<X>` も `array<array-key, X>` もキーを縛らない配列で、`X[]` と同じ意味になる
            return keyOf(shape.element()) + PLURAL;
        }
        // 空白の揃え方が出所で違う (`array<string,int>` と `array<string, int>`)。同じ型なので
        // 組み立て直して 1 つの綴りに寄せる
        StringBuilder canonical = new StringBuilder(shape.base()).append('<');
        for (int i = 0; i < shape.arguments().size(); i++) {
            if (i > 0) {
                canonical.append(", ");
            }
            canonical.append(keyOf(shape.arguments().get(i)));
        }
        return canonical.append('>').toString();
    }

    /**
     * 型キーワードに付いた `\` を落とす。`\int[]` は `int[]` になり、クラス名 (`\App\User`) は残る。
     */
    private static String stripKeywordPrefix(String type) {
        StringBuilder result = new StringBuilder(type.length());
        int cursor = 0;
        while (cursor < type.length()) {
            char c = type.charAt(cursor);
            if (c != '\\') {
                result.append(c);
                cursor++;
                continue;
            }
            int end = cursor + 1;
            while (end < type.length() && isNamePart(type.charAt(end))) {
                end++;
            }
            String name = type.substring(cursor + 1, end);
            boolean qualified = end < type.length() && type.charAt(end) == '\\';
            if (!qualified && PhpStanTypeKeywords.find(name).isPresent()) {
                result.append(name);
            } else {
                result.append('\\').append(name);
            }
            cursor = end;
        }
        return result.toString();
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-';
    }

    /**
     * 配列系の型を基底名と型引数に分けたもの。
     *
     * @param base      `[]` (複数形) / `list` / `non-empty-list` / `array` / `non-empty-array` / `iterable`
     * @param arguments 型引数。複数形は要素型ひとつ、`array<K, V>` はキーと値
     */
    record ArrayShape(String base, List<String> arguments) {
        /** 要素型。`array<K, V>` はキーではなく値。 */
        String element() {
            return arguments.getLast();
        }

        boolean isPlural() {
            return PLURAL.equals(base);
        }
    }

    /**
     * @return 配列系でなければ null
     */
    static @Nullable ArrayShape arrayShapeOf(String type) {
        String trimmed = type.trim();
        if (trimmed.endsWith(PLURAL)) {
            String element = trimmed.substring(0, trimmed.length() - PLURAL.length()).trim();
            return element.isEmpty() ? null : new ArrayShape(PLURAL, List.of(element));
        }
        int angle = trimmed.indexOf('<');
        if (angle < 0 || !trimmed.endsWith(">")) {
            return null;
        }
        String base = trimmed.substring(0, angle).trim();
        if (!GENERIC_COLLECTIONS.contains(base)) {
            return null;
        }
        List<String> arguments = typeArgumentsOf(trimmed.substring(angle + 1, trimmed.length() - 1));
        return arguments.isEmpty() ? null : new ArrayShape(base, arguments);
    }

    /**
     * 交差型 (`T&Countable`) を成分に分ける。
     *
     * <p>`array{a: int}&Countable` のように、型引数や配列形状の中にも `&` は現れうるため、
     * 最上位の `&` だけを区切りにする。交差でない型は成分ひとつの一覧になる。
     */
    static List<String> intersectionMembersOf(String type) {
        List<String> members = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<' || c == '{' || c == '(') {
                depth++;
            } else if (c == '>' || c == '}' || c == ')') {
                depth--;
            } else if (c == '&' && depth == 0) {
                members.add(type.substring(start, i).trim());
                start = i + 1;
            }
        }
        members.add(type.substring(start).trim());
        return members.stream().filter(member -> !member.isEmpty()).toList();
    }

    /** 最上位の `,` で型引数に分ける。入れ子の `<>` や `{}` の中の `,` は区切りにしない。 */
    private static List<String> typeArgumentsOf(String arguments) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '<' || c == '{') {
                depth++;
            } else if (c == '>' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(arguments.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(arguments.substring(start).trim());
        return parts.stream().filter(part -> !part.isEmpty()).toList();
    }
}
