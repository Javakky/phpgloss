package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * 関数呼び出しの戻り値で、型引数の中に埋まった型変数を実引数から解く。
 *
 * <p>PhpStorm は `@return T` は解くが `@return Op<V>` の `V` は残す (実測: `\App\Op<#ᢐV.>`)。
 * 解決そのものは {@link CallSiteGenericResolver} が持ち、ここは拡張点への接続だけを担う。
 */
public final class PhpStanCallSiteGenericTypeProvider implements PhpTypeProvider4 {
    /** 他プラグインと衝突しない一意な文字。 */
    private static final char KEY = 'ᨠ';

    @Override
    public char getKey() {
        return KEY;
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof FunctionReference reference) || reference.getName() == null) {
            return null;
        }
        return new PhpType().add("#" + KEY + CallSiteGenericResolver.encode(reference));
    }

    @Override
    public @Nullable PhpType complete(String expression, Project project) {
        if (expression == null || expression.length() < 2 || expression.charAt(1) != KEY) {
            return null;
        }
        return CallSiteGenericResolver.decodeAndResolve(expression.substring(2), project);
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(
            String expression, Set<String> visited, int depth, Project project) {
        return null;
    }
}
