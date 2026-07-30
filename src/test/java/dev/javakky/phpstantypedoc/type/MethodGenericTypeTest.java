package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * メソッド呼び出しでも型変数が解けることを確認する。
 *
 * <p>関数呼び出しと違い、型変数はレシーバの型引数側にある。
 */
public final class MethodGenericTypeTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            enum Shape
            {
                case AS_LIST;
                case AS_KEYED;
            }

            /** @template V */
            final class Collection
            {
                /** @return non-empty-list<V> */
                public function all(): array { return []; }

                /**
                 * @phpstan-return ($mode is Shape::AS_LIST ? non-empty-list<V> : V)
                 */
                public function pick(Shape $mode): mixed { return null; }
            }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @return Collection<V>
             */
            function collect(array $input): Collection { return new Collection(); }

            function probe(): void
            {
                $collection = collect([new User()]);
                $all = $collection->all();
                $picked = $collection->pick(Shape::AS_LIST);
                $single = $collection->pick(Shape::AS_KEYED);
            }
            """;

    public void testResolvesTemplateFromTheReceiver() {
        assertType("all", "non-empty-list<\\Probe\\User");
    }

    /**
     * メソッドの条件型も、レシーバの型引数と実引数の両方を使って決まる。
     */
    public void testEvaluatesAConditionalOnAMethod() {
        assertType("picked", "non-empty-list<\\Probe\\User");
    }

    public void testPicksTheOtherBranchOnAMethod() {
        var parts = typeOf("single");

        assertTrue("別の分岐 (要素型そのもの) が出ていない: " + parts,
                parts.stream().anyMatch(t -> t.equals("\\Probe\\User")));
    }

    private void assertType(String variableName, String expectedPrefix) {
        var parts = typeOf(variableName);

        assertTrue("期待した型が出ていない (" + expectedPrefix + "): " + parts,
                parts.stream().anyMatch(t -> t.startsWith(expectedPrefix)));
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
