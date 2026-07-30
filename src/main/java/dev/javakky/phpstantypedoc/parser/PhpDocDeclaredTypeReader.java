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
