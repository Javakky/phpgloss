package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import dev.javakky.phpstantypedoc.PhpStanTypeResolverFacade;
import dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader;
import org.jetbrains.annotations.Nullable;


/**
 * 戻り値に書かれた型エイリアスを、その本体に置き換える。
 *
 * <p>`@phpstan-type row_shape array{...}` を宣言したクラスの中では、エイリアスは import なしで
 * 使える。`@return list<row_shape>` はそのまま `list<array{...}>` を意味するが、PhpStorm には
 * エイリアスという概念が無いので `list<array>` まで落ちる (実測)。宣言元のクラスで引き直して展開する。
 *
 * <p>展開できない名前が残るときは何もしない。半端に開いた型は読み手を誤らせる。
 */
final class AliasExpansion {
    private static final PhpStanTypeResolverFacade RESOLVER = new PhpStanTypeResolverFacade();
    /** 入れ子のエイリアス (`@phpstan-type a array{b: b_record}`) を開く深さの上限。 */
    private static final int MAX_DEPTH = 8;

    private AliasExpansion() {
    }

    /**
     * メソッド呼び出しの戻り値を、エイリアスを開いた形で返す。
     *
     * @return 開くものが無い、または開けない名前が残るときは null
     */
    static @Nullable PhpType returnTypeOf(MethodReference reference) {
        for (var resolved : reference.multiResolve(false)) {
            if (!(resolved.getElement() instanceof Method method)) {
                continue;
            }
            PhpType expanded = expandedReturnOf(method);
            if (expanded != null) {
                return expanded;
            }
        }
        return null;
    }

    private static @Nullable PhpType expandedReturnOf(Method method) {
        PhpDocComment doc = method.getDocComment();
        PhpClass owner = method.getContainingClass();
        if (doc == null || owner == null) {
            return null;
        }
        String declared = PhpDocDeclaredTypeReader.unionTypeFor(
                doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        if (declared == null) {
            return null;
        }
        String expanded = expand(declared, owner);
        if (expanded == null || expanded.equals(declared)) {
            return null;
        }
        PhpType type = new PhpType();
        for (String part : PhpType.splitTopLevelUnion(expanded)) {
            type.add(part);
        }
        return type.isEmpty() ? null : type;
    }

    /**
     * 型テキストの中のエイリアス名を本体に置き換える。
     *
     * @return 置き換えるものが無ければ元のまま。エイリアスらしき名前が解決できないときも元のまま
     */
    static @Nullable String expand(String declared, PhpClass owner) {
        PsiFile file = owner.getContainingFile();
        String fqn = owner.getFQN();
        if (file == null || fqn == null) {
            return declared;
        }
        String current = declared;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            String next = expandOnce(current, file, fqn, owner.getProject());
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        // 開き切らないのは循環している証拠。半端な形は返さない
        return null;
    }

    private static String expandOnce(String type, PsiFile file, String ownerFqn, Project project) {
        StringBuilder result = new StringBuilder(type.length());
        int cursor = 0;
        while (cursor < type.length()) {
            if (!isNameStart(type.charAt(cursor))) {
                result.append(type.charAt(cursor));
                cursor++;
                continue;
            }
            int end = cursor;
            while (end < type.length() && isNamePart(type.charAt(end))) {
                end++;
            }
            String name = type.substring(cursor, end);
            // 直後が `:` ならエイリアス名ではなく array shape のキー
            boolean shapeKey = end < type.length() && type.charAt(end) == ':';
            String body = shapeKey ? null : bodyOf(file, ownerFqn, name);
            result.append(body == null ? name : "(" + body + ")");
            cursor = end;
        }
        return result.toString();
    }

    private static @Nullable String bodyOf(PsiFile file, String ownerFqn, String name) {
        return RESOLVER.resolveInClass(file, ownerFqn, name)
                .filter(resolved -> !resolved.unresolved() && !resolved.circular())
                .map(resolved -> resolved.typeBody().trim())
                .filter(body -> !body.isEmpty())
                .orElse(null);
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * 開いた戻り値をテキストのまま返す。表示はこちらを使う (型に落とすと shape が畳まれる)。
     */
    static @Nullable String expandedReturnTextOf(Method method) {
        PhpDocComment doc = method.getDocComment();
        PhpClass owner = method.getContainingClass();
        if (doc == null || owner == null) {
            return null;
        }
        String declared = PhpDocDeclaredTypeReader.unionTypeFor(
                doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        if (declared == null) {
            return null;
        }
        String expanded = expand(declared, owner);
        return expanded == null || expanded.equals(declared) ? null : expanded;
    }
}
