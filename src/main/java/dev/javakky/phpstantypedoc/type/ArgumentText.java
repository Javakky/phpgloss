package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.AssignmentExpression;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.ControlStatement;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.PhpSwitch;
import com.jetbrains.php.lang.psi.elements.TernaryExpression;
import com.jetbrains.php.lang.psi.elements.Try;
import com.jetbrains.php.lang.psi.elements.Variable;
import dev.javakky.phpstantypedoc.parser.PhpFileModel;
import dev.javakky.phpstantypedoc.parser.PhpFileModelCache;
import dev.javakky.phpstantypedoc.resolution.ClassNameResolver;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 呼び出しに書かれた実引数を、条件型の判定に使えるテキストにする。
 *
 * <p>書かれたままでは判定できない形が 2 つある。短い名前のクラス定数 (`Shape::AS_LIST`) は
 * 別 namespace の同名 enum と見分けが付かず、変数 (`$mode`) は値が書かれていない。前者は
 * 書かれたファイルの namespace / use で完全修飾し、後者は同じ関数の中の代入を 1 段だけ辿る。
 */
final class ArgumentText {
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
        for (AssignmentExpression assignment
                : PsiTreeUtil.findChildrenOfType(scope, AssignmentExpression.class)) {
            if (!(assignment.getVariable() instanceof Variable left) || !name.equals(left.getName())) {
                continue;
            }
            PsiElement statement = assignment.getParent();
            for (PsiElement sibling = statement == null ? null : statement.getPrevSibling();
                    sibling != null; sibling = sibling.getPrevSibling()) {
                if (sibling instanceof com.intellij.psi.PsiWhiteSpace) {
                    continue;
                }
                if (sibling instanceof com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment doc) {
                    return dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader.typeNameFor(
                            doc, dev.javakky.phpstantypedoc.parser.PhpDocDeclaredTypeReader.VAR_TAGS, name);
                }
                break;
            }
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
        return beforeUse && assigned instanceof ClassConstantReference ? assigned : null;
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
