package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.ForeachStatement;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * `foreach` で受ける変数の型を、回している式の型引数から取る。
 *
 * <p>`list<array{id: int}>` を回すと、要素は array shape のはず。PhpStorm は要素の型を
 * 複数形 (`X[]`) を通して取るが、shape はその形に載らず `array` まで落ちる (実測: 要素が
 * `mixed|array` になり、キーアクセスが `mixed` になる)。型引数から直接取れば形が残る。
 */
public final class PhpStanForeachValueTypeProvider implements PhpTypeProvider4 {
    @Override
    public char getKey() {
        return 'ᨱ';
    }

    @Override
    public @Nullable PhpType getType(PsiElement element) {
        if (!(element instanceof Variable variable)
                || DumbService.isDumb(element.getProject())) {
            // 回している式の型を読むので、索引の構築中には答えられない
            return null;
        }
        ForeachStatement foreach = foreachReceiving(variable);
        if (foreach == null
                || !(foreach.getArray() instanceof com.jetbrains.php.lang.psi.elements.PhpTypedElement iterated)) {
            return null;
        }
        PhpType iteratedType = iterated.getType().global(element.getProject());
        PhpType elements = new PhpType();
        for (String member : iteratedType.getTypesWithParametrisedParts()) {
            String elementType = elementTypeOf(member);
            if (elementType != null) {
                elements.add(elementType);
            }
        }
        return elements.isEmpty() ? null : elements;
    }

    @Override
    public @Nullable PhpType complete(String expression, Project project) {
        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(
            String expression, Set<String> visited, int depth, Project project) {
        return null;
    }

    /** この変数が値を受けている `foreach`。キーを受ける変数や、無関係な変数では null。 */
    private @Nullable ForeachStatement foreachReceiving(Variable variable) {
        PsiElement parent = variable.getParent();
        if (!(parent instanceof ForeachStatement foreach)) {
            return null;
        }
        for (PhpPsiElement value : foreach.getVariables()) {
            if (value == variable) {
                return foreach;
            }
        }
        return null;
    }

    /**
     * `list<X>` / `array<K, V>` / `X[]` から要素の型を取る。
     *
     * <p>shape を含む型は文字列のまま扱う。{@code PhpType} に載せ直すと形が落ちる。
     */
    private @Nullable String elementTypeOf(String type) {
        TypeUnionSimplifier.ArrayShape shape = TypeUnionSimplifier.arrayShapeOf(type);
        if (shape == null) {
            return null;
        }
        String element = shape.element().trim();
        // 形の分からない要素を足しても、PhpStorm 自身が出すものと変わらない
        return element.isEmpty() || element.equals("mixed") ? null : element;
    }
}
