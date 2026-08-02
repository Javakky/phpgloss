package io.github.javakky.phpgloss.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 名前付き引数を、書かれた位置でなく名前で仮引数に対応付ける。
 *
 * <p>{@code FunctionReference#getParameters()} は名前を落として値だけを位置順に返す (probe で実測)。
 * 位置のまま読むと `map(mode: ..., input: ...)` のような呼び方で条件型が別の分岐を選ぶ。
 */
public final class NamedArgumentTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            enum Shape
            {
                case AS_KEYED;
                case AS_LIST;
                case AUTO;
            }

            /**
             * @template K of array-key
             * @template V
             *
             * @param list<V>|array<K, V> $input
             * @phpstan-return ($mode is Shape::AS_LIST ? list<V> :
             *   ($mode is Shape::AS_KEYED ? array<K, V> :
             *     ($input is list<V> ? list<V> : array<K, V>)))
             */
            function init(array $input, Shape $mode = Shape::AUTO): array
            {
                return $input;
            }

            function probe(): void
            {
                $mixed = init([1, 2, 3], mode: Shape::AS_LIST);
                $reordered = init(mode: Shape::AS_LIST, input: [1, 2, 3]);
                $reorderedAssoc = init(mode: Shape::AS_KEYED, input: ['a' => 1]);
                $unknownName = init(['a' => 1], setting: Shape::AS_LIST);
            }
            """;

    /**
     * 位置と名前付きの混在。位置の実引数は先頭から、名前付きは名前で対応する。
     */
    public void testMixesPositionalAndNamedArguments() {
        var parts = typeOf("mixed");

        assertTrue("list 側が選ばれていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * 宣言と逆順に書いても同じ結果になる。位置で読むと `$input` に `Shape::AS_LIST` が
     * 当たり、条件は偽側へ抜ける。
     */
    public void testResolvesWhenTheOrderIsReversed() {
        var parts = typeOf("reordered");

        assertTrue("名前でなく位置で対応付けている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * 逆順でも分岐は実引数どおりに分かれる。どちらの呼び方でも同じ枝を選ぶだけでは、
     * 名前で読めていることの証明にならない。
     */
    public void testStillDistinguishesTheBranchesWhenReversed() {
        var parts = typeOf("reorderedAssoc");

        assertFalse("assoc 側で list が選ばれている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
    }

    /**
     * どの仮引数にも当たらない名前は捨てる。位置で当ててしまうと `$mode` として読まれ、
     * 既定値で決まるはずの分岐が変わる。
     */
    public void testDropsAnArgumentWhoseNameMatchesNoParameter() {
        var parts = typeOf("unknownName");

        assertFalse("対応先の無い名前を位置で束縛している: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
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
