package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;

import java.util.Set;

/**
 * 値の形による条件 (`$input is list<V>`) と、`array<K, V>` のキー側の扱いを確認する。
 */
public final class ShapeConditionTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Probe;

            final class User {}

            /**
             * @template K of array-key
             * @template V
             * @param non-empty-list<V>|array<K, V> $input
             * @phpstan-return ($input is non-empty-list<V> ? non-empty-list<V> : array<K, V>)
             */
            function shaped(array $input): array
            {
                return $input;
            }

            function probe(): void
            {
                $fromList = shaped([new User()]);
                $fromMap = shaped(['a' => new User()]);
                $fromVariable = shaped($given);
            }

            /** @template V */
            final class Box {}

            /**
             * 判定したい側を総称クラスにしてある。PhpStorm 自身はこの型引数を解かない
             * (`Box<#V>` のまま) ので、解けていれば分岐を選べたことになる。
             *
             * @template K of array-key
             * @template V
             * @param non-empty-list<V>|array<K, V> $input
             * @phpstan-return ($input is non-empty-list<V> ? non-empty-list<V> : Box<V>)
             */
            function boxedWhenKeyed(array $input): mixed
            {
                return $input;
            }

            function probeTyped(): void
            {
                $map = ['a' => new User()];
                $fromTypedMap = boxedWhenKeyed($map);
                $ints = [1, 2, 3];
                $fromIntKeys = boxedWhenKeyed($ints);
            }
            """;

    public void testPicksTheListBranchForAListLiteral() {
        assertHas("fromList", "non-empty-list<\\Probe\\User");
    }

    /**
     * キーを書いた配列リテラルなら list ではない。`array<K, V>` の `K` は実引数から取れないので
     * `array-key` で埋める。埋めないとこの分岐が丸ごと出せない。
     *
     * <p>ただし PhpStorm 自身が `array<K, V>` を `V[]` に正規化するため、キーの型はここで失われる
     * (実測: `array<string, \Probe\User>` を渡しても ` \Probe\User[]` になる)。
     * こちらで保てる範囲は要素型までになる。
     */
    public void testPicksTheArrayBranchForAKeyedLiteral() {
        assertHas("fromMap", "\\Probe\\User[]");
    }

    /**
     * キーが整数の `array<...>` は list とは限らない。型からは決めない。
     */
    public void testAddsNothingForAnIntegerKeyedType() {
        var parts = typeOf("fromIntKeys");

        assertFalse("list でないと決められないのに分岐を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("\\Probe\\Box<\\Probe")
                        || t.startsWith("\\Probe\\Box<\\int")));
    }

    /**
     * リテラルが書かれていなくても、実引数の型のキーが文字列なら list ではないと決まる。
     */
    public void testPicksTheOtherBranchForAStringKeyedType() {
        var parts = typeOf("fromTypedMap");

        assertTrue("型から list でない側を選べていない: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("\\Probe\\Box<\\Probe\\User")));
    }

    /**
     * 変数が渡された場合は形が分からない。誤った分岐を出すより何も足さない。
     */
    public void testAddsNothingForAVariable() {
        var parts = typeOf("fromVariable");

        assertFalse("形が分からないのに分岐を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("non-empty-list<") || t.startsWith("array<")));
    }

    private void assertHas(String variableName, String expectedPrefix) {
        var parts = typeOf(variableName);

        assertTrue("期待した分岐が出ていない (" + expectedPrefix + "): " + parts,
                parts.stream().anyMatch(t -> t.startsWith(expectedPrefix)));
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
