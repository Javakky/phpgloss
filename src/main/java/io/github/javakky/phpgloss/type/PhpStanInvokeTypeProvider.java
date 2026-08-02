package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.DumbService;
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
import com.jetbrains.php.lang.psi.resolve.types.generics.PhpGenericsBaseExtendedWithGenericTypeProvider;
import io.github.javakky.phpgloss.parser.PhpDocDeclaredTypeReader;
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
    /** PhpStorm が型変数を符号化するときの印 (`#ᢐV.`)。 */
    private static final String TEMPLATE_MARKER = "#ᢐ";

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
        if (!DumbService.isDumb(element.getProject())) {
            // 未解決型を挟むと、この型に対する要素アクセスが読めなくなる (実測: `$xs[0]` が
            // 一段前の型になる)。索引外ではその場で解いて、解決済みの型をそのまま返す
            return resolveHere(callee, element.getProject());
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
     * 索引外で、呼び出し先の型から `__invoke` の戻り値をその場で組み立てる。
     */
    private @Nullable PhpType resolveHere(PhpTypedElement callee, Project project) {
        PhpType calleeType = callee instanceof FunctionReference calleeCall && calleeCall.getName() != null
                ? ResolvedCallType.of(calleeCall).orElse(null)
                : callee.getType().global(project);
        if (calleeType == null || calleeType.isEmpty()) {
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

    /**
     * {@code PhpType#add} は文字列を union として分解しないので、先に自分で分ける。
     */
    private PhpType completeSignature(String encoded, Project project) {
        PhpType raw = new PhpType();
        PhpTypeText.splitTopLevelUnion(encoded.replace(UNION_ESCAPE, '|')).forEach(raw::add);
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
        for (PhpClass phpClass : PhpIndex.getInstance(project).getAnyByFQN(classFqn)) {
            Method invoke = phpClass.findMethodByName(INVOKE);
            Map<String, String> bindings = invoke == null
                    ? Map.of()
                    : GenericTypeArguments.of(phpClass, typeString);
            if (bindings.isEmpty()) {
                continue;
            }
            PhpType resolved = fromDocText(invoke, bindings);
            if (resolved == null) {
                resolved = fromEncodedType(invoke, bindings);
            }
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * PHPDoc に書かれたテキストの型変数を差し替える。内部の置換 API は書かれたままの `V` に
     * 効かないため、こちらは文字列で差し替える。
     */
    private @Nullable PhpType fromDocText(Method invoke, Map<String, String> bindings) {
        PhpDocComment doc = invoke.getDocComment();
        String declared = doc == null
                ? null
                : PhpDocDeclaredTypeReader.unionTypeFor(doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        if (declared == null) {
            return null;
        }
        String substituted = TemplateSubstitution.apply(declared, bindings);
        return TemplateSubstitution.hasUnresolved(substituted, bindings.keySet())
                ? null
                : withPluralForm(substituted);
    }

    /**
     * 戻り値が `@psalm-return` だけで書かれている場合のように、PHPDoc のテキストからは読めない型。
     *
     * <p>PhpStorm 自身はそれを解釈していて、型変数をエンコード形 (`non-empty-list<#ᢐV.>`) で
     * 持っている。この形は素の名前をキーにしたマッピングで内部の置換 API が埋められる (実測)。
     */
    private @Nullable PhpType fromEncodedType(Method invoke, Map<String, String> bindings) {
        PhpType declared = invoke.getType();
        if (!hasEncodedTemplate(declared)) {
            return null;
        }
        PhpType substituted = PhpGenericsBaseExtendedWithGenericTypeProvider
                .substituteTypesWithMapping(declared, bindings, 0);
        // 埋まらなかった型変数を含む型は出さない。読み手に意味がないため
        return substituted == null || substituted.isEmpty() || hasEncodedTemplate(substituted)
                ? null
                : substituted;
    }

    private static boolean hasEncodedTemplate(PhpType type) {
        return type.getTypesWithParametrisedParts().stream()
                .anyMatch(part -> part.contains(TEMPLATE_MARKER));
    }

    /**
     * 要素アクセスが読める形も足す。総称の形は残すので精度は落ちない。
     */
    private static PhpType withPluralForm(String type) {
        // union を 1 成分のまま add すると union として扱われない。分けて足す
        PhpType resolved = new PhpType();
        for (String part : PhpTypeText.splitTopLevelUnion(type)) {
            resolved.add(part);
            String plural = TemplateSubstitution.pluralFormOf(part);
            if (plural != null) {
                resolved.add(plural);
            }
        }
        return resolved;
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
