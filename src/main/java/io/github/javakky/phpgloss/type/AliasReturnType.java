package io.github.javakky.phpgloss.type;

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

    /**
     * 戻り値に書かれた型エイリアスの名前。表示に「どのエイリアスを開いたか」を添えるのに使う。
     */
    public static java.util.List<String> aliasNamesOf(Method method) {
        return AliasExpansion.aliasNamesOf(method);
    }

    /** `@return` に書かれたままの型 (`list<row_shape>`)。 */
    public static Optional<String> declaredTextOf(Method method) {
        return Optional.ofNullable(AliasExpansion.declaredReturnTextOf(method));
    }

    /** エイリアスの本体。表示に添えるため 1 行に畳む。 */
    public static Optional<String> bodyOf(Method method, String aliasName) {
        return Optional.ofNullable(AliasExpansion.bodyTextOf(method, aliasName));
    }
}
