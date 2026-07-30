package dev.javakky.phpstantypedoc.keyword;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocType;
import dev.javakky.phpstantypedoc.parser.PhpDocTypeReferenceDetector;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;

import java.util.Optional;

/**
 * キャレット位置にある型キーワードを取り出す。
 *
 * <p>対象は PHPDoc の中の、実際に型が書かれている位置だけ。通常のコードにも `string` や `list` は
 * 現れるし、PHPDoc の説明文や `@phpstan-import-type ... as X` の `as` も同じ綴りを持つ。
 * そこまで引き受けると、型でない語にキーワードの説明を出すことになる。
 */
public final class PhpStanKeywordAtCaret {
    private PhpStanKeywordAtCaret() {
    }

    public static Optional<PhpStanTypeKeyword> find(PsiFile file, int offset) {
        if (file == null || !file.isValid()) {
            return Optional.empty();
        }
        PsiElement element = file.findElementAt(offset);
        PhpDocComment doc = PsiTreeUtil.getParentOfType(element, PhpDocComment.class, false);
        if (doc == null || !isTypePosition(file, doc, offset)) {
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

    /**
     * その位置に型が書かれているか。PHPDoc の中というだけでは足りない。
     *
     * <p>説明文にも `list` や `max` は現れるし、`@phpstan-import-type X from Y as Z` の `as` も
     * PHPDoc の中にある。そこまで拾うと、型でない語にキーワードの説明を出してしまう。
     *
     * <p>型が書かれる場所は 2 種類ある。`@param` / `@return` / `@var` の型と、
     * `@phpstan-type` の本体。
     */
    private static boolean isTypePosition(PsiFile file, PhpDocComment doc, int offset) {
        // まず PHP が型と認識した範囲を使う。union / intersection / 括弧 / 複数行を
        // 自前で数えると必ずどこかで外れる (複数行の array shape で実際に外れた)
        if (PsiTreeUtil.getParentOfType(file.findElementAt(offset), PhpDocType.class, false) != null) {
            return true;
        }
        boolean inTag = new PhpDocTypeReferenceDetector()
                .detect(doc.getText(), doc.getTextRange().getStartOffset(), offset)
                .isPresent();
        if (inTag) {
            return true;
        }
        return PhpFileModelCache.of(file).findDocBlockContainingOffset(offset)
                .filter(block -> block.aliases().stream()
                        .anyMatch(alias -> alias.typeBodyRange().containsExclusiveEnd(offset)))
                .isPresent();
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
