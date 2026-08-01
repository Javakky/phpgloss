package dev.javakky.phpstantypedoc.type;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;

/**
 * 型の検証。**部分一致では足りない**。
 *
 * <p>`assertTrue(type.contains("list<int>"))` は `list<int>|mixed` でも通る。この plugin は
 * 精密な型を出すために在るので、余計な型が混ざるのは機能の失敗にあたる。実測では、解決結果に
 * `mixed` を足す変異を入れても 268 件中 266 件が通った。順序に依らない完全一致で見る。
 */
public final class TypeAssertions {
    private TypeAssertions() {
    }

    /**
     * @param expected `|` 区切りの期待する型。並び順は問わない
     * @param actual   実際の型 (`PhpType` の成分を `|` で繋いだもの)
     */
    public static void assertTypeIs(String expected, String actual) {
        assertEquals("型が一致しない", normalize(expected), normalize(actual));
    }

    public static void assertTypeIs(String message, String expected, String actual) {
        assertEquals(message, normalize(expected), normalize(actual));
    }

    /** 綴りの揺れ (`\int` と `int`、空白、順序) を落とした形。 */
    private static String normalize(String type) {
        List<String> members = TypeUnionSimplifier.normalize(
                Arrays.stream(type.split("\\|")).map(String::trim).filter(m -> !m.isEmpty()).toList());
        return members.stream().map(m -> m.replace(" ", "")).sorted().collect(Collectors.joining("|"));
    }
}
