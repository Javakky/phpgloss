package io.github.javakky.phpgloss.type;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.ForeachStatement;
import com.jetbrains.php.lang.psi.elements.PhpNamedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * `foreach` で受ける変数の型を、回している式の型引数から取る。
 *
 * <p>キーを受ける変数には `array<K, V>` のキー側を与える。
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
        boolean forKey = variable.getParent() instanceof ForeachStatement keyed
                && keyed.getKey() == variable;
        ForeachStatement foreach = forKey
                ? (ForeachStatement) variable.getParent() : foreachReceiving(variable);
        if (foreach == null
                || !(foreach.getArray() instanceof com.jetbrains.php.lang.psi.elements.PhpTypedElement iterated)) {
            return null;
        }
        PhpType iteratedType = iterated.getType().global(element.getProject());
        PhpType elements = new PhpType();
        for (String member : iteratedType.getTypesWithParametrisedParts()) {
            String elementType = forKey ? keyTypeOf(member) : elementTypeOf(member);
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

    /**
     * この変数が**値**を受けている `foreach`。
     *
     * <p>`getVariables()` はキーと値の両方を返す。そちらで判定すると `$k => $v` のキーにも
     * 値の型を与えてしまう (実測: キーが `non-empty-string|array{…}` になった)。
     */
    private @Nullable ForeachStatement foreachReceiving(Variable variable) {
        return variable.getParent() instanceof ForeachStatement foreach
                && foreach.getValue() == variable
                ? foreach : null;
    }

    /**
     * `array<K, V>` からキーの型を取る。キーを持たない形 (`list<X>` / `X[]`) では、
     * 連番の添字になるので何も言わない。
     */
    private @Nullable String keyTypeOf(String type) {
        TypeUnionSimplifier.ArrayShape shape = TypeUnionSimplifier.arrayShapeOf(type);
        if (shape == null || shape.arguments().size() < 2) {
            return null;
        }
        String key = shape.arguments().getFirst().trim();
        return key.isEmpty() || key.equals("mixed") ? null : key;
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
