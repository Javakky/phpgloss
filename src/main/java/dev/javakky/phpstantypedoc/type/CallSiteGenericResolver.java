package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.generics.PhpGenericsBaseExtendedWithGenericTypeProvider;
import dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;
import dev.javakky.phpstantypedoc.resolution.ClassNameResolver;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 関数呼び出しの戻り値を、実引数から型変数を解いた形で組み立てる。
 *
 * <p>PhpStorm は戻り値がそのまま型変数なら解くが、`@return Op<V>` のように型引数の中に埋まった
 * 型変数は未解決のまま残す (実測: `\App\Op<#ᢐV.>`)。ここがその欠けている段。
 *
 * <p>索引中は使えないので、呼び出しの「関数名 + 実引数のシグネチャ」を符号化して持ち回り、
 * 索引後にこのクラスで解決する。置換は PhpStorm 内部の {@code substituteTypesWithMapping} に任せる。
 */
final class CallSiteGenericResolver {
    /** 型は `|` で区切られるため、符号化した中身の `|` は別の文字へ退避する。 */
    private static final char UNION_ESCAPE = 'ᨣ';
    /** 関数名と引数を区切る文字。 */
    private static final char FIELD = 'ᨤ';

    private CallSiteGenericResolver() {
    }

    /**
     * 呼び出しから、索引後に解決できる形の文字列を作る。ローカル情報しか使わない。
     */
    static String encode(FunctionReference reference) {
        StringBuilder encoded = new StringBuilder(reference.getName());
        for (PsiElement argument : reference.getParameters()) {
            encoded.append(FIELD);
            if (argument instanceof PhpTypedElement typed) {
                encoded.append(typed.getType().toString().replace('|', UNION_ESCAPE));
            }
        }
        return encoded.toString();
    }

    /**
     * @param encoded {@link #encode} で作った文字列
     * @return 型変数を解いた戻り値の型。解けないときは null
     */
    static @Nullable PhpType decodeAndResolve(String encoded, Project project) {
        List<String> fields = split(encoded);
        if (fields.isEmpty()) {
            return null;
        }
        String functionName = fields.getFirst();
        List<String> argumentSignatures = fields.subList(1, fields.size());
        PhpType result = new PhpType();
        for (Function function : PhpIndex.getInstance(project).getFunctionsByName(functionName)) {
            PhpType resolved = returnTypeOf(project, function, argumentSignatures);
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static @Nullable PhpType returnTypeOf(
            Project project,
            Function function,
            List<String> argumentSignatures
    ) {
        Collection<String> templates = PhpGenericsBaseExtendedWithGenericTypeProvider.getTemplates(function);
        PhpDocComment doc = function.getDocComment();
        if (templates.isEmpty() || doc == null) {
            return null;
        }
        String declaredReturn = PhpDocDeclaredTypeReader.typeNameFor(
                doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        if (declaredReturn == null || !declaredReturn.contains("<")) {
            // 型引数を持たない戻り値は PhpStorm が既に解いている
            return null;
        }
        Map<String, String> bindings = TemplateUnification.empty();
        Parameter[] parameters = function.getParameters();
        for (int i = 0; i < parameters.length && i < argumentSignatures.size(); i++) {
            String declared = PhpDocDeclaredTypeReader.typeNameFor(
                    doc, PhpDocDeclaredTypeReader.PARAM_TAGS, parameters[i].getName());
            String signature = argumentSignatures.get(i);
            if (declared == null || signature.isBlank()) {
                continue;
            }
            TemplateUnification.unify(declared, actualTypeOf(project, signature), templates, bindings);
        }
        if (bindings.isEmpty()) {
            return null;
        }
        String substituted = TemplateSubstitution.apply(qualify(declaredReturn, function), bindings);
        if (TemplateSubstitution.hasUnresolved(substituted, templates)) {
            // 型変数が残ったままの型は出さない。読み手に意味がないため
            return null;
        }
        return new PhpType().add(substituted);
    }

    /**
     * `Op<V>` のように短い名前で書かれたクラスを完全修飾名にする。
     * そのままだと `\Op` として扱われ、存在しないクラスになる。
     */
    private static String qualify(String declared, Function function) {
        int angle = declared.indexOf('<');
        String base = angle < 0 ? declared : declared.substring(0, angle);
        if (base.startsWith("\\") || base.isBlank() || function.getContainingFile() == null) {
            return declared;
        }
        PhpFileModel fileModel = PhpFileModelCache.of(function.getContainingFile());
        String resolved = new ClassNameResolver().resolve(base, fileModel.namespace(), fileModel.uses());
        return resolved == null
                ? declared
                : "\\" + resolved + (angle < 0 ? "" : declared.substring(angle));
    }

    /**
     * 実引数の型を復元する。シグネチャをそのまま完成させられれば使い、空になる場合は
     * シグネチャの中で既に具体型になっている部分だけを採る。
     *
     * <p>{@code PhpType#add} は文字列を union として分解しないので、先に自分で分ける。
     */
    private static PhpType actualTypeOf(Project project, String signature) {
        List<String> parts = PhpType.splitTopLevelUnion(signature.replace(UNION_ESCAPE, '|'));
        PhpType raw = new PhpType();
        parts.forEach(raw::add);
        PhpType completed = PhpIndex.getInstance(project).completeType(project, raw, new HashSet<>());
        if (!completed.getTypesWithParametrisedParts().isEmpty()) {
            return completed;
        }
        PhpType concrete = new PhpType();
        for (String part : parts) {
            if (!part.startsWith("#") && !part.equals("?")) {
                concrete.add(part);
            }
        }
        return concrete;
    }

    private static List<String> split(String encoded) {
        List<String> fields = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= encoded.length(); i++) {
            if (i == encoded.length() || encoded.charAt(i) == FIELD) {
                fields.add(encoded.substring(start, i));
                start = i + 1;
            }
        }
        return fields;
    }
}
