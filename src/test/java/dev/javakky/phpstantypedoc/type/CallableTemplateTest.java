package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * `@param callable(V, K): E $callback` の型変数を、渡されたクロージャから決める。
 *
 * <p>クロージャの型は `\Closure<#ᤓvᤓ\int,\string>` の形で見える (probe で実測)。引数の位置には
 * **書かれた**型宣言だけが載り、戻り位置には書かれた宣言か、無ければ PhpStorm が推論した型が載る。
 */
public final class CallableTemplateTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            /**
             * @template V
             * @template E
             * @param list<V> $input
             * @param callable(V): E $callback
             * @return list<E>
             */
            function convert(array $input, callable $callback): array { return []; }

            /**
             * @template V
             * @param callable(V): string $callback
             * @return non-empty-list<V>
             */
            function seed(callable $callback): array { return []; }

            function probe(): void
            {
                $declared = convert([1, 2, 3], fn (int $v): string => (string) $v);
                $block = convert([1, 2, 3], function (int $v): string { return (string) $v; });
                $inferred = convert([1, 2, 3], fn (int $v) => new User());
                $fromString = convert([1, 2, 3], 'strval');
                $firstClass = convert([1, 2, 3], strval(...));
                $fromParameter = seed(fn (User $v): string => 'x');
                $withoutDeclaration = seed(fn ($v): string => 'x');
            }
            """;

    /**
     * クロージャに書かれた戻り型宣言から `E` が決まる。
     */
    public void testBindsTheReturnTemplateFromADeclaredReturnType() {
        assertResolves("declared", "list<\\string>");
    }

    /**
     * アロー関数でなくても同じ。`function () {}` 形のクロージャも型としては同じ形で見える。
     */
    public void testBindsFromABlockClosureToo() {
        assertResolves("block", "list<\\string>");
    }

    /**
     * 戻り型が書かれていなければ PhpStorm が推論した型を使う。
     */
    public void testFallsBackToTheInferredReturnType() {
        assertResolves("inferred", "list<\\Probe\\User>");
    }

    /**
     * 文字列の callable からは戻り型が決められない。無理に解かず、型変数を残したまま黙る。
     */
    public void testDoesNotBindFromAStringCallable() {
        assertUnresolved("fromString");
    }

    /**
     * 第一級 callable 構文の型は `\Closure` だけで、引数も戻り値も持たない (実測)。
     */
    public void testDoesNotBindFromAFirstClassCallable() {
        assertUnresolved("firstClass");
    }

    /**
     * 戻り位置に無い型変数も、クロージャの引数に書かれた型宣言から決まる。
     */
    public void testBindsFromTheClosureParameterDeclaration() {
        assertResolves("fromParameter", "non-empty-list<\\Probe\\User>");
    }

    /**
     * 引数の型宣言が書かれていなければ決めない。推論には頼らない。
     */
    public void testDoesNotBindFromAnUndeclaredClosureParameter() {
        assertUnresolved("withoutDeclaration");
    }

    private void assertResolves(String variableName, String expected) {
        var parts = typeOf(variableName);

        assertTrue("期待した型が出ていない (" + expected + "): " + parts, parts.contains(expected));
    }

    private void assertUnresolved(String variableName) {
        var parts = typeOf(variableName);

        assertFalse("決められないはずの型変数が解けている: " + parts,
                parts.stream().anyMatch(t -> t.contains("<")));
    }

    private Set<String> typeOf(String variableName) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
