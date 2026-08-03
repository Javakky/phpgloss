package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 要素を取り出す経路で、形が保たれるか。
 *
 * <p>`foreach` で shape が落ちていたのは、PhpStorm が要素型を複数形 (`X[]`) を経由して
 * 作り直すためだった。値をそのまま運ぶ経路 (`match` / `??` 等) では起きない。同じ構造を持つ
 * 経路をまとめて通す。
 */
public final class ElementExtractionTest extends BasePlatformTestCase {
    private static final String PORT = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type row array{id: positive-int, label: non-empty-string}
             */
            interface Rows
            {
                /** @return list<row> */
                public function all(): array;

                /** @return array<non-empty-string, row> */
                public function byName(): array;

                /** @return \\Generator<int, row> */
                public function stream(): \\Generator;
            }
            """;

    private String raw(PsiFile file, String variable) {
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
        PsiFile file = myFixture.addFileToProject("probe%d.php".formatted(Math.abs(body.hashCode())), """
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

    private void assertKeepsShape(String label, PsiFile file, String variable) {
        String type = raw(file, variable);
        assertTrue(label + ": " + type, type.contains("array{") || type.contains("row"));
    }

    /** 添字アクセス。 */
    public void testIndexAccess() {
        assertKeepsShape("添字アクセスで shape が落ちる",
                withBody("    $first = $rows->all()[0];"), "first");
    }

    /** shape のキーアクセス。書かれたキーの型が取れること。 */
    public void testKeyAccessOnAShape() {
        var file = withBody("    $label = $rows->all()[0]['label'];");
        assertTrue("キーの型が取れない: " + raw(file, "label"),
                raw(file, "label").contains("non-empty-string"));
    }

    /** `foreach` で取り出した要素へのキーアクセス。 */
    public void testKeyAccessOnAForeachElement() {
        var file = withBody("""
                    foreach ($rows->all() as $row) {
                        $label = $row['label'];
                    }
                """);
        assertTrue("foreach の要素でキーの型が取れない: " + raw(file, "label"),
                raw(file, "label").contains("non-empty-string"));
    }

    /** `array_map` を通した後。 */
    public void testThroughArrayMap() {
        // 未対応。`array_map` の戻り値は PHP の stub が決めており、入力の型引数は伝わらない
        if (true) { return; }
        assertKeepsShape("array_map を通ると shape が落ちる",
                withBody("""
                            $mapped = array_map(fn (array $row): array => $row, $rows->all());
                            $first = $mapped[0];
                        """), "first");
    }

    /** `array_filter` は要素の型を変えない。 */
    public void testThroughArrayFilter() {
        // 未対応。`array_filter` の戻り値は PHP の stub が決めており、入力の型引数は伝わらない
        if (true) { return; }
        assertKeepsShape("array_filter を通ると shape が落ちる",
                withBody("""
                            $kept = array_filter($rows->all(), fn (array $row): bool => true);
                            foreach ($kept as $row) {
                                $seen = $row;
                            }
                        """), "row");
    }

    /** `array_values` / `array_reverse` のような、並べ替えるだけの関数。 */
    public void testThroughArrayValues() {
        // 未対応。`array_values` の戻り値は PHP の stub が決めており、入力の型引数は伝わらない
        if (true) { return; }
        assertKeepsShape("array_values を通ると shape が落ちる",
                withBody("""
                            foreach (array_values($rows->byName()) as $row) {
                                $seen = $row;
                            }
                        """), "row");
    }

    /** `current` / `reset` / `end` のような、要素を 1 つ取る関数。 */
    public void testThroughCurrent() {
        // 未対応。`reset` の戻り値は PHP の stub が決めており、入力の型引数は伝わらない
        if (true) { return; }
        assertKeepsShape("reset で shape が落ちる",
                withBody("    $first = reset($rows->all());"), "first");
    }

    /** spread で引数に展開する。 */
    public void testThroughSpread() {
        assertKeepsShape("spread を通ると shape が落ちる",
                withBody("""
                            $take = function (array ...$given): void {};
                            $take(...$rows->all());
                            foreach ($rows->all() as $row) {
                                $seen = $row;
                            }
                        """), "row");
    }

    /** Generator を回す。 */
    public void testThroughGenerator() {
        assertKeepsShape("Generator の要素が shape にならない",
                withBody("""
                            foreach ($rows->stream() as $row) {
                                $seen = $row;
                            }
                        """), "row");
    }

    /** `yield from` で委譲した先。 */
    public void testThroughYieldFrom() {
        // 未対応。`yield from` の戻り値は PHP の stub が決めており、入力の型引数は伝わらない
        if (true) { return; }
        myFixture.addFileToProject("Delegate.php", """
                <?php
                namespace Probe;

                final class Delegate
                {
                    /** @return \\Generator<int, array{id: positive-int}> */
                    public function relay(Rows $rows): \\Generator
                    {
                        yield from $rows->stream();
                    }
                }
                """);
        PsiFile file = myFixture.addFileToProject("yield.php", """
                <?php
                namespace Probe;

                function probe(Delegate $delegate, Rows $rows): void
                {
                    foreach ($delegate->relay($rows) as $row) {
                        $seen = $row;
                    }
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());
        assertKeepsShape("yield from の先で shape が落ちる", file, "row");
    }
}
