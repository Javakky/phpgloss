package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.generics.PhpGenericsBaseExtendedWithGenericTypeProvider;
import dev.javakky.phpstantypedoc.keyword.PhpStanTypeKeywords;
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
    /** 引数の「型」と「書かれたテキスト」を区切る文字。条件型の判定にテキストが要る。 */
    private static final char SUBFIELD = 'ᨦ';
    /** メソッド呼び出しであることを表す印。続けてレシーバのシグネチャを持つ。 */
    private static final char METHOD = 'ᨧ';
    /**
     * 埋め込むシグネチャの中に区切り文字が現れるため、退避先を用意する。
     *
     * <p>退避文字は外側専用にする。埋め込むシグネチャの中には、この plugin 自身が索引時に
     * 作った未解決型が入れ子で含まれ、その中の `|` は既に {@link #UNION_ESCAPE} で退避済み。
     * 同じ文字を使うと、外側を戻したときに内側まで戻ってしまい、入れ子の型が壊れる。
     */
    private static final char OUTER_UNION_ESCAPE = 'ᨪ';
    private static final char FIELD_ESCAPE = 'ᨨ';
    private static final char SUBFIELD_ESCAPE = 'ᨩ';

    private CallSiteGenericResolver() {
    }

    /**
     * 呼び出しから、索引後に解決できる形の文字列を作る。ローカル情報しか使わない。
     */
    static String encode(FunctionReference reference) {
        StringBuilder encoded = new StringBuilder();
        if (reference instanceof MethodReference method
                && method.getClassReference() instanceof PhpTypedElement receiver) {
            // レシーバの型は索引後でないと決まらないので、シグネチャのまま持ち回る
            encoded.append(METHOD).append(escapeSignature(receiver.getType().toString())).append(FIELD);
        }
        encoded.append(reference.getName());
        for (PsiElement argument : reference.getParameters()) {
            encoded.append(FIELD);
            if (argument instanceof PhpTypedElement typed) {
                encoded.append(escapeSignature(typed.getType().toString()));
            }
            // 条件型は実引数の値 (`Shape::AS_LIST`) で分岐するため、型だけでは足りない
            encoded.append(SUBFIELD).append(escapeSignature(argument.getText()));
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
        if (encoded.charAt(0) == METHOD) {
            return methodResultOf(project, fields);
        }
        String functionName = fields.getFirst();
        List<Argument> arguments = fields.subList(1, fields.size()).stream().map(Argument::parse).toList();
        PhpType result = new PhpType();
        for (Function function : PhpIndex.getInstance(project).getFunctionsByName(functionName)) {
            PhpType resolved = returnTypeOf(project, function, arguments, Map.of());
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 関数呼び出しを、その場で (索引外で) 解く。呼び出し先の型引数が要る場面で使う。
     */
    static @Nullable PhpType resolveCallHere(FunctionReference reference) {
        if (reference.getName() == null) {
            return null;
        }
        Project project = reference.getProject();
        List<Argument> arguments = new ArrayList<>();
        for (PsiElement argument : reference.getParameters()) {
            String signature = argument instanceof PhpTypedElement typed ? typed.getType().toString() : "";
            arguments.add(new Argument(signature, argument.getText()));
        }
        PhpType result = new PhpType();
        for (Function function : PhpIndex.getInstance(project).getFunctionsByName(reference.getName())) {
            PhpType resolved = returnTypeOf(project, function, arguments, Map.of());
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * メソッド呼び出しを、その場で (索引外で) 解く。
     *
     * <p>レシーバの型引数はシグネチャを持ち回ると落ちる (実測)。`global()` で解けた型からなら取れる。
     * 索引中に呼ばれないことは呼び出し側が保証する。
     */
    static @Nullable PhpType resolveMethodHere(MethodReference reference) {
        if (!(reference.getClassReference() instanceof PhpTypedElement receiver) || reference.getName() == null) {
            return null;
        }
        Project project = reference.getProject();
        PhpType receiverType = receiver.getType().global(project);
        List<Argument> arguments = new ArrayList<>();
        for (PsiElement argument : reference.getParameters()) {
            String signature = argument instanceof PhpTypedElement typed ? typed.getType().toString() : "";
            arguments.add(new Argument(signature, argument.getText()));
        }
        PhpType result = new PhpType();
        for (String typeString : receiverType.getTypesWithParametrisedParts()) {
            int angle = typeString.indexOf('<');
            if (angle < 0) {
                continue;
            }
            for (PhpClass phpClass : PhpIndex.getInstance(project).getClassesByFQN(typeString.substring(0, angle))) {
                Method method = phpClass.findMethodByName(reference.getName());
                Map<String, String> seed = GenericTypeArguments.of(phpClass, typeString);
                if (method == null || seed.isEmpty()) {
                    continue;
                }
                PhpType resolved = returnTypeOf(project, method, arguments, seed);
                if (resolved != null) {
                    result.add(resolved);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * メソッド呼び出しの戻り値。型変数はレシーバの型引数と実引数の両方から決まる。
     *
     * <p>`$op->map($fn)` の `V` はレシーバ `Op<\App\User>` 側にある。関数呼び出しと違い、
     * 実引数だけを見ても決まらない。
     */
    private static @Nullable PhpType methodResultOf(Project project, List<String> fields) {
        String receiverSignature = unescapeSignature(fields.getFirst().substring(1));
        if (fields.size() < 2) {
            return null;
        }
        String methodName = fields.get(1);
        List<Argument> arguments = fields.subList(2, fields.size()).stream().map(Argument::parse).toList();
        PhpType receiverType = actualTypeOf(project, receiverSignature);
        PhpType result = new PhpType();
        for (String typeString : receiverType.getTypesWithParametrisedParts()) {
            int angle = typeString.indexOf('<');
            String classFqn = angle < 0 ? typeString : typeString.substring(0, angle);
            for (PhpClass phpClass : PhpIndex.getInstance(project).getClassesByFQN(classFqn)) {
                Method method = phpClass.findMethodByName(methodName);
                if (method == null) {
                    continue;
                }
                PhpType resolved = returnTypeOf(
                        project, method, arguments, GenericTypeArguments.of(phpClass, typeString));
                if (resolved != null) {
                    result.add(resolved);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static @Nullable PhpType returnTypeOf(
            Project project,
            Function function,
            List<Argument> arguments,
            Map<String, String> seed
    ) {
        Collection<String> templates =
                new java.util.LinkedHashSet<>(PhpGenericsBaseExtendedWithGenericTypeProvider.getTemplates(function));
        templates.addAll(seed.keySet());
        PhpDocComment doc = function.getDocComment();
        if (templates.isEmpty() || doc == null) {
            return null;
        }
        Parameter[] parameters = function.getParameters();
        String declaredReturn = returnExpressionOf(doc, parameters, arguments);
        if (declaredReturn == null || !declaredReturn.contains("<")) {
            // 型引数を持たない戻り値は PhpStorm が既に解いている
            return null;
        }
        Map<String, String> bindings = TemplateUnification.empty();
        bindings.putAll(seed);
        for (int i = 0; i < parameters.length && i < arguments.size(); i++) {
            String declared = PhpDocDeclaredTypeReader.typeNameFor(
                    doc, PhpDocDeclaredTypeReader.PARAM_TAGS, parameters[i].getName());
            String signature = arguments.get(i).signature();
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
        PhpType resolved = new PhpType().add(substituted);
        String plural = TemplateSubstitution.pluralFormOf(substituted);
        if (plural != null) {
            // 要素アクセスが読める形も足す。総称の形は残すので精度は落ちない
            resolved.add(plural);
        }
        return resolved;
    }

    /**
     * 戻り値の宣言を読む。条件型なら実引数を見て分岐を選ぶ。
     */
    private static @Nullable String returnExpressionOf(
            PhpDocComment doc,
            Parameter[] parameters,
            List<Argument> arguments
    ) {
        String raw = rawReturnOf(doc);
        if (ConditionalReturnType.parse(raw) == null) {
            return PhpDocDeclaredTypeReader.typeNameFor(doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
        }
        return ConditionalReturnType.resolve(raw, subject -> {
            for (int i = 0; i < parameters.length && i < arguments.size(); i++) {
                if (subject.equals(parameters[i].getName())) {
                    return arguments.get(i).text();
                }
            }
            return null;
        });
    }

    /**
     * `@phpstan-return` に書かれた本文をそのまま読む。条件型は {@code PhpDocType} にならないため、
     * 型として読もうとすると括弧の中身が落ちる。
     */
    private static @Nullable String rawReturnOf(PhpDocComment doc) {
        for (String tag : List.of("@phpstan-return", "@psalm-return")) {
            for (var element : doc.getTagElementsByName(tag)) {
                String text = element.getText();
                String body = stripDocDecoration(text.substring(text.indexOf(tag) + tag.length()));
                if (!body.isEmpty()) {
                    return body;
                }
            }
        }
        return null;
    }

    /**
     * 複数行にまたがる型から PHPDoc の飾り (行頭の `*`) を落として 1 行にする。
     * 落とさないと、入れ子の条件型が `*   ($mode is ...` の形になり読めない。
     */
    /**
     * 埋め込むシグネチャから区切り文字を退避する。他のプロバイダの未解決型 (自分のものも含む) が
     * 入れ子で現れるため、退避しないと分割位置が壊れる。
     */
    private static String escapeSignature(String signature) {
        return signature
                .replace('|', OUTER_UNION_ESCAPE)
                .replace(FIELD, FIELD_ESCAPE)
                .replace(SUBFIELD, SUBFIELD_ESCAPE);
    }

    private static String unescapeSignature(String encoded) {
        return encoded
                .replace(SUBFIELD_ESCAPE, SUBFIELD)
                .replace(FIELD_ESCAPE, FIELD)
                .replace(OUTER_UNION_ESCAPE, '|');
    }

    private static String stripDocDecoration(String body) {
        StringBuilder result = new StringBuilder();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (trimmed.endsWith("*/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 2).trim();
            }
            if (!trimmed.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(trimmed);
            }
        }
        return result.toString();
    }

    /**
     * 実引数一つ分。条件型の判定には書かれたテキストが要る。
     */
    private record Argument(String signature, String text) {
        static Argument parse(String encoded) {
            int separator = encoded.indexOf(SUBFIELD);
            return separator < 0
                    ? new Argument(unescapeSignature(encoded), "")
                    : new Argument(unescapeSignature(encoded.substring(0, separator)),
                            unescapeSignature(encoded.substring(separator + 1)));
        }
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
        if (PhpStanTypeKeywords.find(base).isPresent()) {
            // `non-empty-list` などの型キーワードはクラスではない。修飾すると存在しない名前になる
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
        List<String> parts = PhpType.splitTopLevelUnion(signature);
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
