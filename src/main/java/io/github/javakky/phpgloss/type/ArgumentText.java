package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.AssignmentExpression;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.ControlStatement;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.PhpSwitch;
import com.jetbrains.php.lang.psi.elements.TernaryExpression;
import com.jetbrains.php.lang.psi.elements.Try;
import com.jetbrains.php.lang.psi.elements.Variable;
import io.github.javakky.phpgloss.parser.PhpFileModel;
import io.github.javakky.phpgloss.parser.PhpFileModelCache;
import io.github.javakky.phpgloss.resolution.ClassNameResolver;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 呼び出しに書かれた実引数を、条件型の判定に使えるテキストにする。
 *
 * <p>書かれたままでは判定できない形が 2 つある。短い名前のクラス定数 (`Shape::AS_LIST`) は
 * 別 namespace の同名 enum と見分けが付かず、変数 (`$mode` / `$input`) は値が書かれていない。
 * 前者は書かれたファイルの namespace / use で完全修飾し、後者は同じ関数の中の代入を 1 段だけ辿る。
 * 辿った先は enum ケースか配列リテラルに限る。`$input is list<V>` の判定にはリテラルの中身が要り、
 * PhpStorm の型は連番のリテラルも `array<int, int>` として持つため型からは決められない。
 */
public final class ArgumentText {
    /** 書かれた場所によって指す先が変わる名前。完全修飾できない。 */
    private static final Set<String> RELATIVE_OWNERS = Set.of("self", "static", "parent", "$this");

    private ArgumentText() {
    }

    /**
     * @param argument 呼び出しに書かれた実引数、または宣言に書かれた既定値
     */
    static String of(PsiElement argument) {
        PsiElement value = argument instanceof Variable variable ? assignedCaseOf(variable) : argument;
        String text = (value == null ? argument : value).getText();
        String qualified = qualifyClassConstant(text, argument.getContainingFile());
        return qualified == null ? text : qualified;
    }

    /**
     * `Shape::AS_LIST` を、そのテキストが書かれたファイルの namespace / use で完全修飾する。
     *
     * @return `\Probe\Shape::AS_LIST` の形。クラス定数でない、または解決できないときは null
     */
    static @Nullable String qualifyClassConstant(@Nullable String text, @Nullable PsiFile file) {
        if (text == null || file == null) {
            return null;
        }
        String trimmed = text.trim();
        int separator = trimmed.indexOf("::");
        if (separator <= 0 || separator + 2 >= trimmed.length()) {
            return null;
        }
        String owner = trimmed.substring(0, separator);
        if (RELATIVE_OWNERS.contains(owner)) {
            return null;
        }
        PhpFileModel model = PhpFileModelCache.of(file);
        String resolved = new ClassNameResolver().resolve(owner, model.namespace(), model.uses());
        return resolved == null ? null : "\\" + resolved + trimmed.substring(separator);
    }

    /**
     * 実引数に書かれた配列リテラルから、その形と要素型を組み立てる。
     *
     * <p>PhpStorm は `[null, 1, 2]` を `array<int, mixed>` として持つ。要素の型が混ざると
     * `mixed` に潰れ、そこから束縛した型変数も `mixed` になる。書かれた要素はそれぞれ型が
     * 分かるので、union として組み直す。
     *
     * @return `list<null|int>` の形。配列リテラルでない、または要素の型が読めないときは null
     */
    public static @Nullable String literalArrayTypeOf(PsiElement argument, Project project) {
        return literalArrayTypeOf(argument, project, true);
    }

    /**
     * @param resolveGlobally 他のファイルを引いてよいか。索引の構築中 ({@code getType} の経路) は
     *                        false にする。索引中に他ファイルを解決すると TypeProvider の契約に
     *                        反し、例外がログに積まれる (実測: PhpStorm の索引で 18 件)
     */
    public static @Nullable String literalArrayTypeOf(
            PsiElement argument, Project project, boolean resolveGlobally) {
        PsiElement value = argument instanceof Variable variable ? assignedCaseOf(variable) : argument;
        if (!(value instanceof ArrayCreationExpression literal)) {
            return null;
        }
        Set<String> elements = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        boolean keyed = false;
        for (PsiElement child : literal.getChildren()) {
            PsiElement element = child;
            if (child instanceof ArrayHashElement hash) {
                keyed = true;
                element = hash.getValue();
                // キーの型も書かれている。`array-key` で埋めると実引数から取れた型より粗くなる
                if (hash.getKey() instanceof PhpTypedElement key) {
                    keys.addAll(typesOf(key, project, resolveGlobally));
                }
            }
            // 要素は型を持たないラッパーに包まれている (実測: PhpPsiElementImpl)。中の式を取る
            PhpTypedElement typed = element instanceof PhpTypedElement direct
                    ? direct : PsiTreeUtil.findChildOfType(element, PhpTypedElement.class);
            if (typed == null) {
                continue;
            }
            for (String type : typesOf(typed, project, resolveGlobally)) {
                // 要素そのものが読めないなら、union に混ぜると全体を粗くしてしまう
                // `#㈲…` は PhpStorm がまだ解いていない型の符号。そのまま組み立てると
                // 内部表現が型として漏れる
                if (type.isBlank() || type.equals("?") || type.endsWith("mixed")
                        || type.indexOf('#') >= 0) {
                    return null;
                }
                elements.add(type);
            }
        }
        if (elements.isEmpty()) {
            return null;
        }
        String elementType = String.join("|", elements);
        if (!keyed) {
            return "list<" + elementType + ">";
        }
        // キーが読めないなら組み立てない。粗い上界で埋めると、実引数から取れる型を上書きしてしまう
        return keys.isEmpty()
                || keys.stream().anyMatch(k -> k.isBlank() || k.endsWith("mixed") || k.indexOf('#') >= 0)
                ? null
                : "array<" + String.join("|", keys) + ", " + elementType + ">";
    }

    /** 索引中は他ファイルを引かない。ローカルに読める型だけを使う。 */
    private static java.util.Collection<String> typesOf(
            PhpTypedElement element, Project project, boolean resolveGlobally) {
        return resolveGlobally
                ? element.getType().global(project).getTypes()
                : element.getType().getTypes();
    }

    /**
     * 変数の直前に書かれた `@var` の型を、書かれたテキストのまま読む。
     *
     * <p>`@var list<positive-int>` の `positive-int` は PhpStorm の型としては `int` に潰れて残らない
     * (実測: global 型は `\array` のみ)。精密化を保つには書かれた文字列を読むしかない。
     *
     * @return 書かれた型。`@var` が無い、または代入を 1 段で辿れないときは null
     */
    static @Nullable String writtenVarTypeOf(PsiElement argument) {
        if (!(argument instanceof Variable variable)) {
            return null;
        }
        Function scope = PsiTreeUtil.getParentOfType(variable, Function.class);
        String name = variable.getName();
        if (scope == null || name == null) {
            return null;
        }
        AssignmentExpression single = null;
        for (AssignmentExpression assignment
                : PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression.class)) {
            if (assignment.getVariable() instanceof Variable left && name.equals(left.getName())) {
                if (single != null) {
                    // 複数回代入される変数は、呼び出し時点でどの @var が効いているか決められない
                    return null;
                }
                single = assignment;
            }
        }
        PsiElement statement = single == null ? null : single.getParent();
        for (PsiElement sibling = statement == null ? null : statement.getPrevSibling();
                sibling != null; sibling = sibling.getPrevSibling()) {
            if (sibling instanceof com.intellij.psi.PsiWhiteSpace) {
                continue;
            }
            if (sibling instanceof com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment doc) {
                return io.github.javakky.phpgloss.parser.PhpDocDeclaredTypeReader.unionTypeFor(
                        doc, io.github.javakky.phpgloss.parser.PhpDocDeclaredTypeReader.VAR_TAGS, name);
            }
            break;
        }
        return null;
    }

    /**
     * 変数に代入された enum ケースを 1 段だけ辿る。
     *
     * <p>関数の本体に直接書かれた代入が、呼び出しより前に 1 つだけあるときに限る。複数回の代入や
     * 分岐の中の代入では、呼び出し時点の値が決まらない。
     *
     * @return 代入されたクラス定数。辿れないときは null
     */
    private static @Nullable PsiElement assignedCaseOf(Variable variable) {
        Function scope = PsiTreeUtil.getParentOfType(variable, Function.class);
        String name = variable.getName();
        if (scope == null || name == null) {
            return null;
        }
        PsiElement assigned = null;
        for (AssignmentExpression assignment
                : PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression.class)) {
            if (!(assignment.getVariable() instanceof Variable target) || !name.equals(target.getName())) {
                continue;
            }
            if (assigned != null || !isDirectlyInBody(assignment, scope)) {
                return null;
            }
            assigned = assignment.getValue();
        }
        boolean beforeUse = assigned != null && assigned.getTextOffset() < variable.getTextOffset();
        if (!beforeUse) {
            return null;
        }
        // enum ケースは条件の値判定に、配列リテラルは値の形の判定に使う。どちらも書かれた値が
        // そのまま条件を決める。それ以外は値が読めないので辿らない
        return assigned instanceof ClassConstantReference || assigned instanceof ArrayCreationExpression
                ? assigned : null;
    }

    private static boolean isDirectlyInBody(PsiElement assignment, Function scope) {
        for (PsiElement parent = assignment.getParent();
                parent != null && parent != scope;
                parent = parent.getParent()) {
            if (parent instanceof ControlStatement || parent instanceof PhpSwitch
                    || parent instanceof Try || parent instanceof TernaryExpression
                    || parent instanceof Function) {
                return false;
            }
        }
        return true;
    }
}
