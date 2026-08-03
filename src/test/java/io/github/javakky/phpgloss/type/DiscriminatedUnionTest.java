package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/** タグで分かれる shape の union を、タグの値で絞れるか。 */
public final class DiscriminatedUnionTest extends BasePlatformTestCase {
    private String raw(PsiFile file, String variable) {
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    private boolean declared;

    private PsiFile withBody(String body) {
        if (!declared) {
            declared = true;
            myFixture.addFileToProject("Tagged.php", """
                    <?php
                    namespace Probe;

                    interface Tagged
                    {
                        /** @return array{kind: 'A', value: positive-int}|array{kind: 'B', value: non-empty-string} */
                        public function one(): array;
                    }
                    """);
        }
        PsiFile file = myFixture.addFileToProject("probe%d.php".formatted(Math.abs(body.hashCode())), """
                <?php
                namespace Probe;

                function probe(Tagged $tagged): void
                {
                %s
                }
                """.formatted(body));
        myFixture.openFileInEditor(file.getVirtualFile());
        return file;
    }

    /** 絞らなければ、どちらの枝の型も出る。 */
    public void testKeepsBothBranchesWithoutNarrowing() {
        String type = raw(withBody("""
                    $item = $tagged->one();
                    $value = $item['value'];
                """), "value");

        assertTrue("片方の枝が落ちている: " + type,
                type.contains("positive-int") && type.contains("non-empty-string"));
    }

    /** `match` の腕がタグを決めているなら、その枝だけ。 */
    public void testNarrowsInsideAMatchArm() {
        String type = raw(withBody("""
                    $item = $tagged->one();
                    $value = match ($item['kind']) {
                        'A' => $item['value'],
                        default => null,
                    };
                """), "value");

        assertTrue("選んだ枝の型が出ない: " + type, type.contains("positive-int"));
        assertFalse("選ばれなかった枝が残っている: " + type, type.contains("non-empty-string"));
    }

    /** `if` がタグを決めているなら、その枝だけ。 */
    public void testNarrowsInsideAnIf() {
        String type = raw(withBody("""
                    $item = $tagged->one();
                    if ($item['kind'] === 'A') {
                        $value = $item['value'];
                    }
                """), "value");

        assertTrue("選んだ枝の型が出ない: " + type, type.contains("positive-int"));
        assertFalse("選ばれなかった枝が残っている: " + type, type.contains("non-empty-string"));
    }

    /** もう一方のタグでも同じ。 */
    public void testNarrowsToTheOtherBranch() {
        String type = raw(withBody("""
                    $item = $tagged->one();
                    if ($item['kind'] === 'B') {
                        $value = $item['value'];
                    }
                """), "value");

        assertTrue("選んだ枝の型が出ない: " + type, type.contains("non-empty-string"));
        assertFalse("選ばれなかった枝が残っている: " + type, type.contains("positive-int"));
    }

    /** 条件が別の変数を見ているなら、絞らない。 */
    public void testDoesNotNarrowOnAnotherVariable() {
        String type = raw(withBody("""
                    $item = $tagged->one();
                    $other = $tagged->one();
                    if ($other['kind'] === 'A') {
                        $value = $item['value'];
                    }
                """), "value");

        assertTrue("別の変数の条件で絞ってしまった: " + type,
                type.contains("positive-int") && type.contains("non-empty-string"));
    }

    /** どの枝にも当てはまらないタグでは、絞らずに両方残す。 */
    public void testKeepsBothWhenTheTagMatchesNothing() {
        String type = raw(withBody("""
                    $item = $tagged->one();
                    if ($item['kind'] === 'Z') {
                        $value = $item['value'];
                    }
                """), "value");

        assertTrue("当てはまらないタグで枝を落とした: " + type,
                type.contains("positive-int") && type.contains("non-empty-string"));
    }
}
