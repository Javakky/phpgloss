package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * 値が制御構造を通ったときに、型が保たれるか。
 *
 * <p>`foreach` で shape が落ちていたのと同じことが、他の構造でも起きうる。こちらが一度も
 * 試していない形をまとめて通す。
 */
public final class ControlFlowShapesTest extends BasePlatformTestCase {
    private static final String PORT = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type row array{id: positive-int, label: non-empty-string}
             */
            interface Rows
            {
                /** @return row */
                public function one(): array;

                /** @return list<row> */
                public function all(): array;

                /** @return row|null */
                public function find(int $id): ?array;
            }
            """;

    private String shown(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> io.github.javakky.phpgloss.doc.VariableHoverProbe.typeAt(
                        file, v.getTextRange().getStartOffset() + 1))
                .orElse("");
    }

    private String raw(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((com.jetbrains.php.lang.psi.elements.PhpTypedElement) v).getType()
                                .global(getProject()).getTypesWithParametrisedParts())))
                .orElse("");
    }

    private PsiFile withBody(String body) {
        myFixture.addFileToProject("Rows.php", PORT);
        PsiFile file = myFixture.addFileToProject("probe%d.php".formatted(Math.abs(body.hashCode())), """
                <?php
                namespace Probe;

                function probe(Rows $rows, int $n): void
                {
                %s
                }
                """.formatted(body));
        myFixture.openFileInEditor(file.getVirtualFile());
        return file;
    }

    /** `match` の枝から返る値。 */
    public void testKeepsAShapeThroughMatch() {
        var file = withBody("""
                    $picked = match (true) {
                        $n > 0 => $rows->one(),
                        default => $rows->one(),
                    };
                """);
        assertTrue("match を通ると shape が落ちる: " + raw(file, "picked"),
                raw(file, "picked").contains("array{") || raw(file, "picked").contains("row"));
    }

    /** 三項演算子の枝。 */
    public void testKeepsAShapeThroughTernary() {
        var file = withBody("""
                    $picked = $n > 0 ? $rows->one() : $rows->one();
                """);
        assertTrue("三項を通ると shape が落ちる: " + raw(file, "picked"),
                raw(file, "picked").contains("array{") || raw(file, "picked").contains("row"));
    }

    /** `try` の中で代入し、`catch` の後で使う。 */
    public void testKeepsAShapeAcrossTryCatch() {
        var file = withBody("""
                    $found = null;
                    try {
                        $found = $rows->one();
                    } catch (\\Throwable $error) {
                    }
                    $seen = $found;
                """);
        assertTrue("try を抜けると shape が落ちる: " + raw(file, "found"),
                raw(file, "found").contains("array{") || raw(file, "found").contains("row"));
    }

    /** 早期 return で null を除いたあと。 */
    public void testNarrowsAfterAnEarlyReturn() {
        var file = withBody("""
                    $found = $rows->find($n);
                    if ($found === null) {
                        return;
                    }
                    $seen = $found;
                """);
        assertFalse("null を除いた後も null が残る: " + shown(file, "seen"),
                shown(file, "seen").contains("null"));
    }

    /** 分解代入の左辺。何も言えなくてよいが、誤った型を与えてはいけない。 */
    public void testDoesNotGuessOnListAssignment() {
        var file = withBody("""
                    [$first, $second] = $rows->all();
                    $seen = $first;
                """);
        assertFalse("分解した変数に配列そのものの型を与えている: " + raw(file, "first"),
                raw(file, "first").contains("list<"));
    }

    /** null 合体で既定値を与えたあと。 */
    public void testKeepsTheShapeThroughNullCoalescing() {
        var file = withBody("""
                    $found = $rows->find($n) ?? $rows->one();
                    $seen = $found;
                """);
        assertTrue("?? を通ると shape が落ちる: " + raw(file, "found"),
                raw(file, "found").contains("array{") || raw(file, "found").contains("row"));
    }
}
