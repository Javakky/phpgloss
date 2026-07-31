package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.AssignmentExpression;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import org.jetbrains.annotations.Nullable;

/**
 * 解決器そのものの出力を見るための道具。
 *
 * <p>変数の型を見るだけでは足りない場面がある。素の `@return` を PhpStorm 自身が解いて同じ union に
 * 並べるため、こちらが何も解けていなくても表示が変わらない (`reduce` の `@return R|V|null` や、
 * 交差型の戻り値がそれ)。そこだけはこの解決器の戻り値を直接測る。
 */
final class ResolvedCall {
    private ResolvedCall() {
    }

    /**
     * @param variableName 呼び出しの結果を受けている変数の名前
     * @return 解決器が組み立てた型。何も解けなければ null
     */
    static @Nullable PhpType of(PsiFile file, String variableName) {
        for (AssignmentExpression assignment
                : PsiTreeUtil.findChildrenOfType(file, AssignmentExpression.class)) {
            if (assignment.getVariable() instanceof Variable variable
                    && variableName.equals(variable.getName())
                    && assignment.getValue() instanceof FunctionReference reference) {
                return CallSiteGenericResolver.resolveCallHere(reference);
            }
        }
        throw new IllegalArgumentException("呼び出しが見つからない: " + variableName);
    }
}
