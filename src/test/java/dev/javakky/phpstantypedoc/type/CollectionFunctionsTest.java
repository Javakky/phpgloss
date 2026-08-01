package dev.javakky.phpstantypedoc.type;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;

import java.util.Set;

/**
 * 実際のコードに書かれている形の `reduce` / `groupBy` / `any` / `chunkBy` の PHPDoc をそのまま写して測る。
 *
 * <p>自分で書いた fixture では通るのに実物では効かない、という差がこれまで 2 度出ている
 * (`@phpstan-param` にも条件型が書かれている / 3 段の入れ子)。同族の関数をまとめて固定する。
 *
 * <p>`reduce` だけは呼び出し側の型を直接見ても判定できない。素の `@return R|V|null` を PhpStorm 自身が
 * 解いて同じ union に並べるため、こちらが分岐を決められていなくても表示は変わらない。
 * 解決器の出力そのものを見る。
 */
public final class CollectionFunctionsTest extends BasePlatformTestCase {
    private static final String SOURCE = """
            <?php
            namespace Example\\Collection;

            /**
             * 配列を畳み込んで単一の値にします.
             *
             * @template V
             * @template R
             *
             * @param array<V> $input
             * @param callable(R|V|null, V): (R|V|null) $callback
             * @param R $initial
             * @phpstan-param (R is null ?
             *    callable(?V, V): ?V :
             *    callable(R, V): R
             *  ) $callback
             * @return R|V|null
             * @phpstan-return (R is null ? (?V) : R)
             */
            function reduce(array $input, callable $callback, mixed $initial = null): mixed
            {
                return $initial;
            }

            /**
             * コールバックで指定した分類キーごとに配列をグループ化します。
             *
             * @template K of array-key
             * @template V
             * @template G of array-key
             *
             * @param list<V>|array<K, V> $input 対象の配列
             * @param callable(V, K): G $classifier 分類キーを返す関数（第1引数: 値, 第2引数: キー）
             * @return array<G, list<V>|array<K, V>>
             * @phpstan-return ($input is list<V> ? array<G, list<V>> : array<G, array<K, V>>)
             */
            function groupBy(array $input, callable $classifier): array
            {
                return [];
            }

            /**
             * いずれかの要素がコールバック関数を満たすかどうかを調べる
             *
             * @template K of array-key
             * @template V
             *
             * @param list<V>|array<K, V> $input 対象の配列 フィルターする条件
             * @param callable(V, K): bool $callback フィルターする条件
             */
            function any(array $input, callable $callback): bool
            {
                return false;
            }

            /**
             * 連続する要素を $callback の戻り値が等しい範囲ごとにまとめた配列の配列を返します。
             *
             * @template V
             * @template R
             *
             * @param list<V> $input  対象の配列
             * @param callable(V): R $callback  各要素を chunk キーに変換する。
             * @return list<non-empty-list<V>>
             */
            function chunkBy(array $input, callable $callback): array
            {
                return [];
            }

            function probe(): void
            {
                $grouped = groupBy([1, 2, 3], fn (int $v, int $k): string => 'g');
                $groupedAssoc = groupBy(['a' => 1], fn (int $v, string $k): string => 'g');
                $groupedUnknown = groupBy($given, fn (int $v, int $k): string => 'g');
                $chunked = chunkBy([1, 1, 2], fn (int $v): int => $v);
                $chunkedByString = chunkBy(['a', 'b'], fn (string $v): string => $v);
                $anyResult = any([1, 2], fn (int $v, int $k): bool => true);
                $reduced = reduce([1, 2, 3], fn (int $c, int $v): int => $c + $v, 0);
                $reducedWithoutInitial = reduce([1, 2, 3], fn (?int $c, int $v): ?int => $v);
                $reducedWithUnknownInitial = reduce([1, 2, 3], fn (int $c, int $v): int => $v, $seed);
            }
            """;

    /**
     * list を渡したときの `groupBy`。分類キーの型 `G` はクロージャの戻り型宣言から決まり、
     * 条件型は書かれた配列リテラルで list 側に畳まれる。
     */
    public void testGroupsAListByTheClassifierReturnType() {
        var parts = typeOf("grouped");

        assertTrue("groupBy の list 分岐が解けていない: " + parts,
                parts.stream().anyMatch(t -> t.replace(" ", "").equals("array<\\string,list<\\int>>")));
    }

    /**
     * キー付きの配列を渡したときは、条件が偽側に畳まれてキーの型変数 `K` も要る。
     */
    public void testGroupsAKeyedArrayKeepingItsKeyType() {
        var parts = typeOf("groupedAssoc");

        assertTrue("groupBy の array 分岐が解けていない: " + parts,
                parts.stream().anyMatch(
                        t -> t.replace(" ", "").equals("array<\\string,array<\\string,\\int>>")));
    }

    /**
     * 実引数の形が分からなければ list かどうかを決められない。両分岐の union を出すと嘘になるので
     * 何も足さない。
     */
    public void testAddsNothingWhenTheInputShapeIsUnknown() {
        var parts = typeOf("groupedUnknown");

        assertFalse("形が分からないのに分岐を選んでいる: " + parts,
                parts.stream().anyMatch(t -> t.startsWith("array<")));
    }

    /**
     * `chunkBy` は条件型を持たない。要素型 `V` が入力から決まれば、入れ子の総称型がそのまま解ける。
     */
    public void testChunksKeepingTheElementType() {
        var parts = typeOf("chunked");

        assertTrue("chunkBy の戻り値が解けていない: " + parts,
                parts.contains("list<non-empty-list<\\int>>"));
    }

    public void testChunksAStringList() {
        var parts = typeOf("chunkedByString");

        assertTrue("chunkBy の戻り値が解けていない: " + parts,
                parts.contains("list<non-empty-list<\\string>>"));
    }

    /**
     * `any` の戻り値は `bool` で型変数を含まない。PhpStorm が既に解いているので、
     * この解決器は何も足さない。
     */
    public void testAddsNothingForAPlainBooleanReturn() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);

        assertNull("型変数を含まない戻り値に手を出している", ResolvedCall.of(file, "anyResult"));
        assertTrue("bool が失われている: " + typeOf(file, "anyResult"),
                typeOf(file, "anyResult").stream().anyMatch(t -> t.contains("bool")));
    }

    /**
     * `$initial` を書いた `reduce` は、条件 `R is null` が偽になり `R` がそのまま戻り値になる。
     */
    public void testReducesToTheInitialTypeWhenItIsWritten() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        PhpType resolved = ResolvedCall.of(file, "reduced");

        assertNotNull("条件が決まるのに何も解けていない", resolved);
        assertEquals(Set.of("\\int"), resolved.getTypes());
    }

    /**
     * `$initial` を書かなければ宣言の既定値 `null` が `R` になり、条件は真。`?V` 側が選ばれる。
     */
    public void testReducesToANullableElementWhenTheInitialIsOmitted() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);
        PhpType resolved = ResolvedCall.of(file, "reducedWithoutInitial");

        assertNotNull("既定値で条件が決まるのに何も解けていない", resolved);
        assertEquals(Set.of("\\int", "\\null"), resolved.getTypes());
    }

    /**
     * `$initial` の型が分からなければ null かどうかも決まらない。条件型の文字列をそのまま
     * 型として出すのが一番まずいので、何も足さないことを固定する。
     */
    public void testAddsNothingWhenTheInitialTypeIsUnknown() {
        var file = myFixture.configureByText(PhpFileType.INSTANCE, SOURCE);

        assertNull("null かどうか決まらないのに分岐を選んでいる",
                ResolvedCall.of(file, "reducedWithUnknownInitial"));
        assertFalse("条件型の文字列を型として出している: " + typeOf(file, "reducedWithUnknownInitial"),
                typeOf(file, "reducedWithUnknownInitial").stream().anyMatch(t -> t.contains(" is ")));
    }

    private Set<String> typeOf(String variableName) {
        return typeOf(myFixture.configureByText(PhpFileType.INSTANCE, SOURCE), variableName);
    }

    private Set<String> typeOf(PsiFile file, String variableName) {
        Variable variable = PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variableName.equals(v.getName()))
                .findFirst().orElseThrow();
        return ((PhpTypedElement) variable).getType().global(getProject())
                .getTypesWithParametrisedParts();
    }
}
