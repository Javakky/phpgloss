package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * `foreach` の書き方いろいろ。要素の型を与える側は、こちらが試していない形でも壊れてはいけない。
 *
 * <p>キーと値を取り違える欠陥を一度出しているので、変数の受け方が変わる形をまとめて通す。
 */
public final class ForeachShapesTest extends BasePlatformTestCase {
    private static final String PORT = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type row array{id: positive-int, label: non-empty-string}
             * @phpstan-type pair array{0: non-empty-string, 1: positive-int}
             */
            interface Rows
            {
                /** @return list<row> */
                public function all(): array;

                /** @return array<non-empty-string, row> */
                public function byName(): array;

                /** @return list<pair> */
                public function pairs(): array;

                /** @return array<non-empty-string, list<row>> */
                public function grouped(): array;
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

    private PsiFile withBody(String body) {
        myFixture.addFileToProject("Rows.php", PORT);
        PsiFile file = myFixture.addFileToProject("probe-%d.php".formatted(body.hashCode()), """
                <?php
                namespace Probe;

                function probe(Rows $rows): void
                {
                %s
                }
                """.formatted(body));
        myFixture.openFileInEditor(file.getVirtualFile());
        return file;
    }

    /** 参照で受けても、値の型は同じ。 */
    public void testHandlesAReference() {
        var file = withBody("""
                    foreach ($rows->all() as &$row) {
                        $seen = $row;
                    }
                """);
        assertTrue("参照で受けると shape が落ちる: " + typeOf(file, "row"),
                typeOf(file, "row").contains("array{"));
    }

    /** キーと参照を同時に使っても、取り違えない。 */
    public void testHandlesAKeyWithAReference() {
        var file = withBody("""
                    foreach ($rows->byName() as $name => &$row) {
                        $seen = $row;
                    }
                """);
        assertFalse("キーに値の型が混ざる: " + typeOf(file, "name"),
                typeOf(file, "name").contains("array{"));
        assertTrue("値の shape が落ちる: " + typeOf(file, "row"),
                typeOf(file, "row").contains("array{"));
    }

    /** 分解して受ける形。何も言えなくてよいが、誤った型を与えてはいけない。 */
    public void testDoesNotGuessOnDestructuring() {
        var file = withBody("""
                    foreach ($rows->pairs() as [$name, $count]) {
                        $seenName = $name;
                    }
                """);
        assertFalse("分解した変数に要素そのものの型を与えている: " + typeOf(file, "name"),
                typeOf(file, "name").contains("array{"));
    }

    /** 入れ子の foreach。外側の要素を内側で回す。 */
    public void testHandlesNestedLoops() {
        var file = withBody("""
                    foreach ($rows->grouped() as $name => $group) {
                        foreach ($group as $row) {
                            $seen = $row;
                        }
                    }
                """);
        assertTrue("内側の要素が shape にならない: " + typeOf(file, "row"),
                typeOf(file, "row").contains("array{"));
        assertFalse("外側のキーに値の型が混ざる: " + typeOf(file, "name"),
                typeOf(file, "name").contains("array{"));
    }

    /** `yield $k => $v` を回す。Generator のキーと値。 */
    public void testHandlesAGenerator() {
        myFixture.addFileToProject("Source.php", """
                <?php
                namespace Probe;

                /**
                 * @phpstan-type row array{id: positive-int}
                 */
                final class Source
                {
                    /** @return \\Generator<non-empty-string, row> */
                    public function stream(): \\Generator { yield 'a' => ['id' => 1]; }
                }
                """);
        PsiFile file = myFixture.addFileToProject("generator.php", """
                <?php
                namespace Probe;

                function probe(Source $source): void
                {
                    foreach ($source->stream() as $name => $row) {
                        $seen = $row;
                    }
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertFalse("Generator のキーに値の型が混ざる: " + typeOf(file, "name"),
                typeOf(file, "name").contains("array{"));
    }
}
