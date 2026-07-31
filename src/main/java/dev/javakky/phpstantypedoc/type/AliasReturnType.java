package dev.javakky.phpstantypedoc.type;

import com.jetbrains.php.lang.psi.elements.Method;

import java.util.Optional;

/**
 * メソッドの戻り値に書かれた型エイリアスを開いた形。表示側が使う公開の入口。
 */
public final class AliasReturnType {
    private AliasReturnType() {
    }

    /**
     * @return 開くエイリアスが無い、または開けない名前が残るときは empty
     */
    public static Optional<String> expandedTextOf(Method method) {
        return Optional.ofNullable(AliasExpansion.expandedReturnTextOf(method));
    }
}
