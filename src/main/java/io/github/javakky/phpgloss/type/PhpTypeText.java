package io.github.javakky.phpgloss.type;

import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 型を書かれたテキストとして扱う操作。IDE の版差をここで吸収する。
 *
 * <p>{@code PhpType.splitTopLevelUnion} は 2026.1 で入ったもので、2025.x には無い
 * (実測: そこだけで 10 件の非互換になった)。**あれば本家に委ね、無いときだけ自前で分ける**。
 * 自前の実装を常用しないのは、union の分け方が本家と食い違うと束縛が静かに壊れるため。
 * 本家が使える IDE では本家の答えがそのまま出る。
 */
public final class PhpTypeText {
    /** 本家の実装。無い IDE では null。呼び出しごとに探すと重いので一度だけ引く。 */
    private static final @Nullable Method DELEGATE = findDelegate();

    private PhpTypeText() {
    }

    /**
     * 最上位の {@code |} で union を分ける。型引数 ({@code <>})、array shape ({@code {}})、
     * 条件型や callable の括弧 ({@code ()}) の中の {@code |} は区切りにしない。
     */
    @SuppressWarnings("unchecked")
    public static List<String> splitTopLevelUnion(String type) {
        if (DELEGATE != null) {
            try {
                return (List<String>) DELEGATE.invoke(null, type);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                // 本家が呼べなくなったら自前に落ちる。ここで諦めると型が一切出なくなる
            }
        }
        return splitHere(type);
    }

    /** 本家が使えるか。版差の扱いを測るためにテストから見る。 */
    static boolean delegatesToPhpPlugin() {
        return DELEGATE != null;
    }

    private static @Nullable Method findDelegate() {
        try {
            Method method = PhpType.class.getMethod("splitTopLevelUnion", String.class);
            return java.lang.reflect.Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (NoSuchMethodException | LinkageError absent) {
            return null;
        }
    }

    /**
     * 古い IDE 用。本家と同じ分け方をする。
     *
     * <p>本家の実測に合わせてある。空の成分は落とすが、**前後の空白は落とさない**
     * (`"int | null"` → `["int ", " null"]`)。呼び出し側が trim を前提にしていると、
     * 本家のある IDE との間で挙動が割れる。
     */
    static List<String> splitHere(String type) {
        if (type == null) {
            return List.of();
        }
        List<String> members = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<' || c == '{' || c == '(') {
                depth++;
            } else if (c == '>' || c == '}' || c == ')') {
                depth--;
            } else if (c == '|' && depth == 0) {
                addMember(members, type.substring(start, i));
                start = i + 1;
            }
        }
        addMember(members, type.substring(start));
        // 区切りが無いときは元の型ひとつ。空文字列でも 1 件返すのが本家の挙動 (実測)
        return members.isEmpty() ? List.of(type) : List.copyOf(members);
    }

    private static void addMember(List<String> members, String member) {
        if (!member.trim().isEmpty()) {
            members.add(member);
        }
    }
}
