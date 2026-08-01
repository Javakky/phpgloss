package dev.javakky.phpstantypedoc.type;

import junit.framework.TestCase;

import java.util.List;

/**
 * union の分割。開発対象の IDE には本家があるので、ここで測っているのは本家の答え。
 * 自前の実装は本家が無い IDE でだけ使われ、同じ答えを返すことを別のテストで固定する。
 */
public final class PhpTypeTextTest extends TestCase {
    public void testSplitsAtTheTopLevelOnly() {
        assertEquals(List.of("list<int|string>", "null"),
                PhpTypeText.splitTopLevelUnion("list<int|string>|null"));
        assertEquals(List.of("array{a: int|string}", "false"),
                PhpTypeText.splitTopLevelUnion("array{a: int|string}|false"));
        assertEquals(List.of("callable(int|string): bool"),
                PhpTypeText.splitTopLevelUnion("callable(int|string): bool"));
    }

    /**
     * 前後の空白は落とさない。本家の実測がそうなっており、落とすと本家のある IDE と
     * 挙動が割れる。空の成分だけは落ちる。
     */
    public void testMatchesUpstreamSpacingAndEmptyMembers() {
        assertEquals(List.of("int ", " null"), PhpTypeText.splitTopLevelUnion("int | null"));
        assertEquals(List.of("int"), PhpTypeText.splitTopLevelUnion("|int|"));
        assertEquals(List.of(""), PhpTypeText.splitTopLevelUnion(""));
    }

    public void testKeepsATypeWithoutAUnion() {
        assertEquals(List.of("\\App\\User"), PhpTypeText.splitTopLevelUnion("\\App\\User"));
    }

    /** 内部のエンコード形もそのまま通す。ここで壊すと索引経由の解決が静かに失敗する。 */
    public void testKeepsEncodedTypesIntact() {
        assertEquals(List.of("#ᢐV.", "\\null"),
                PhpTypeText.splitTopLevelUnion("#ᢐV.|\\null"));
    }

    /** 開発対象の IDE では本家に委ねる。自前の実装が常用されていないことを固定する。 */
    public void testDelegatesToThePhpPluginWhenAvailable() {
        assertTrue("本家の splitTopLevelUnion に委ねていない", PhpTypeText.delegatesToPhpPlugin());
    }

    /**
     * 自前の実装が本家と同じ答えを返すこと。古い IDE ではこちらが使われるので、
     * 差があると版によって推論が変わる。
     */
    public void testTheFallbackAgreesWithUpstream() {
        for (String type : new String[]{
                "list<int|string>|null", "array{a: int|string}|false", "callable(int|string): bool",
                "int | null", "|int|", "", "int", "\\App\\User", "#ᢐV.|\\null",
                "(int|string)|null", "array{a: array{b: int|null}}|int"}) {
            assertEquals("本家と自前で分け方が違う: " + type,
                    PhpTypeText.splitTopLevelUnion(type), PhpTypeText.splitHere(type));
        }
    }
}
