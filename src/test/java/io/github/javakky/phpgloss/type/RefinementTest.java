package io.github.javakky.phpgloss.type;

import junit.framework.TestCase;

import java.util.List;

/**
 * 解決した型より union の中の成分が精密なら、そちらを見せる。
 *
 * <p>変異テストで、この機構を守っているテストが 2 件しか無いと分かった。分岐ごとに固定する。
 */
public final class RefinementTest extends TestCase {
    /** 整数の範囲は `int` より精密。 */
    public void testPrefersAnIntegerRange() {
        assertEquals(List.of("array<string, int<20,20>>"),
                TypeUnionSimplifier.refineAgainst(List.of("array<string, int>"),
                        List.of("\\array<\\string,\\int<20,20>>")));
    }

    /** 狭い型キーワードは、その基底型より精密。 */
    public void testPrefersANarrowerKeyword() {
        assertEquals(List.of("list<positive-int>"),
                TypeUnionSimplifier.refineAgainst(List.of("list<int>"), List.of("list<positive-int>")));
        assertEquals(List.of("list<non-empty-string>"),
                TypeUnionSimplifier.refineAgainst(List.of("list<string>"),
                        List.of("list<non-empty-string>")));
    }

    /** 空でないと分かっている方が精密。 */
    public void testPrefersANonEmptyCollection() {
        assertEquals(List.of("non-empty-list<int>"),
                TypeUnionSimplifier.refineAgainst(List.of("list<int>"), List.of("non-empty-list<int>")));
    }

    /** 構造が違えば置き換えない。要素だけが精密な組にしか働かない。 */
    public void testDoesNotCrossStructures() {
        assertEquals(List.of("array<string, int>"),
                TypeUnionSimplifier.refineAgainst(List.of("array<string, int>"),
                        List.of("list<int<20,20>>")));
        assertEquals(List.of("list<int>"),
                TypeUnionSimplifier.refineAgainst(List.of("list<int>"), List.of("list<string>")));
    }

    /** 引数の数が違う総称型は比べない。 */
    public void testDoesNotCompareDifferentArity() {
        assertEquals(List.of("array<int>"),
                TypeUnionSimplifier.refineAgainst(List.of("array<int>"),
                        List.of("array<string, int<1,1>>")));
    }
}
