package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 変性付きの型変数宣言 (`@template-covariant` / `@template-contravariant`) も、素の
 * `@template` と同じに読む。変性は部分型付けの規則であって、型引数の対応付けは変わらない。
 */
public final class TemplateVarianceTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            /**
             * @template K of array-key
             * @template-covariant V
             */
            final class Box
            {
                /** @return non-empty-list<V> */
                public function all(): array { return []; }
            }

            /**
             * @template-covariant V of \\Probe\\User
             * @return non-empty-list<V>
             */
            function covariant(): array { return []; }

            /**
             * @phpstan-template-contravariant V of \\Probe\\User
             * @return non-empty-list<V>
             */
            function contravariant(): array { return []; }

            /**
             * @template V
             * @param list<V> $input
             * @return Box<int, V>
             */
            function boxOf(array $input): Box { return new Box(); }

            function probe(): void
            {
                $fromCovariant = covariant();
                $fromContravariant = contravariant();
                $box = boxOf([new User()]);
                $fromBox = $box->all();
            }
            """;

    /**
     * 実引数から決められない型変数は `of` の上界で埋まる。その上界は変性付きのタグからも読む。
     */
    public void testReadsTheBoundOfACovariantTemplate() {
        assertResolves("fromCovariant");
    }

    /**
     * `@phpstan-` 接頭辞付きの反変宣言も同じ。
     */
    public void testReadsTheBoundOfAContravariantTemplate() {
        assertResolves("fromContravariant");
    }

    /**
     * クラスの型変数一覧は宣言順が型引数の順になる。`@template K` の次に
     * `@template-covariant V` と書かれていれば、2 番目の型引数が `V` に当たる。
     */
    public void testKeepsTheDeclarationOrderAcrossVariance() {
        assertResolves("fromBox");
    }

    private void assertResolves(String variableName) {
        var parts = typeOf(variableName);

        assertTrue("変性付きの型変数が解けていない: " + parts,
                parts.contains("non-empty-list<\\Probe\\User>"));
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
