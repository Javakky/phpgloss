package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 呼び出されたジェネリッククラスの `__invoke` に、型引数を伝播させる。
 *
 * <p>PhpStorm は `Op<\App\User>` を型として持てるが、`__invoke` の `@return non-empty-list<V>` の
 * `V` にそれを結び付けない。結果として `$op()` は素の `array` になり、要素型も list である構造も
 * 失われる (YouTrack WI-65938、2022 年起票・未解決)。
 *
 * <p>呼び出し先が関数呼び出しのときは、その関数の戻り値の解決からこの伝播までを
 * {@code complete} の中で一度に行う。型を二段に分けて持ち回ると、途中で型引数が落ちるため。
 */
public final class PhpStanInvokeTypeProvider implements PhpTypeProvider4 {
    /** 他プラグインと衝突しない一意な文字。 */
    private static final char KEY = 'ᨡ';
    /** 呼び出し先が関数呼び出しであることを表す印。 */
    private static final char FROM_CALL = 'ᨥ';
    /** 型は `|` で区切られるため、符号化した中身の `|` は別の文字へ退避する。 */
    private static final char UNION_ESCAPE = 'ᨢ';

    private static final String INVOKE = "__invoke";

    @Override
    public char getKey() {
        return KEY;
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof FunctionReference reference) || reference.getName() != null) {
            // 名前を持つ呼び出しは通常の関数呼び出し。`$op()` は名前を持たない
            return null;
        }
        PhpTypedElement callee = calleeOf(reference);
        if (callee == null) {
            return null;
        }
        if (callee instanceof FunctionReference calleeCall && calleeCall.getName() != null) {
            // `opOf(...)()` の形。呼び出し先の解決ごと complete で行う
            return new PhpType().add("#" + KEY + FROM_CALL + CallSiteGenericResolver.encode(calleeCall));
        }
        PhpType calleeType = callee.getType();
        return calleeType.isEmpty()
                ? null
                : new PhpType().add("#" + KEY + calleeType.toString().replace('|', UNION_ESCAPE));
    }

    @Override
    public @Nullable PhpType complete(String expression, Project project) {
        if (expression == null || expression.length() < 2 || expression.charAt(1) != KEY) {
            return null;
        }
        String body = expression.substring(2);
        PhpType calleeType = body.isEmpty() || body.charAt(0) != FROM_CALL
                ? completeSignature(body, project)
                : CallSiteGenericResolver.decodeAndResolve(body.substring(1), project);
        if (calleeType == null) {
            return null;
        }
        PhpType result = new PhpType();
        for (String typeString : calleeType.getTypesWithParametrisedParts()) {
            PhpType invoked = invokeResultOf(project, typeString);
            if (invoked != null) {
                result.add(invoked);
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(
            String expression, Set<String> visited, int depth, Project project) {
        return null;
    }

    /**
     * {@code PhpType#add} は文字列を union として分解しないので、先に自分で分ける。
     */
    private PhpType completeSignature(String encoded, Project project) {
        PhpType raw = new PhpType();
        PhpType.splitTopLevelUnion(encoded.replace(UNION_ESCAPE, '|')).forEach(raw::add);
        return PhpIndex.getInstance(project).completeType(project, raw, new HashSet<>());
    }

    /**
     * 型引数を持つ型 (`\App\Op<\App\User>`) から、その `__invoke` の戻り値を組み立てる。
     */
    private @Nullable PhpType invokeResultOf(Project project, String typeString) {
        int angle = typeString.indexOf('<');
        if (angle < 0) {
            return null;
        }
        String classFqn = typeString.substring(0, angle);
        for (PhpClass phpClass : PhpIndex.getInstance(project).getClassesByFQN(classFqn)) {
            Method invoke = phpClass.findMethodByName(INVOKE);
            if (invoke == null) {
                continue;
            }
            Map<String, String> bindings = GenericTypeArguments.of(phpClass, typeString);
            PhpDocComment doc = invoke.getDocComment();
            if (bindings.isEmpty() || doc == null) {
                continue;
            }
            String declared = PhpDocDeclaredTypeReader.typeNameFor(
                    doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
            if (declared == null) {
                continue;
            }
            String substituted = TemplateSubstitution.apply(declared, bindings);
            if (!TemplateSubstitution.hasUnresolved(substituted, bindings.keySet())) {
                return new PhpType().add(substituted);
            }
        }
        return null;
    }

    /**
     * `$op()` の `$op` にあたる式を返す。`opOf([])()` なら内側の呼び出しがそれにあたる。
     */
    private @Nullable PhpTypedElement calleeOf(FunctionReference reference) {
        for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PhpTypedElement typed && !(child instanceof PsiWhiteSpace)) {
                return typed;
            }
        }
        return null;
    }
}
