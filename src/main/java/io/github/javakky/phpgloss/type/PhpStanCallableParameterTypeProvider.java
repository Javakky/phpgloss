package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import io.github.javakky.phpgloss.parser.PhpDocDeclaredTypeReader;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 関数の本体で、callable の仮引数を呼んだ結果の型。
 *
 * <p>`function groupBy(array $input, callable $classifier)` の本体で `$classifier($value, $key)`
 * を呼ぶと、PhpStorm は型なし (`?`) を返す。PHPDoc には `@param callable(V, K): G $classifier` と
 * 書かれていて、戻り位置の型は分かる。呼び出し元が決まらないので型変数そのものは埋められないが、
 * `@template G of array-key` の上界までは言える (PHPStan も `G of (int|string)` と表示する)。
 *
 * <p>上界の無い型変数では何も言わない。`mixed` を返しても読み手には何も伝わらないため。
 */
public final class PhpStanCallableParameterTypeProvider implements PhpTypeProvider4 {
    /**
     * この plugin が使う型のキー。他と重ならない 1 文字を選ぶ。
     *
     * <p>`ᨭ` は呼び出しの符号化で名前付き引数の区切りに使っており、同じ文字を
     * キーにすると型の中の区切りとキーが見分けられなくなる。
     */
    private static final char KEY = 'ᨯ';

    @Override
    public char getKey() {
        return KEY;
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof FunctionReference reference)
                || element instanceof com.jetbrains.php.lang.psi.elements.MethodReference) {
            return null;
        }
        String parameter = calleeParameterNameOf(reference);
        if (parameter == null) {
            return null;
        }
        Function enclosing = PsiTreeUtil.getParentOfType(reference, Function.class);
        if (enclosing == null || !declaresParameter(enclosing, parameter)) {
            return null;
        }
        String returnType = callableReturnTypeOf(enclosing.getDocComment(), parameter);
        if (returnType == null) {
            return null;
        }
        Map<String, String> templates = GenericTypeArguments.templates(enclosing.getDocComment());
        String resolved = templates.containsKey(returnType)
                ? templates.get(returnType)
                : returnType;
        if (resolved == null || resolved.isBlank() || TemplateSubstitution.hasUnresolved(
                resolved, templates.keySet())) {
            return null;
        }
        PhpType type = new PhpType();
        for (String part : PhpTypeText.splitTopLevelUnion(expandKeyword(resolved))) {
            type.add(part);
        }
        return type.isEmpty() ? null : type;
    }

    @Override
    public @Nullable PhpType complete(String s, Project project) {
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(
            String expression, Set<String> visited, int depth, Project project) {
        return null;
    }

    /** `array-key` のように、そのままでは型として読めない語を PHP の型に開く。 */
    private String expandKeyword(String type) {
        return switch (type.trim()) {
            case "array-key" -> "int|string";
            case "scalar" -> "int|float|string|bool";
            default -> type;
        };
    }

    /**
     * `$classifier(...)` の `classifier`。呼び出し対象が変数でなければ null。
     *
     * <p>通常の関数呼び出し (`helper($x)`) では、先頭の子は関数名で、変数は引数リストの中に入る。
     * 直接の子だけを見るので、そちらを取り違えることはない。
     */
    private @Nullable String calleeParameterNameOf(FunctionReference reference) {
        for (PsiElement child = reference.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            if (child instanceof Variable variable) {
                return variable.getName();
            }
            if (child instanceof FunctionReference) {
                // `$a()()` の内側は別の呼び出し。ここでは扱わない
                return null;
            }
        }
        return null;
    }

    private boolean declaresParameter(Function function, String name) {
        for (Parameter parameter : function.getParameters()) {
            if (name.equals(parameter.getName())) {
                return true;
            }
        }
        return false;
    }

    /** `@param callable(V, K): G $classifier` の `G`。 */
    private @Nullable String callableReturnTypeOf(@Nullable PhpDocComment doc, String parameter) {
        String declared = PhpDocDeclaredTypeReader.unionTypeFor(
                doc, PhpDocDeclaredTypeReader.PARAM_TAGS, parameter);
        if (declared == null) {
            return null;
        }
        String trimmed = declared.trim();
        if (!trimmed.startsWith("callable(") && !trimmed.startsWith("\\Closure(")
                && !trimmed.startsWith("Closure(")) {
            return null;
        }
        int close = matchingParenthesisOf(trimmed);
        if (close < 0) {
            return null;
        }
        String rest = trimmed.substring(close + 1).trim();
        return rest.startsWith(":") ? rest.substring(1).trim() : null;
    }

    /** 引数リストを閉じる `)` の位置。入れ子の括弧を数える。 */
    private int matchingParenthesisOf(String type) {
        int depth = 0;
        for (int i = type.indexOf('('); i >= 0 && i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
