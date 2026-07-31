package dev.javakky.phpstantypedoc.doc;

import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.AssignmentExpression;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import dev.javakky.phpstantypedoc.type.AliasReturnType;
import dev.javakky.phpstantypedoc.type.ArgumentText;
import dev.javakky.phpstantypedoc.type.ResolvedCallType;
import dev.javakky.phpstantypedoc.type.TypeUnionSimplifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * エイリアスに当たらない変数のホバー。global 型の union から、他の型に呑まれているものを落として出す。
 *
 * <p>PHP 標準の表示は解けた型と抽象型を並べる (実測: `$result: array|int[]|int[]|list<int>`)。
 * union の成分はプロバイダ側から消せないため、表示のときに整理する。整理して減らないときは target を
 * 出さず、PHP 標準の表示に任せる。
 */
final class SimplifiedVariableTypeTarget implements DocumentationTarget {
    private final PhpNamedElement variable;
    private final List<String> types;
    /** 型の中に現れるエイリアス。名前をリンクにするために持つ。無いときは null。 */
    private final @Nullable AliasedType aliased;
    private final PhpStanTypeHtmlRenderer renderer = new PhpStanTypeHtmlRenderer();

    private SimplifiedVariableTypeTarget(PhpNamedElement variable, List<String> types) {
        this(variable, types, null);
    }

    private SimplifiedVariableTypeTarget(
            PhpNamedElement variable, List<String> types, @Nullable AliasedType aliased) {
        this.variable = variable;
        this.types = types;
        this.aliased = aliased;
    }

    /**
     * 代入元のメソッドが戻り値にエイリアスを書いているときの、宣言のままの型。
     *
     * <p>本体は並べない。名前がその宣言へのリンクになっていれば、そこから辿れる (実機で指摘)。
     */
    private record AliasedType(String declared, String ownerFqn, List<String> aliasNames) {
    }

    private static Optional<AliasedType> aliasedFromAssignment(PhpNamedElement variable) {
        if (!(variable instanceof Variable)) {
            return Optional.empty();
        }
        PsiElement value = variable.getParent() instanceof AssignmentExpression assignment
                && assignment.getVariable() == variable ? assignment.getValue() : null;
        if (!(value instanceof MethodReference call)) {
            return Optional.empty();
        }
        for (var resolved : call.multiResolve(false)) {
            if (!(resolved.getElement() instanceof Method method)) {
                continue;
            }
            List<String> names = AliasReturnType.aliasNamesOf(method);
            String declared = AliasReturnType.declaredTextOf(method).orElse(null);
            if (names.isEmpty() || declared == null) {
                continue;
            }
            PhpClass owner = method.getContainingClass();
            if (owner != null && owner.getFQN() != null) {
                return Optional.of(new AliasedType(declared, owner.getFQN(), names));
            }
        }
        return Optional.empty();
    }

    static Optional<SimplifiedVariableTypeTarget> at(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        PhpNamedElement variable = PsiTreeUtil.getParentOfType(element, PhpNamedElement.class, false);
        if (!(variable instanceof Variable) && !(variable instanceof Parameter)) {
            return Optional.empty();
        }
        // この plugin の解決器が代入右辺の分岐を確定させているなら、その型が値の精密化で、
        // union の他の成分は同じ値の粗い近似 (選択肢ではない) と分かる。表示はそれに固める
        // 書かれたリテラルから読める型は、推論より確か。PhpStorm はキーがクラス定数だと
        // 整数キーの配列と見る (実測: `[Region::NORTH => [...]]` が `array<int, string>[]`)
        List<String> written = writtenFromLiteral(variable);
        if (!written.isEmpty()) {
            return Optional.of(new SimplifiedVariableTypeTarget(variable, written));
        }
        // 戻り値にエイリアスが書かれている呼び出しは、開いた形をそのまま並べると読みにくい
        // (実機で指摘)。書かれた名前を残し、本体はメソッドのホバーと同じ `(名前: 本体)` で添える
        Optional<AliasedType> aliased = aliasedFromAssignment(variable);
        if (aliased.isPresent()) {
            return Optional.of(new SimplifiedVariableTypeTarget(
                    variable, List.of(aliased.get().declared()), aliased.get()));
        }
        List<String> resolved = resolvedFromAssignment(variable);
        if (!resolved.isEmpty()) {
            // 解決器の束縛は PhpStorm の定数追跡 (`int<20,20>`) より粗いことがある。
            // 分岐が確定しているなら union は全部同じ値の記述なので、同じ構造の精密な成分に置き換える
            List<String> refined = TypeUnionSimplifier.refineAgainst(
                    resolved, variable.getType().global(file.getProject()).getTypesWithParametrisedParts());
            return Optional.of(new SimplifiedVariableTypeTarget(variable, refined));
        }
        return TypeUnionSimplifier.simplify(variable.getType().global(file.getProject()).getTypesWithParametrisedParts())
                .map(simplified -> new SimplifiedVariableTypeTarget(variable, simplified));
    }

    /**
     * 変数に代入された配列リテラルから読める型。読めないときは空。
     *
     * <p>リテラルは書かれたものそのものなので、推論より確か。ただし要素やキーが読めない
     * リテラルでは何も返さない (組み立てを諦める判断は {@code literalArrayTypeOf} が持つ)。
     */
    private static List<String> writtenFromLiteral(PhpNamedElement variable) {
        if (!(variable instanceof Variable)) {
            return List.of();
        }
        // 代入の左辺そのものを指しているときは、その右辺が値になる (辿る対象は「使う側」の変数)
        PsiElement source = variable;
        if (variable.getParent() instanceof AssignmentExpression assignment
                && assignment.getVariable() == variable && assignment.getValue() != null) {
            source = assignment.getValue();
        }
        String written = ArgumentText.literalArrayTypeOf(source, variable.getProject());
        if (written == null) {
            return List.of();
        }
        List<String> normalized = TypeUnionSimplifier.normalize(List.of(written));
        // PhpStorm 自身が同じ型に辿り着いているなら、割り込む必要はない
        List<String> current = TypeUnionSimplifier.normalize(
                variable.getType().global(variable.getProject()).getTypesWithParametrisedParts());
        return normalized.equals(current) ? List.of() : normalized;
    }

    /**
     * 変数が関数内で一度だけ、呼び出しの結果を代入されている場合に、その呼び出しを
     * この plugin の解決器で解いた型を返す。複数回代入される変数はどの値か決められないので使わない。
     */
    private static List<String> resolvedFromAssignment(PhpNamedElement variable) {
        if (!(variable instanceof Variable) && !(variable instanceof Parameter)) {
            return List.of();
        }
        Function enclosing = PsiTreeUtil.getParentOfType(variable, Function.class, false);
        if (enclosing == null || variable.getName() == null) {
            return List.of();
        }
        AssignmentExpression single = null;
        for (AssignmentExpression assignment
                : PsiTreeUtil.findChildrenOfType(enclosing, AssignmentExpression.class)) {
            if (assignment.getVariable() instanceof Variable left
                    && variable.getName().equals(left.getName())) {
                if (single != null) {
                    return List.of();
                }
                single = assignment;
            }
        }
        if (single == null || !(single.getValue() instanceof FunctionReference call)) {
            return List.of();
        }
        return ResolvedCallType.of(call)
                .map(type -> {
                    var parts = type.getTypesWithParametrisedParts();
                    // 自分が出した型どうしは重複記述と分かっている (複数形は要素アクセス用に
                    // 自分で足したもの) ので、ここは包摂まで整理してよい
                    return TypeUnionSimplifier.simplify(parts)
                            .orElseGet(() -> TypeUnionSimplifier.normalize(parts));
                })
                .orElse(List.of());
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        return Pointer.fileRangePointer(
                variable.getContainingFile(),
                variable.getTextRange(),
                (file, range) -> at(file, range.getStartOffset()).orElse(null));
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        return TargetPresentation.builder(name()).presentation();
    }

    @Override
    public @Nullable Navigatable getNavigatable() {
        return variable;
    }

    @Override
    public @Nullable String computeDocumentationHint() {
        return name() + ": " + typeText();
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        // PHP 標準の HTML を土台にし、型の部分だけを整理後の型に差し替える。丸ごと自前で
        // 組み直すと、名前空間のリンクや Source 行の体裁が失われる (実機で指摘を受けた)
        String base = standardHtml();
        String patched = base == null ? null : replaceTypeText(base);
        if (patched != null) {
            return DocumentationResult.documentation(patched);
        }
        return DocumentationResult.documentation(renderer.renderVariableType(
                variable.getName(), linkedTypeText(), variable.getContainingFile().getName()));
    }

    private @Nullable String standardHtml() {
        DocumentationProvider provider =
                LanguageDocumentation.INSTANCE.forLanguage(variable.getLanguage());
        return provider == null ? null : provider.generateDoc(variable, variable);
    }

    /**
     * `$name: <型>` の型の部分を差し替える。
     *
     * <p>標準の HTML では変数名が色付きの {@code <span>} に包まれるため、`$name:` の文字列一致では
     * 見つからない (実機でフォールバックに落ち、リンクも色も失われた)。名前の出現位置から
     * タグを飛ばしながら本文の `:` を探し、そこから定義ブロックの閉じまでを差し替える。
     */
    private @Nullable String replaceTypeText(String html) {
        int at = html.indexOf(name());
        if (at < 0) {
            at = html.indexOf(variable.getName());
            if (at < 0) {
                return null;
            }
        }
        int colon = textIndexOf(html, ':', at);
        if (colon < 0) {
            return null;
        }
        int end = html.indexOf("</pre>", colon);
        if (end < 0) {
            end = html.indexOf("</div>", colon);
        }
        if (end < 0) {
            return null;
        }
        return html.substring(0, colon + 1) + " " + linkedTypeText() + html.substring(end);
    }

    /**
     * タグの中 (`style="color:#..."` の `:` 等) を飛ばして、本文としての文字を探す。
     */
    private int textIndexOf(String html, char target, int from) {
        boolean inTag = false;
        for (int i = from; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag && c == target) {
                return i;
            }
        }
        return -1;
    }

    private String linkedTypeText() {
        String linked = new PhpStanTypeBodyLinkifier(variable.getProject(), null)
                .linkify(escape(typeText()));
        return aliased == null ? linked : withAliasLinks(linked);
    }

    /** 型の中のエイリアス名を、その宣言へのリンクにする。 */
    private String withAliasLinks(String html) {
        String result = html;
        for (String name : aliased.aliasNames()) {
            String url = PhpStanTypeDocumentationLink.forAlias(aliased.ownerFqn(), name);
            result = result.replaceAll("\\b" + java.util.regex.Pattern.quote(name) + "\\b",
                    java.util.regex.Matcher.quoteReplacement("<a href='" + url + "'>" + name + "</a>"));
        }
        return result;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    PsiElement contextElement() {
        return variable;
    }

    private String name() {
        return "$" + variable.getName();
    }

    String typeText() {
        return String.join("|", types);
    }
}
