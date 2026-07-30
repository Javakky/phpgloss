package dev.javakky.phpstantypedoc.keyword;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;

import java.util.Optional;

/**
 * キャレット位置にある型キーワードを取り出す。
 *
 * <p>対象は PHPDoc の中だけ。通常のコードにも `string` や `list` は現れるので、そこまで
 * 引き受けると PHP 本来の移動先を奪う。
 */
public final class PhpStanKeywordAtCaret {
    private PhpStanKeywordAtCaret() {
    }

    public static Optional<PhpStanTypeKeyword> find(PsiFile file, int offset) {
        if (file == null || !file.isValid()) {
            return Optional.empty();
        }
        PsiElement element = file.findElementAt(offset);
        if (PsiTreeUtil.getParentOfType(element, PhpDocComment.class, false) == null) {
            return Optional.empty();
        }
        String text = file.getText();
        if (offset < 0 || offset >= text.length() || !isWordPart(text.charAt(offset))) {
            return Optional.empty();
        }
        int start = offset;
        while (start > 0 && isWordPart(text.charAt(start - 1))) {
            start--;
        }
        int end = offset + 1;
        while (end < text.length() && isWordPart(text.charAt(end))) {
            end++;
        }
        // `array{list: int}` の `list` はキー名であってキーワードではない
        if (isShapeKey(text, end)) {
            return Optional.empty();
        }
        return PhpStanTypeKeywords.find(text.substring(start, end));
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    private static boolean isShapeKey(String text, int end) {
        int cursor = end;
        while (cursor < text.length() && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '?')) {
            cursor++;
        }
        return cursor < text.length() && text.charAt(cursor) == ':';
    }
}
