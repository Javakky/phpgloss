package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 静的メソッド呼び出しでも型変数が解ける。
 *
 * <p>`Foo::bar()` のレシーバは素のクラス参照で、型引数を持ちようがない (probe で実測:
 * `classReference` は `ClassReference`、型は `\Probe\Factory`)。型引数を持つレシーバだけを
 * 通していた頃はここで諦めていた。型変数はメソッド自身の `@template` から来る。
 */
public final class StaticMethodGenericTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            final class Factory
            {
                /**
                 * @template V
                 * @param list<V> $input
                 * @return non-empty-list<V>
                 */
                public static function wrap(array $input): array { return $input; }

                /**
                 * @template V
                 * @template E
                 * @param list<V> $input
                 * @param callable(V): E $callback
                 * @return non-empty-list<E>
                 */
                public static function convert(array $input, callable $callback): array { return []; }
            }

            function probe(): void
            {
                $wrapped = Factory::wrap([new User()]);
                $converted = Factory::convert([1, 2, 3], fn (int $v): User => new User());
            }
            """;

    public void testResolvesATemplateOnAStaticCall() {
        assertResolves("wrapped");
    }

    /**
     * 静的メソッドでもコールバックの戻り値から型変数が決まる。
     */
    public void testResolvesTheCallbackResultOnAStaticCall() {
        assertResolves("converted");
    }

    private void assertResolves(String variableName) {
        var parts = typeOf(variableName);

        assertTrue("静的メソッドの型変数が解けていない: " + parts,
                parts.contains("non-empty-list<\\Probe\\User>"));
        assertFalse("解けたのに粗い型が混ざっている: " + parts,
                parts.contains("mixed") || parts.equals("?"));
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
