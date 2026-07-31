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
