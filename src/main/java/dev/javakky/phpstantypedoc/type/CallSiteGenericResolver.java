package dev.javakky.phpstantypedoc.type;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    /** 「書かれたテキスト」と「@var に書かれた型」を区切る文字。精密化はテキストにしか残らない。 */
    private static final char VAR_FIELD = 'ᨬ';
    /** 名前付き引数 (`mode: Shape::AS_LIST`) の名前を区切る文字。 */
    private static final char NAME_FIELD = 'ᨭ';
    /** {@link #VAR_FIELD} と {@link #NAME_FIELD}。どちらも書かれたときだけ現れる。 */
    private static final String OPTIONAL_FIELDS = "" + VAR_FIELD + NAME_FIELD;
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

    /** 条件型が書かれるのは phpstan / psalm の拡張タグだけ。 */
    private static final List<String> EXTENDED_PARAM_TAGS = List.of("@phpstan-param", "@psalm-param");
    /** callable の形は素の `@param` にも書かれる。 */
    private static final List<String> ALL_PARAM_TAGS =
            List.of("@phpstan-param", "@psalm-param", "@param");

    private CallSiteGenericResolver() {
    }

    /**
     * 呼び出しから、索引後に解決できる形の文字列を作る。ローカル情報しか使わない。
     *
     * <p>先頭には呼び出し元の namespace を置く。同じ短い名前の関数は namespace ごとに別物なので、
     * 索引後に名前だけで引くと `\A\filter` と `\B\filter` が混ざる。
     */
    static String encode(FunctionReference reference) {
        StringBuilder encoded = new StringBuilder(namespaceOf(reference.getContainingFile()));
        encoded.append(FIELD);
        if (reference instanceof MethodReference method
                && method.getClassReference() instanceof PhpTypedElement receiver) {
            // レシーバの型は索引後でないと決まらないので、シグネチャのまま持ち回る
            encoded.append(METHOD).append(escapeSignature(receiver.getType().toString())).append(FIELD);
        }
        encoded.append(reference.getName());
        for (PsiElement argument : reference.getParameters()) {
            encoded.append(FIELD);
            if (argument instanceof PhpTypedElement typed) {
                encoded.append(escapeSignature(signatureOf(typed)));
            }
            // 条件型は実引数の値 (`Shape::AS_LIST`) で分岐するため、型だけでは足りない
            encoded.append(SUBFIELD).append(escapeSignature(ArgumentText.of(argument)));
            String written = ArgumentText.writtenVarTypeOf(argument);
            if (written != null) {
                // `@var list<positive-int>` の精密化は PhpStorm の型に残らないため、書かれた
                // テキストを運ぶ。読み取りはローカル PSI だけで済むので索引中でも行える
                encoded.append(VAR_FIELD).append(escapeSignature(written));
            }
            String name = argumentNameOf(argument);
            if (name != null) {
                encoded.append(NAME_FIELD).append(escapeSignature(name));
            }
        }
        return encoded.toString();
    }

    /**
     * 名前付き引数の名前を読む。
     *
     * <p>{@code FunctionReference#getParameters()} は名前を落として値だけを位置順に返す (実測)。
     * 名前は引数リストの中に `名前` `:` の並びで残っているので、値の手前を遡って読む。
     *
     * @return `mode: Shape::AS_LIST` の `mode`。名前付きでなければ null
     */
    private static @Nullable String argumentNameOf(PsiElement argument) {
        PsiElement colon = previousMeaningful(argument);
        if (colon == null || !":".equals(colon.getText())) {
            return null;
        }
        PsiElement name = previousMeaningful(colon);
        return name == null || name.getText().isBlank() ? null : name.getText();
    }

    private static @Nullable PsiElement previousMeaningful(PsiElement element) {
        for (PsiElement sibling = element.getPrevSibling(); sibling != null;
                sibling = sibling.getPrevSibling()) {
            if (!(sibling instanceof PsiWhiteSpace) && !(sibling instanceof PsiComment)) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * @param encoded {@link #encode} で作った文字列
     * @return 型変数を解いた戻り値の型。解けないときは null
     */
    static @Nullable PhpType decodeAndResolve(String encoded, Project project) {
        List<String> fields = split(encoded);
        if (fields.size() < 2) {
            return null;
        }
        String namespace = fields.getFirst();
        List<String> call = fields.subList(1, fields.size());
        if (!call.getFirst().isEmpty() && call.getFirst().charAt(0) == METHOD) {
            return methodResultOf(project, call);
        }
        String functionName = call.getFirst();
        List<Argument> arguments = call.subList(1, call.size()).stream().map(Argument::parse).toList();
        PhpType result = new PhpType();
        for (Function function : functionsNamed(project, functionName, namespace)) {
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
        List<Argument> arguments = argumentsOf(reference);
        PhpType result = new PhpType();
        for (Function function : functionsNamed(
                project, reference.getName(), namespaceOf(reference.getContainingFile()))) {
            PhpType resolved = returnTypeOf(project, function, arguments, Map.of());
            if (resolved != null) {
                result.add(resolved);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static List<Argument> argumentsOf(FunctionReference reference) {
        List<Argument> arguments = new ArrayList<>();
        for (PsiElement argument : reference.getParameters()) {
            String signature = argument instanceof PhpTypedElement typed ? signatureOf(typed) : "";
            arguments.add(new Argument(
                    signature,
                    ArgumentText.of(argument),
                    ArgumentText.writtenVarTypeOf(argument),
                    argumentNameOf(argument)));
        }
        return arguments;
    }

    /**
     * 実引数の型を文字列にする。
     *
     * <p>{@code PhpType#toString} は型引数を落とす (実測: `['a' => 1]` の型は
     * `\array<\string, \int>` を持っているのに `#㈲\string|int[]|?` になる)。落とすとキーの型が
     * 復元できないので、型引数を含む形で持ち回る。
     */
    private static String signatureOf(PhpTypedElement typed) {
        return String.join("|", typed.getType().getTypesWithParametrisedParts());
    }

    private static String namespaceOf(@Nullable PsiFile file) {
        if (file == null) {
            return "";
        }
        String namespace = PhpFileModelCache.of(file).namespace();
        return namespace == null ? "" : namespace;
    }

    /**
     * 同じ短い名前の関数から、呼び出し元の namespace のものを選ぶ。無ければ global 側に落ちる
     * (PHP の名前解決と同じ順序)。
     *
     * <p>どちらにも当たらないときは絞らない。`use function` で別の namespace から取り込んだ形は
     * ここでは追えないため、絞ると今まで解けていたものまで解けなくなる。
     */
    private static Collection<Function> functionsNamed(Project project, String name, String namespace) {
        Collection<Function> candidates = PhpIndex.getInstance(project).getFunctionsByName(name);
        if (candidates.size() < 2) {
            return candidates;
        }
        List<Function> local = withFqn(candidates, fqnOf(namespace, name));
        if (!local.isEmpty()) {
            return local;
        }
        List<Function> global = withFqn(candidates, fqnOf("", name));
        return global.isEmpty() ? candidates : global;
    }

    private static List<Function> withFqn(Collection<Function> candidates, String fqn) {
        return candidates.stream().filter(function -> fqn.equalsIgnoreCase(function.getFQN())).toList();
    }

    private static String fqnOf(String namespace, String name) {
        return namespace.isBlank() ? "\\" + name : "\\" + namespace + "\\" + name;
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
        return methodResultOf(
                project,
                receiver.getType().global(project),
                reference.getName(),
                argumentsOf(reference));
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
        List<Argument> arguments = fields.subList(2, fields.size()).stream().map(Argument::parse).toList();
        return methodResultOf(
                project, actualTypeOf(project, receiverSignature), fields.get(1), arguments);
    }

    /**
     * レシーバの型からメソッドの戻り値を組み立てる。
     *
     * <p>型引数を持たないレシーバも通す。`Foo::bar(...)` の静的呼び出しはレシーバが素のクラス名で、
     * 型引数を持ちようがない (実測: `classReference` は `ClassReference` で型は `\Probe\Holder`)。
     * 型変数はメソッド自身の `@template` から来て、実引数だけで決まる。
     */
    private static @Nullable PhpType methodResultOf(
            Project project,
            PhpType receiverType,
            String methodName,
            List<Argument> arguments
    ) {
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
        PhpDocComment doc = function.getDocComment();
        if (doc == null) {
            return null;
        }
        Collection<String> templates =
                new LinkedHashSet<>(PhpGenericsBaseExtendedWithGenericTypeProvider.getTemplates(function));
        // 変性付きの宣言 (`@template-covariant`) は PhpStorm 側の一覧に載らない
        templates.addAll(GenericTypeArguments.templates(doc).keySet());
        templates.addAll(seed.keySet());
        if (templates.isEmpty()) {
            return null;
        }
        Parameter[] parameters = function.getParameters();
        List<Argument> byParameter = alignedArguments(parameters, arguments);
        CallSite callSite = new CallSite(project, function, parameters, byParameter);
        String declaredReturn = returnExpressionOf(doc, callSite);
        if (declaredReturn == null || !mentionsTemplate(declaredReturn, templates)) {
            // 型変数を含まない戻り値は PhpStorm が既に解いている
            return null;
        }
        Map<String, String> bindings = TemplateUnification.empty();
        bindings.putAll(seed);
        for (int i = 0; i < parameters.length; i++) {
            Argument argument = byParameter.get(i);
            if (argument == null) {
                continue;
            }
            String parameterName = parameters[i].getName();
            // `callable(V, K): E` の戻り位置は union として読むと `:` の手前で切れる (実測)。
            // callable の形は書かれたまま読んで、渡されたクロージャと突き合わせる
            CallableUnification.unify(
                    project,
                    rawParamOf(doc, parameterName, ALL_PARAM_TAGS),
                    argument.signature(),
                    templates,
                    bindings);
            String declared = declaredParamOf(doc, callSite, parameterName);
            if (declared == null) {
                continue;
            }
            String written = argument.writtenVarType();
            if (written != null) {
                // `@var list<positive-int>` の精密化は PhpStorm の型には残らない (int に潰れる)。
                // 書かれた型どうしを構造で照合して先に束縛する
                TemplateUnification.unifyText(declared, written, templates, bindings);
            }
            if (argument.signature().isBlank()) {
                continue;
            }
            TemplateUnification.unify(
                    declared, actualTypeOf(project, argument.signature()), templates, bindings);
        }
        fillFromBounds(doc, templates, bindings);
        if (bindings.isEmpty()) {
            return null;
        }
        // 置換を先に行う。修飾が先だと裸の型変数 `V` が `\Probe\V` に化け、
        // 未解決の検出 (語としての `V` を探す) をすり抜けて型として漏れる
        String substituted = PhpType.splitTopLevelUnion(declaredReturn).stream()
                .map(part -> qualify(TemplateSubstitution.apply(part.trim(), bindings), function))
                .reduce((a, b) -> a + "|" + b)
                .orElse(null);
        if (substituted == null) {
            return null;
        }
        if (TemplateSubstitution.hasUnresolved(substituted, templates)) {
            // 型変数が残ったままの型は出さない。読み手に意味がないため
            return null;
        }
        // `positive-int|null` を 1 成分のまま add すると union として扱われない。分けて足す
        PhpType resolved = new PhpType();
        for (String part : PhpType.splitTopLevelUnion(substituted)) {
            resolved.add(part);
            String plural = TemplateSubstitution.pluralFormOf(part);
            if (plural != null) {
                // 要素アクセスが読める形も足す。総称の形は残すので精度は落ちない
                resolved.add(plural);
            }
        }
        return resolved;
    }

    /**
     * 実引数を仮引数の位置へ並べ替える。
     *
     * <p>名前付き引数は書かれた位置と対応しない。`init($input, mode: Shape::AS_LIST)` の
     * `mode:` を位置で読むと第 2 仮引数へ当ててしまい、たまたま当たっているだけになる。
     * 名前の付いた実引数は名前で、付いていない実引数は先頭からの位置で対応させ、
     * 対応先が決められないものは捨てる。誤った位置へ束縛すると条件型が別の分岐を選ぶ。
     *
     * @return 仮引数と同じ長さの一覧。渡されなかった位置は null
     */
    private static List<Argument> alignedArguments(Parameter[] parameters, List<Argument> arguments) {
        List<Argument> aligned = new ArrayList<>(Collections.nCopies(parameters.length, null));
        int position = 0;
        for (Argument argument : arguments) {
            int index = argument.name() == null
                    ? position++
                    : indexOfParameter(parameters, argument.name());
            if (index >= 0 && index < aligned.size() && aligned.get(index) == null) {
                aligned.set(index, argument);
            }
        }
        return aligned;
    }

    private static int indexOfParameter(Parameter[] parameters, String name) {
        for (int i = 0; i < parameters.length; i++) {
            if (name.equals(parameters[i].getName())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 実引数から決められなかった型変数を、`@template K of array-key` の上界で埋める。
     *
     * <p>埋めないとその型変数を含む分岐が丸ごと出せない。上界が書かれていない型変数は埋めず、
     * 従来どおり型を出さない。
     */
    private static void fillFromBounds(
            PhpDocComment doc, Collection<String> templates, Map<String, String> bindings) {
        Map<String, String> declared = GenericTypeArguments.templates(doc);
        for (String template : templates) {
            String bound = declared.get(template);
            if (bound != null && !bound.isBlank()) {
                bindings.putIfAbsent(template, bound);
            }
        }
    }

    /**
     * 引数の宣言を読む。
     *
     * <p>`@phpstan-param` にも条件型が書かれていることがある (実物の `init` がそう)。条件式は
     * 「どの型を受け付けるか」を書いたもので、そのままでは型変数を決める形になっていない。
     * 戻り値と同じ実引数で畳んでから使い、畳めないときだけ素の `@param` に落とす。
     */
    private static @Nullable String declaredParamOf(
            PhpDocComment doc,
            CallSite callSite,
            String parameterName
    ) {
        String raw = rawParamOf(doc, parameterName, EXTENDED_PARAM_TAGS);
        if (raw != null && ConditionalReturnType.parse(raw) != null) {
            String folded = ConditionalReturnType.resolve(raw, callSite);
            if (folded != null) {
                return folded;
            }
        }
        String declared = PhpDocDeclaredTypeReader.unionTypeFor(
                doc, PhpDocDeclaredTypeReader.PARAM_TAGS, parameterName);
        if (declared == null || !(declared.trim().startsWith("(") || declared.contains(" is "))) {
            return declared;
        }
        return PhpDocDeclaredTypeReader.unionTypeFor(doc, List.of("@param"), parameterName);
    }

    /**
     * `@phpstan-param` に書かれた本文をそのまま読む。条件型は {@code PhpDocType} にならない。
     */
    private static @Nullable String rawParamOf(PhpDocComment doc, String parameterName, List<String> tags) {
        for (String tag : tags) {
            for (var element : doc.getTagElementsByName(tag)) {
                String text = element.getText();
                if (!text.contains("$" + parameterName)) {
                    continue;
                }
                String body = stripDocDecoration(text.substring(text.indexOf(tag) + tag.length()));
                int variable = body.indexOf("$" + parameterName);
                String type = (variable < 0 ? body : body.substring(0, variable)).trim();
                if (!type.isEmpty()) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * 条件型を畳むための呼び出し側の情報。
     *
     * @param parameters 呼び出し先の仮引数。書かれなかった実引数を既定値で埋めるのに使う
     * @param arguments  仮引数の位置に並べ替えた実引数。渡されなかった位置は null
     */
    private record CallSite(
            Project project,
            Function function,
            Parameter[] parameters,
            List<Argument> arguments
    ) implements ConditionalReturnType.CallSite {

        @Override
        public @Nullable ConditionalArgument argumentOf(String parameterName) {
            for (int i = 0; i < parameters.length; i++) {
                if (!parameterName.equals(parameters[i].getName())) {
                    continue;
                }
                Argument argument = arguments.get(i);
                return argument == null
                        ? defaultArgumentOf(parameters[i])
                        : conditionArgumentOf(argument.signature(), argument.text());
            }
            return null;
        }

        @Override
        public @Nullable String qualifyDeclared(String classConstant) {
            return ArgumentText.qualifyClassConstant(classConstant, function.getContainingFile());
        }

        /**
         * 書かれなかった実引数は、宣言の既定値で埋める。`init($input)` の `$mode` は
         * `Shape::AUTO` として条件に回る。
         */
        private @Nullable ConditionalArgument defaultArgumentOf(Parameter parameter) {
            PsiElement value = parameter.getDefaultValue();
            if (value == null) {
                return null;
            }
            String signature = value instanceof PhpTypedElement typed ? signatureOf(typed) : "";
            // 既定値は宣言側のファイルに書かれているので、修飾も宣言側の namespace で行われる
            return conditionArgumentOf(signature, ArgumentText.of(value));
        }

        private ConditionalArgument conditionArgumentOf(String signature, String text) {
            return signature.isBlank()
                    ? ConditionalArgument.ofText(text)
                    : new ConditionalArgument(text, () -> actualTypeOf(project, signature));
        }
    }

    /**
     * 戻り値の宣言を読む。条件型なら実引数を見て分岐を選ぶ。
     */
    private static @Nullable String returnExpressionOf(PhpDocComment doc, CallSite callSite) {
        String raw = rawReturnOf(doc);
        if (ConditionalReturnType.parse(raw) != null) {
            return ConditionalReturnType.resolve(raw, callSite);
        }
        if (raw != null) {
            // `@return V|null 説明文` の union を丸ごと読む。PhpDocType 経由 (typeNameFor) は
            // union の先頭しか返さず、`|null` が落ちる (実測: Option 型の null が消えた)
            return firstTypeTokenOf(raw);
        }
        return PhpDocDeclaredTypeReader.unionTypeFor(doc, PhpDocDeclaredTypeReader.RETURN_TAGS, null);
    }

    /**
     * 型の直後に説明文が続く形から、型だけを取り出す。型引数の中の空白は区切りにしない。
     */
    private static String firstTypeTokenOf(String body) {
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '<' || c == '{' || c == '(') {
                depth++;
            } else if (c == '>' || c == '}' || c == ')') {
                depth--;
            } else if (depth == 0 && Character.isWhitespace(c)) {
                return body.substring(0, i);
            }
        }
        return body;
    }

    /**
     * `@phpstan-return` に書かれた本文をそのまま読む。条件型は {@code PhpDocType} にならないため、
     * 型として読もうとすると括弧の中身が落ちる。
     */
    private static @Nullable String rawReturnOf(PhpDocComment doc) {
        for (String tag : List.of("@phpstan-return", "@psalm-return", "@return")) {
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
     *
     * @param name 名前付き引数として書かれた名前。位置で渡されたなら null
     */
    private record Argument(
            String signature,
            String text,
            @Nullable String writtenVarType,
            @Nullable String name
    ) {
        Argument(String signature, String text) {
            this(signature, text, null, null);
        }

        static Argument parse(String encoded) {
            int separator = encoded.indexOf(SUBFIELD);
            if (separator < 0) {
                return new Argument(unescapeSignature(encoded), "");
            }
            String signature = unescapeSignature(encoded.substring(0, separator));
            String rest = encoded.substring(separator + 1);
            String text = "";
            String writtenVarType = null;
            String name = null;
            char field = 0;
            int start = 0;
            for (int i = 0; i <= rest.length(); i++) {
                if (i < rest.length() && OPTIONAL_FIELDS.indexOf(rest.charAt(i)) < 0) {
                    continue;
                }
                String value = unescapeSignature(rest.substring(start, i));
                if (field == VAR_FIELD) {
                    writtenVarType = value;
                } else if (field == NAME_FIELD) {
                    name = value;
                } else {
                    text = value;
                }
                if (i == rest.length()) {
                    break;
                }
                field = rest.charAt(i);
                start = i + 1;
            }
            return new Argument(signature, text, writtenVarType, name);
        }
    }

    /**
     * 戻り値の宣言に型変数が語として現れるか。現れないなら PhpStorm の解決に任せる。
     */
    private static boolean mentionsTemplate(String declared, Collection<String> templates) {
        for (String template : templates) {
            int at = declared.indexOf(template);
            while (at >= 0) {
                boolean leftFree = at == 0 || !Character.isLetterOrDigit(declared.charAt(at - 1));
                int right = at + template.length();
                boolean rightFree = right >= declared.length()
                        || !(Character.isLetterOrDigit(declared.charAt(right)) || declared.charAt(right) == '_');
                if (leftFree && rightFree) {
                    return true;
                }
                at = declared.indexOf(template, at + 1);
            }
        }
        return false;
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
    static PhpType actualTypeOf(Project project, String signature) {
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
