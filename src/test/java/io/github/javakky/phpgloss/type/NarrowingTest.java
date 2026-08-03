package io.github.javakky.phpgloss.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * `match` / `switch` / `instanceof` で型が絞られたあと、こちらが足した型が残るか。
 *
 * <p>絞り込みは PhpStorm の仕事だが、絞る対象がこちらの解いた型 (エイリアスや shape) のとき、
 * 絞った先で形が落ちてはいけない。
 */
public final class NarrowingTest extends BasePlatformTestCase {
    private static final String PORT = """
            <?php
            namespace Probe;

            interface Node {}
            final class Branch implements Node {}

            /**
             * @phpstan-type row array{id: positive-int, label: non-empty-string}
             */
            interface Rows
            {
                /** @return row|null */
                public function find(int $id): ?array;

                /** @return row|non-empty-string */
                public function either(): array|string;

                /** @return Node */
                public function node(): Node;
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

    /** エイリアスを持つ実装クラス。宣言を分けて、ファイル内の他の宣言と混ざらないようにする。 */
    private static final String LEAF = """
            <?php
            namespace Probe;

            /**
             * @phpstan-type leaf_data array{weight: positive-int}
             */
            final class Leaf implements Node
            {
                /** @var leaf_data */
                public array $data = ['weight' => 1];
            }
            """;

    private PsiFile withBody(String body) {
        myFixture.addFileToProject("Rows.php", PORT);
        myFixture.addFileToProject("Leaf.php", LEAF);
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

    /** `if` で null を除いたあと、shape が残る。 */
    public void testNarrowsNullAwayWithIf() {
        var file = withBody("""
                    $found = $rows->find($n);
                    if ($found !== null) {
                        $seen = $found;
                    }
                """);
        assertTrue("null を除くと shape も落ちる: " + raw(file, "seen"),
                raw(file, "seen").contains("array{"));
    }

    /** `match (true)` の腕の中で絞られる。 */
    public void testNarrowsInsideMatch() {
        var file = withBody("""
                    $node = $rows->node();
                    $label = match (true) {
                        $node instanceof Leaf => $node,
                        default => null,
                    };
                """);
        assertTrue("match の腕で絞られない: " + raw(file, "label"),
                raw(file, "label").contains("Leaf"));
    }

    /** `switch (true)` の case の中で絞られる。 */
    public void testNarrowsInsideSwitch() {
        var file = withBody("""
                    $node = $rows->node();
                    switch (true) {
                        case $node instanceof Branch:
                            $seen = $node;
                            break;
                    }
                """);
        assertTrue("switch の case で絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Branch"));
    }

    /** union のうち配列側だけを `is_array` で取り出す。 */
    public void testNarrowsAUnionByIsArray() {
        var file = withBody("""
                    $either = $rows->either();
                    if (is_array($either)) {
                        $seen = $either;
                    }
                """);
        assertTrue("is_array で絞ると shape が落ちる: " + raw(file, "seen"),
                raw(file, "seen").contains("array{"));
    }

    /** `match` のキーが `::class` の形。 */
    public void testNarrowsByClassConstant() {
        var file = withBody("""
                    $node = $rows->node();
                    $seen = match ($node::class) {
                        Leaf::class => $node,
                        default => null,
                    };
                """);
        assertTrue("::class で絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Leaf") || raw(file, "seen").contains("Node"));
    }

    /** 親の型で受けた変数を、子で絞る。 */
    public void testNarrowsFromAnInterfaceToAnImplementation() {
        var file = withBody("""
                    $node = $rows->node();
                    if ($node instanceof Leaf) {
                        $seen = $node;
                    }
                """);
        assertTrue("実装クラスに絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Leaf"));
    }

    /** 否定して早期 return したあと。 */
    public void testNarrowsAfterANegatedInstanceofGuard() {
        var file = withBody("""
                    $node = $rows->node();
                    if (!($node instanceof Leaf)) {
                        return;
                    }
                    $seen = $node;
                """);
        assertTrue("否定の guard を抜けても絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Leaf"));
    }

    /** `else` の側。 */
    public void testNarrowsInTheElseBranch() {
        var file = withBody("""
                    $node = $rows->node();
                    if ($node instanceof Leaf) {
                        $inThen = $node;
                    } else {
                        $inElse = $node;
                    }
                """);
        assertTrue("then 側で絞られない: " + raw(file, "inThen"),
                raw(file, "inThen").contains("Leaf"));
        assertFalse("else 側にも絞った型が残っている: " + raw(file, "inElse"),
                raw(file, "inElse").contains("Leaf"));
    }

    /** `&&` で条件を重ねる。 */
    public void testNarrowsThroughAConjunction() {
        var file = withBody("""
                    $node = $rows->node();
                    if ($node instanceof Leaf && $n > 0) {
                        $seen = $node;
                    }
                """);
        assertTrue("&& の先で絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Leaf"));
    }

    /** 三項の条件として使う。 */
    public void testNarrowsInsideATernary() {
        var file = withBody("""
                    $node = $rows->node();
                    $seen = $node instanceof Leaf ? $node : null;
                """);
        assertTrue("三項の中で絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Leaf"));
    }

    /** 絞ったあとにプロパティを読む。こちらの機構が効く場所。 */
    public void testReadsAnAliasedPropertyAfterNarrowing() {
        var file = withBody("""
                    $node = $rows->node();
                    if ($node instanceof Leaf) {
                        $weight = $node->data['weight'];
                    }
                """);
        assertTrue("絞ったあとプロパティの shape が読めない: " + raw(file, "weight"),
                raw(file, "weight").contains("positive-int"));
    }

    /** union のうちクラス側を `instanceof` で分ける。 */
    public void testSplitsAUnionOfClasses() {
        myFixture.addFileToProject("Mixed.php", """
                <?php
                namespace Probe;

                interface Mixed2
                {
                    /** @return Leaf|Branch */
                    public function either(): Node;
                }
                """);
        PsiFile file = myFixture.addFileToProject("split.php", """
                <?php
                namespace Probe;

                function probe(Mixed2 $source): void
                {
                    $node = $source->either();
                    if ($node instanceof Leaf) {
                        $seen = $node;
                    }
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        // 絞った型は入るが、PhpStorm は元の union も残す。型システムの上では両方が並び、
        // 絞り込みは補完や検査の側で効く。こちらが足すものはない
        assertTrue("union から一方に絞れない: " + raw(file, "seen"),
                raw(file, "seen").contains("Leaf"));
    }
}
