package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ジェネリクスの型変数と実引数の対応を作る。
 *
 * <p>`Op<\App\User>` のように型引数が付いた型から、クラスの `@template` 宣言の順序に沿って
 * 「V → \App\User」の対応を得る。PhpStorm は型引数を {@link PhpType} 上に持てるが、
 * それをメンバの戻り値へ伝播させないため、その差し替えを自前で行う。
 */
final class GenericTypeArguments {
    private static final String TEMPLATE_TAG = "@template";

    private GenericTypeArguments() {
    }

    /**
     * @param parametrizedType `\App\Op<\App\User>` の形の型文字列
     * @return 型変数名 → 実引数の型文字列。対応が作れないときは空
     */
    static Map<String, String> of(PhpClass phpClass, String parametrizedType) {
        List<String> arguments = PhpType.getParametrizedParts(parametrizedType);
        if (arguments.isEmpty()) {
            return Map.of();
        }
        List<String> names = templateNames(phpClass);
        Map<String, String> bindings = new LinkedHashMap<>();
        for (int i = 0; i < names.size() && i < arguments.size(); i++) {
            String argument = arguments.get(i);
            // 未解決のまま残った型変数 (`#ᢐV.`) を実引数として扱わない。
            // 扱うと `non-empty-list<V>` のような、型変数が残った型を出してしまう
            if (argument.startsWith("#") || argument.isBlank() || names.contains(argument)) {
                return Map.of();
            }
            bindings.put(names.get(i), argument);
        }
        return bindings;
    }

    /**
     * @return `@template` で宣言された型変数名を宣言順に返す
     */
    static List<String> templateNames(PhpClass phpClass) {
        return new ArrayList<>(templates(phpClass.getDocComment()).keySet());
    }

    /**
     * `@template K of array-key` を「名前 → 上界」で読む。
     *
     * <p>上界は、実引数から決められなかった型変数を埋めるのに使う。埋めないとその型変数を含む
     * 分岐が丸ごと出せない。
     *
     * @return 宣言順の 名前 → `of` に書かれた上界。`of` が無い型変数は空文字列
     */
    static Map<String, String> templates(PhpDocComment doc) {
        if (doc == null) {
            return Map.of();
        }
        Map<String, String> templates = new LinkedHashMap<>();
        for (String tag : new String[]{TEMPLATE_TAG, "@phpstan-" + TEMPLATE_TAG.substring(1),
                "@psalm-" + TEMPLATE_TAG.substring(1)}) {
            for (PhpDocTag docTag : doc.getTagElementsByName(tag)) {
                String declaration = docTag.getText().substring(
                        docTag.getText().indexOf(tag) + tag.length()).trim();
                String name = firstIdentifierOf(declaration);
                if (name != null) {
                    templates.putIfAbsent(name, boundOf(declaration.substring(name.length())));
                }
            }
        }
        return templates;
    }

    /**
     * `@template K of array-key` の `K`。
     *
     * <p>{@code PhpDocTag#getTagValue()} は空文字列を返すことがあるため、タグのテキストから直接読む。
     */
    private static String firstIdentifierOf(String declaration) {
        int end = 0;
        while (end < declaration.length()
                && (Character.isLetterOrDigit(declaration.charAt(end)) || declaration.charAt(end) == '_')) {
            end++;
        }
        return end == 0 ? null : declaration.substring(0, end);
    }

    /**
     * 型変数名に続く `of array-key` から上界を読む。`of` が書かれていなければ空文字列。
     */
    private static String boundOf(String afterName) {
        String rest = afterName.trim();
        if (!rest.startsWith("of") || rest.length() <= 2 || !Character.isWhitespace(rest.charAt(2))) {
            return "";
        }
        return firstTypeOf(rest.substring(3).trim());
    }

    /**
     * 上界に続けて説明が書かれることがある。型引数の中の空白は区切りにしない。
     */
    private static String firstTypeOf(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<' || c == '{') {
                depth++;
            } else if (c == '>' || c == '}') {
                depth--;
            } else if (depth == 0 && Character.isWhitespace(c)) {
                return text.substring(0, i);
            }
        }
        return text;
    }
}
