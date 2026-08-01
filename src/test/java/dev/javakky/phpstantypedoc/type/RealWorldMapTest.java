package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 実際のコードにある callable 付きの総称関数 の PHPDoc をそのまま写して測る。
 *
 * <p>`init` との差は、要素型が入力ではなくコールバックの戻り値から決まること。3 段の条件型と
 * `@template K of array-key` の境界はそのまま。
 */
public final class RealWorldMapTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php

            namespace Example\\Collection;

            enum Shape
            {
                case AS_KEYED;
                case AS_LIST;
                case AUTO;
            }

            /**
             * 各要素にコールバックを適用した配列を返します。
             *
             * @template K of array-key
             * @template V
             * @template E
             *
             * @param list<V>|array<K, V> $input 対象の配列
             * @phpstan-param ($mode is Shape::AS_LIST ? list<V> :
             *   ($mode is Shape::AS_KEYED ? array<K, V> :
             *     list<V>|array<K, V>
             * )) $input
             * @param callable(V, K): E $callback 各要素に適用する関数
             * @return list<E>|array<K, E>
             * @phpstan-return ($mode is Shape::AS_LIST ? list<E> :
             *     ($mode is Shape::AS_KEYED ? array<K, E> :
             *       ($input is list<V> ? list<E> :
             *         array<K, E>
             *  )))
             */
            function map(array $input, callable $callback, Shape $mode = Shape::AUTO): array
            {
                return $input;
            }

            function probe(): void
            {
                $asList = map([1, 2, 3], fn (int $v): string => (string) $v, Shape::AS_LIST);
                $asAssoc = map(['a' => 1], fn (int $v): string => (string) $v, Shape::AS_KEYED);
                $auto = map([1, 2, 3], fn (int $v): string => (string) $v);
            }
            """;

    public void testResolvesTheListBranchWithTheCallbackResult() {
        var parts = typeOf("asList");

        assertTrue("list<string> になっていない: " + parts, parts.contains("list<\\string>"));
    }

    /**
     * `$mode` を書かずに呼ぶと既定値 `Shape::AUTO` で外側 2 段が偽側へ抜け、3 段目の
     * `$input is list<V>` が配列リテラルで決まる。実物で一番よく書かれる呼び方がこれになる。
     */
    public void testFoldsThreeLevelsWhenTheModeIsOmitted() {
        var parts = typeOf("auto");

        assertTrue("3 段目まで畳めていない: " + parts, parts.contains("list<\\string>"));
    }

    /**
     * キーを保つ側では `array<K, E>` が選ばれる。`K` は入力から、`E` はコールバックから決まる。
     */
    public void testKeepsTheKeyOnTheAssociativeBranch() {
        var parts = typeOf("asAssoc");

        assertFalse("assoc 側で list が選ばれている: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("list<")));
        assertTrue("キーと要素の両方が決まっていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("array<") && t.endsWith("\\string>")));
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
