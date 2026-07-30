package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * ジェネリッククラスの型引数が `__invoke` の戻り値へ伝わることを確認する。
 *
 * <p>PhpStorm 単体では `$op()` が素の `array` になり、要素型も list である構造も失われる
 * (YouTrack WI-65938、2022 年起票・未解決)。
 */
public final class InvokeGenericTypeTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            /** @template V */
            final class Op
            {
                /** @return non-empty-list<V> */
                public function __invoke(): array { return []; }
            }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @return Op<V>
             */
            function opOf(array $input): Op { return new Op(); }

            function probe(): void
            {
                $op = opOf([new User()]);
                $direct = $op();
                $chained = opOf([new User()])();
            }
            """;

    public void testPropagatesTypeArgumentThroughInvoke() {
        assertResolves("direct");
    }

    public void testPropagatesThroughAChainedCall() {
        assertResolves("chained");
    }

    private void assertResolves(String variableName) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();

        var type = ((PhpTypedElement) variable).getType().global(getProject());
        var parts = type.getTypesWithParametrisedParts();

        assertTrue("要素型が伝わっていない: " + parts,
                parts.stream().anyMatch(t -> t.contains("\\Probe\\User")));
        assertTrue("list である構造が失われている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<")));
    }
}
