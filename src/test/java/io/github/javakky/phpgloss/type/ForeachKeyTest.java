package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/** `foreach ($x as $key => $value)` のキーと値を取り違えていないこと。 */
public final class ForeachKeyTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type row array{id: positive-int}
             */
            interface Rows
            {
                /** @return array<non-empty-string, row> */
                public function byName(): array;
            }

            function probe(Rows $rows): void
            {
                foreach ($rows->byName() as $name => $row) {
                    $seenName = $name;
                    $seenRow = $row;
                }
            }
            """;

    private String typeOf(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    /** キーには `array<K, V>` の K 側が入り、値の型は混ざらない。 */
    public void testKeepsTheKeyAndTheValueApart() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);

        assertTrue("キーの型が取れていない: " + typeOf(file, "name"),
                typeOf(file, "name").contains("non-empty-string"));
        assertFalse("キーに値の型が混ざっている: " + typeOf(file, "name"),
                typeOf(file, "name").contains("array{"));
        assertTrue("値の shape が取れていない: " + typeOf(file, "row"),
                typeOf(file, "row").contains("array{"));
    }

    /** 連番のキーしか持たない形では、キーについて何も言わない。 */
    public void testSaysNothingAboutAListKey() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, """
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int}
                 */
                interface Rows
                {
                    /** @return list<row> */
                    public function all(): array;
                }

                function probe(Rows $rows): void
                {
                    foreach ($rows->all() as $index => $row) {
                        $seenIndex = $index;
                    }
                }
                """);

        assertFalse("list のキーに要素の型を与えている: " + typeOf(file, "index"),
                typeOf(file, "index").contains("array{"));
    }
}
