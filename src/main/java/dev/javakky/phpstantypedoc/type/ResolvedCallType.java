package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.Optional;

/**
 * 呼び出し式を、この plugin の解決器でその場で解いた型。表示側が使う公開の入口。
 *
 * <p>ここで型が得られたということは、条件型の分岐が実引数で確定した (または型変数が
 * すべて解けた) ということ。union に並ぶ他の成分は同じ値の粗い近似であって選択肢ではないと
 * 分かるので、表示はこの型に固めてよい。解決器が黙る呼び出しでは empty を返し、
 * その場合の union は選択肢を含みうるため、呼び出し側は保守的な整理に留める。
 */
public final class ResolvedCallType {
    /**
     * 同じ呼び出しは型プロバイダの経路とホバーの経路の両方から解かれる。計算は呼び出しの
     * PSI 単位で 1 回にし、PSI が変わったら作り直す。
     */
    private static final Key<CachedValue<Optional<PhpType>>> CACHE =
            Key.create("phpstantypedoc.resolved.call.type");

    private ResolvedCallType() {
    }

    public static Optional<PhpType> of(FunctionReference reference) {
        return CachedValuesManager.getCachedValue(reference, CACHE, () ->
                CachedValueProvider.Result.create(
                        resolve(reference), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static Optional<PhpType> resolve(FunctionReference reference) {
        PhpType resolved = reference instanceof MethodReference method
                ? CallSiteGenericResolver.resolveMethodHere(method)
                : reference.getName() != null ? CallSiteGenericResolver.resolveCallHere(reference) : null;
        return resolved == null || resolved.isEmpty() ? Optional.empty() : Optional.of(resolved);
    }
}
