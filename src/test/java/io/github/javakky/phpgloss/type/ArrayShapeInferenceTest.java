package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

/**
 * array shape を含む型の推論。実プロジェクトでは shape が型の主役になる
 * (計測: 4,618 代入のうち 696 が shape、84 がオプショナルキー、101 が 2 段以上の入れ子)。
 *
 * <p>ここで書く形は構文の骨組みだけで、どのプロジェクトのものでもない。
 *
 * <p>見るのは型システムに入った成分。PhpStorm 自身の `array` や `mixed` が同じ union に並ぶが、
 * それは表示のときに整理される (`VariableTypeUnionHoverTest`)。ここでは shape が取り出せるかだけを見る。
 */
public final class ArrayShapeInferenceTest extends BasePlatformTestCase {
    private String typeOf(String source, String variable) {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, source);
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .reduce((first, last) -> last)
                .map(v -> String.join("|", TypeUnionSimplifier.normalize(
                        ((PhpTypedElement) v).getType().global(getProject())
                                .getTypesWithParametrisedParts())))
                .orElse("");
    }

    /** shape を型引数に持つ総称型から、要素の shape が取り出せる。 */
    public void testResolvesAShapeInsideATypeArgument() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(): void
                {
                    /** @var list<array{type: string, text: string}> $nodes */
                    $nodes = [];
                    $node = firstOrNull($nodes);
                }
                """, "node");

        assertTrue("shape が型引数から取り出せていない: " + type, type.contains("array{"));
        assertTrue("null が落ちている: " + type, type.contains("null"));
    }

    /** オプショナルキー (`attrs?:`) を含む shape でも、構造を保って解ける。 */
    public void testKeepsAnOptionalKeyInAShape() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(): void
                {
                    /** @var list<array{type: string, attrs?: array<string, mixed>}> $nodes */
                    $nodes = [];
                    $node = firstOrNull($nodes);
                }
                """, "node");

        assertTrue("オプショナルキーが落ちている: " + type, type.contains("attrs?"));
    }

    /** 2 段以上に入れ子になった shape も、内側まで保つ。 */
    public void testKeepsANestedShape() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(): void
                {
                    /** @var list<array{node: array{type: string}, depth: int}> $rows */
                    $rows = [];
                    $row = firstOrNull($rows);
                }
                """, "row");

        assertTrue("内側の shape が落ちている: " + type,
                type.replace(" ", "").contains("array{type:string}"));
    }

    /** リテラルの union (`'a'|'b'|'c'`) を型引数に持つ形。 */
    public void testKeepsALiteralUnionElement() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(): void
                {
                    /** @var list<'draft'|'published'|'archived'> $states */
                    $states = [];
                    $state = firstOrNull($states);
                }
                """, "state");

        assertTrue("リテラル union が落ちている: " + type, type.contains("draft"));
    }

    /** `class-string<T>` を型引数に持つ形。 */
    public void testKeepsAClassStringElement() {
        String type = typeOf("""
                <?php
                namespace Probe;

                final class Node {}

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(): void
                {
                    /** @var list<class-string<Node>> $classes */
                    $classes = [];
                    $class = firstOrNull($classes);
                }
                """, "class");

        assertTrue("class-string が落ちている: " + type, type.contains("class-string"));
    }

    /** 総称クラスを型引数に持つ形 (`list<Collection<Node>>`)。 */
    public void testKeepsAGenericClassElement() {
        String type = typeOf("""
                <?php
                namespace Probe;

                /** @template T */
                final class Collection {}

                /**
                 * @template V
                 * @param list<V> $input
                 * @return V|null
                 */
                function firstOrNull(array $input): mixed { return $input[0] ?? null; }

                function probe(): void
                {
                    /** @var list<Collection<int>> $groups */
                    $groups = [];
                    $group = firstOrNull($groups);
                }
                """, "group");

        assertTrue("総称クラスの型引数が落ちている: " + type,
                type.replace(" ", "").contains("Collection<int>")
                        || type.replace(" ", "").contains("Collection<\\int>"));
    }
}
