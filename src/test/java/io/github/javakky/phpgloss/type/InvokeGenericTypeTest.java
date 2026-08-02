package io.github.javakky.phpgloss.type;

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

            /** @template V */
            final class PsalmOp
            {
                /** @psalm-return non-empty-list<V> */
                public function __invoke(): array { return []; }
            }

            /**
             * @template V
             * @param non-empty-list<V> $input
             * @return PsalmOp<V>
             */
            function psalmOpOf(array $input): PsalmOp { return new PsalmOp(); }

            function probe(): void
            {
                $op = opOf([new User()]);
                $direct = $op();
                $chained = opOf([new User()])();
                $element = $chained[0];
                $psalm = psalmOpOf([new User()])();
            }
            """;

    public void testPropagatesTypeArgumentThroughInvoke() {
        assertResolves("direct");
    }

    public void testPropagatesThroughAChainedCall() {
        assertResolves("chained");
    }

    /**
     * 戻り値が `@psalm-return` だけで書かれている場合。PHPDoc のテキストからは読まないが、
     * PhpStorm 自身はエンコード形の型変数として持っている。内部の置換 API で埋める。
     *
     * <p>この経路では `non-empty-list` である構造は PhpStorm 側の正規化で失われる。取り戻せる
     * のは要素型まで。
     */
    public void testPropagatesThroughAPsalmAnnotatedInvoke() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "psalm".equals(v.getName()))
                .findFirst().orElseThrow();

        var parts = ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();

        assertTrue("要素型が伝わっていない: " + parts,
                parts.stream().anyMatch(t -> t.contains("\\Probe\\User")));
    }

    /**
     * 要素アクセスまで通ることを見る。型自体が合っていても、要素型が一段前の型になっていた
     * (実機で `$f[0]` が `Op<User>` になっていた)。
     */
    public void testElementAccessYieldsTheElementType() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> "element".equals(v.getName()))
                .findFirst().orElseThrow();

        var parts = ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();

        var chainedParts = ((PhpTypedElement) PsiTreeUtil.findChildrenOfType(file, Variable.class).stream().filter(v -> "chained".equals(v.getName())).findFirst().orElseThrow()).getType().global(getProject()).getTypesWithParametrisedParts();
        assertTrue("要素型が User でない: " + parts + " / chained=" + chainedParts, parts.contains("\\Probe\\User"));
        // PhpStorm 自身も `Op<User>` を混ぜてくる。`interceptsNativeSignature` を入れても
        // 消えないことを 2 度実測したので、正しい型を足すところまでを保証する
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
