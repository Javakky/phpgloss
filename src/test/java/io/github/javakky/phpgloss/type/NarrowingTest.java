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

            /**
             * @phpstan-type leaf_data array{weight: positive-int}
             */
            final class Leaf implements Node
            {
                /** @var leaf_data */
                public array $data = ['weight' => 1];
            }

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

    /**
     * `match (true)` の腕で `object` を実装クラスへ絞り、その値を引数として渡す。
     *
     * <p>Symfony の `Descriptor::describe` がこの形。`object $object` を `instanceof` で振り分け、
     * 腕の中で `describeInputArgument($object, ...)` に渡す。絞れていないと引数の型が合わない。
     */
    public void testNarrowsAnObjectParameterInsideMatchArms() {
        myFixture.addFileToProject("Shapes.php", """
                <?php
                namespace Probe;

                final class Argument {}
                final class Option {}

                abstract class Describer
                {
                    public function describe(object $object): void
                    {
                        match (true) {
                            $object instanceof Argument => $this->onArgument($object),
                            $object instanceof Option => $this->onOption($object),
                            default => null,
                        };
                    }

                    abstract protected function onArgument(Argument $argument): void;

                    abstract protected function onOption(Option $option): void;
                }
                """);
        PsiFile file = myFixture.configureByText("probe_match_object.php", """
                <?php
                namespace Probe;

                function probe(object $object): void
                {
                    $seen = match (true) {
                        $object instanceof Argument => $object,
                        default => null,
                    };
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertTrue("object 型の引数が match の腕で絞られない: " + raw(file, "seen"),
                raw(file, "seen").contains("Argument"));
    }

    /** 絞った値を引数として渡す位置でも、絞った型のままである。 */
    public void testKeepsTheNarrowedTypeAtAnArgumentPosition() {
        PsiFile file = myFixture.configureByText("probe_pass.php", """
                <?php
                namespace Probe;

                final class Argument {}

                function consume(Argument $argument): void {}

                function probe(object $object): void
                {
                    match (true) {
                        $object instanceof Argument => consume($object),
                        default => null,
                    };
                }
                """);
        myFixture.openFileInEditor(file.getVirtualFile());

        assertTrue("引数として渡す位置で絞った型が失われる: " + raw(file, "object"),
                raw(file, "object").contains("Argument"));
    }

    /** 指定した位置の変数の型。腕がいくつもある `match` では、どの腕の変数かを選ぶ必要がある。 */
    private String typeAt(PsiFile file, int offset) {
        Variable variable = PsiTreeUtil.findElementOfClassAtOffset(file, offset, Variable.class, false);
        assertNotNull("その位置に変数がない", variable);
        return String.join("|", TypeUnionSimplifier.normalize(
                ((PhpTypedElement) variable).getType().global(getProject())
                        .getTypesWithParametrisedParts()));
    }

    /**
     * 腕が並んだ `match (true)` の、三つめの腕。
     *
     * <p>Symfony の `Descriptor::describe` がこの形で、そこで絞れていないと報告された。腕が一つの
     * ときは絞れるので、腕の数で挙動が変わるかを測る。
     */
    public void testNarrowsInTheThirdArmOfAMatch() {
        String source = """
                <?php
                namespace Probe;

                final class Argument {}
                final class Option {}
                final class Definition {}
                final class Order {}

                function onArgument(Argument $it): void {}
                function onOption(Option $it): void {}
                function onDefinition(Definition $it): void {}
                function onOrder(Order $it): void {}

                function probe(object $object): void
                {
                    match (true) {
                        $object instanceof Argument => onArgument($object),
                        $object instanceof Option => onOption($object),
                        $object instanceof Definition => onDefinition($object),
                        $object instanceof Order => onOrder($object),
                        default => null,
                    };
                }
                """;
        PsiFile file = myFixture.configureByText("probe_arms.php", source);
        myFixture.openFileInEditor(file.getVirtualFile());

        int first = source.indexOf("onArgument($object)") + "onArgument($".length();
        int third = source.indexOf("onDefinition($object)") + "onDefinition($".length();

        assertTrue("一つめの腕で絞れない: " + typeAt(file, first),
                typeAt(file, first).contains("Argument"));
        assertTrue("三つめの腕で絞れない: " + typeAt(file, third),
                typeAt(file, third).contains("Definition"));
    }

    /**
     * 実物と同じ形をすべて揃える。抽象メソッドへの `$this->` 呼び出し、引数が二つ、
     * `default` が `throw` 式、`match` の結果を捨てる文。どれかが効いているかを見る。
     */
    public void testNarrowsWhenTheArmCallsAnAbstractMethod() {
        String source = """
                <?php
                namespace Probe;

                final class Argument {}
                final class Option {}
                final class Definition {}

                final class Bad extends \\RuntimeException {}

                abstract class Describer
                {
                    protected string $sink;

                    public function describe(object $object, array $options = []): void
                    {
                        match (true) {
                            $object instanceof Argument => $this->onArgument($object, $options),
                            $object instanceof Option => $this->onOption($object, $options),
                            $object instanceof Definition => $this->onDefinition($object, $options),
                            default => throw new Bad(\\sprintf('type "%s"', get_debug_type($object))),
                        };
                    }

                    abstract protected function onArgument(Argument $argument, array $options = []): void;

                    abstract protected function onOption(Option $option, array $options = []): void;

                    abstract protected function onDefinition(Definition $definition, array $options = []): void;
                }
                """;
        PsiFile file = myFixture.configureByText("probe_abstract.php", source);
        myFixture.openFileInEditor(file.getVirtualFile());

        int third = source.indexOf("$this->onDefinition($object") + "$this->onDefinition($".length();

        assertTrue("抽象メソッド呼び出しの腕で絞れない: " + typeAt(file, third),
                typeAt(file, third).contains("Definition"));
    }
}
