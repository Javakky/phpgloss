package dev.javakky.phpstantypedoc.parser;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocType;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;

import java.util.List;

/**
 * PHPDoc タグに書かれた生の型名を読む。
 *
 * <p>{@code PhpDocTag#getTagValue()} は `@param CreateUserData $payload` に対して空文字列を
 * 返すため使えない。型名は PHP のパーサが型と認識した {@link PhpDocType} のテキストから取る。
 * {@code getType()} は解決済みの型を返すのでエイリアス名が失われる。
 */
public final class PhpDocDeclaredTypeReader {
    public static final List<String> PARAM_TAGS = List.of("@phpstan-param", "@param");
    public static final List<String> RETURN_TAGS = List.of("@phpstan-return", "@return");
    public static final List<String> VAR_TAGS = List.of("@phpstan-var", "@var");

    private PhpDocDeclaredTypeReader() {
    }

    /**
     * タグに書かれた型を、union を含めて丸ごと読む。
     *
     * <p>{@link #typeNameFor} は {@code PhpDocType} 経由のため union の先頭しか返さない
     * (実測: `@return V|null` が `V` になる)。こちらはタグのテキストから直接読み、
     * 型引数の中でない空白 (説明文の始まり) で切る。`V | null` のように空白を挟んだ union も繋ぐ。
     *
     * @param variableName `@param` / `@var` で変数名を絞る場合は `$` を除いた名前。絞らないなら null
     */
    public static String unionTypeFor(PhpDocComment doc, List<String> tagNames, String variableName) {
        if (doc == null) {
            return null;
        }
        for (String tagName : tagNames) {
            for (var element : doc.getTagElementsByName(tagName)) {
                String text = element.getText();
                String body = stripDecoration(text.substring(text.indexOf(tagName) + tagName.length()));
                if (variableName != null) {
                    int variable = body.indexOf("$" + variableName);
                    if (variable < 0) {
                        continue;
                    }
                    body = body.substring(0, variable);
                }
                String type = firstUnionTokenOf(body.trim());
                if (!type.isEmpty()) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * 複数行の型から PHPDoc の飾り (行頭の `*`) を落として 1 行にする。
     */
    private static String stripDecoration(String body) {
        StringBuilder result = new StringBuilder();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (trimmed.endsWith("*/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
            }
            if (!trimmed.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(trimmed);
            }
        }
        return result.toString();
    }

    /**
     * 先頭の型を、空白区切りの説明文の手前まで読む。`|` が続く限り次の語も型として繋ぐ。
     *
     * <p>`callable(V): non-empty-string` のように `:` の先に戻り型が続く形も 1 つの型として読む。
     * 以前は呼び出し側にも同じ処理があり、片方だけ直したせいで callable の戻り型が落ちていた。
     */
    public static String firstTypeOf(String body) {
        return firstUnionTokenOf(body);
    }

    private static String firstUnionTokenOf(String body) {
        StringBuilder type = new StringBuilder();
        int cursor = 0;
        while (cursor < body.length()) {
            int depth = 0;
            int start = cursor;
            while (cursor < body.length()) {
                char c = body.charAt(cursor);
                if (c == '<' || c == '{' || c == '(') {
                    depth++;
                } else if (c == '>' || c == '}' || c == ')') {
                    depth--;
                } else if (depth == 0 && Character.isWhitespace(c)) {
                    break;
                }
                cursor++;
            }
            type.append(body, start, cursor);
            // 空白の先が `|` なら union の続き。`V | null 説明` を最後まで繋ぐ
            int next = cursor;
            while (next < body.length() && Character.isWhitespace(body.charAt(next))) {
                next++;
            }
            // `callable(V, K): G` の戻り型は `:` の先にある。`:` の直後で切ると型が落ちる
            char last = type.isEmpty() ? ' ' : type.charAt(type.length() - 1);
            boolean continues = last == '|' || last == ':' || last == '&';
            if (next < body.length()
                    && (body.charAt(next) == '|' || body.charAt(next) == ':' || continues)) {
                if (body.charAt(next) == ':' && last != ':') {
                    type.append(':');
                    next++;
                    while (next < body.length() && Character.isWhitespace(body.charAt(next))) {
                        next++;
                    }
                }
                if (next < body.length() && body.charAt(next) == '|') {
                    type.append('|');
                    next++;
                    while (next < body.length() && Character.isWhitespace(body.charAt(next))) {
                        next++;
                    }
                }
                cursor = next;
                continue;
            }
            break;
        }
        return type.toString();
    }

    /**
     * @param variableName 変数名で絞る場合は `$` を除いた名前。絞らない場合は null
     */
    public static String typeNameFor(PhpDocComment doc, List<String> tagNames, String variableName) {
        if (doc == null) {
            return null;
        }
        for (String tagName : tagNames) {
            for (PhpDocTag tag : doc.getTagElementsByName(tagName)) {
                String typeName = typeNameFromTag(tag, variableName);
                if (typeName != null) {
                    return typeName;
                }
            }
        }
        return null;
    }

    private static String typeNameFromTag(PhpDocTag tag, String variableName) {
        if (variableName != null && !declaresVariable(tag, variableName)) {
            return null;
        }
        PhpDocType docType = PsiTreeUtil.findChildOfType(tag, PhpDocType.class);
        if (docType == null) {
            return null;
        }
        String typeName = docType.getText();
        return typeName == null || typeName.isBlank() ? null : typeName;
    }

    private static boolean declaresVariable(PhpDocTag tag, String variableName) {
        String expected = "$" + variableName;
        for (String part : tag.getText().trim().split("\\s+")) {
            if (part.equals(expected)) {
                return true;
            }
        }
        return false;
    }
}
