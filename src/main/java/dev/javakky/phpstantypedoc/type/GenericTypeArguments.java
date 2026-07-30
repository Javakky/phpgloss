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
        PhpDocComment doc = phpClass.getDocComment();
        if (doc == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String tag : new String[]{TEMPLATE_TAG, "@phpstan-" + TEMPLATE_TAG.substring(1),
                "@psalm-" + TEMPLATE_TAG.substring(1)}) {
            for (PhpDocTag docTag : doc.getTagElementsByName(tag)) {
                String name = firstWordAfterTag(docTag.getText(), tag);
                if (name != null && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * `@template K of array-key` から `K` を取り出す。
     *
     * <p>{@code PhpDocTag#getTagValue()} は空文字列を返すことがあるため、タグのテキストから直接読む。
     */
    private static String firstWordAfterTag(String tagText, String tag) {
        String rest = tagText.substring(tagText.indexOf(tag) + tag.length()).trim();
        if (rest.isEmpty()) {
            return null;
        }
        int end = 0;
        while (end < rest.length() && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_')) {
            end++;
        }
        return end == 0 ? null : rest.substring(0, end);
    }
}
