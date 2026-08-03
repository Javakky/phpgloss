package io.github.javakky.phpgloss.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class PhpDocAliasParserTest {
    private final PhpDocAliasParser parser = new PhpDocAliasParser();

    @Test
    public void parsesSingleLineAlias() {
        ParsedPhpDoc doc = parser.parse("/** @phpstan-type UserId positive-int */");
        assertEquals(1, doc.aliases().size());
        assertEquals("UserId", doc.aliases().getFirst().aliasName());
        assertEquals("positive-int", doc.aliases().getFirst().typeExpression());
    }

    @Test
    public void parsesNestedMultilineShapeKeepingRelativeIndentation() {
        ParsedPhpDoc doc = parser.parse("""
                /**
                 * @phpstan-type UserData array{
                 *     id: positive-int,
                 *     name: non-empty-string,
                 *     address?: array{
                 *         postalCode: string,
                 *         city: string
                 *     }
                 * }
                 */
                """);
        assertEquals(1, doc.aliases().size());
        assertTrue(doc.aliases().getFirst().typeExpression().contains("    address?: array{"));
        assertTrue(doc.aliases().getFirst().typeExpression().contains("        city: string"));
        assertTrue(doc.aliases().getFirst().typeExpression().endsWith("}"));
    }

    @Test
    public void parsesComplexTypeExpression() {
        ParsedPhpDoc doc = parser.parse("""
                /**
                 * @phpstan-type Complex array{'quoted-key'?: list<callable(int): string>&Traversable}|null
                 */
                """);
        assertEquals(1, doc.aliases().size());
        assertTrue(doc.aliases().getFirst().typeExpression().contains("'quoted-key'?:"));
        assertTrue(doc.aliases().getFirst().typeExpression().contains("list<callable(int): string>&Traversable"));
    }

    @Test
    public void parsesImportForms() {
        ParsedPhpDoc doc = parser.parse("""
                /**
                 * @phpstan-import-type UserData from User
                 * @phpstan-import-type Address from \\App\\Domain\\User
                 * @phpstan-import-type Payload from User as CreateUserData
                 */
                """);
        assertEquals(3, doc.imports().size());
        assertEquals("UserData", doc.imports().get(0).localName());
        assertEquals("\\App\\Domain\\User", doc.imports().get(1).sourceClassText());
        assertEquals("CreateUserData", doc.imports().get(2).localName());
    }

    @Test
    public void stopsAtNextTagAtDepthZero() {
        ParsedPhpDoc doc = parser.parse("""
                /**
                 * @phpstan-type UserData array{id: int}
                 * @param UserData $data
                 */
                """);
        assertEquals("array{id: int}", doc.aliases().getFirst().typeExpression());
    }

    @Test
    public void incompleteBracesAndQuotesDoNotThrow() {
        ParsedPhpDoc doc = parser.parse("""
                /**
                 * @phpstan-type Broken array{key: 'unterminated
                 * @phpstan-type Next int
                 */
                """);
        assertFalse(doc.aliases().isEmpty());
    }

    @Test
    public void parsesDirectAliasChainAsSeparateDefinitions() {
        ParsedPhpDoc doc = parser.parse("""
                /**
                 * @phpstan-type A B
                 * @phpstan-type B A
                 */
                """);

        assertEquals(2, doc.aliases().size());
        assertEquals("B", doc.aliases().get(0).typeExpression());
        assertEquals("A", doc.aliases().get(1).typeExpression());
    }

    /** Psalm の書き方でも同じエイリアスが読める。 */
    @Test
    public void parsesPsalmFlavouredTags() {
        ParsedPhpDoc parsed = parser.parse("""
                /**
                 * @psalm-type user_row = array{id: int, name: string}
                 * @psalm-import-type other_row from \\Vendor\\Port
                 */
                """);

        assertEquals(1, parsed.aliases().size());
        assertEquals("user_row", parsed.aliases().get(0).aliasName());
        assertEquals("array{id: int, name: string}", parsed.aliases().get(0).typeExpression());
        assertEquals(1, parsed.imports().size());
        assertEquals("other_row", parsed.imports().get(0).originalName());
    }

    /** PHPStan の書き方でも `=` を挟める。 */
    @Test
    public void parsesAnEqualsSignBetweenNameAndType() {
        ParsedPhpDoc parsed = parser.parse(
                "/** @phpstan-type row = array{id: int} */");

        assertEquals(1, parsed.aliases().size());
        assertEquals("array{id: int}", parsed.aliases().get(0).typeExpression());
    }

    /** 入れ子の総称型が深さで閉じる。 */
    @Test
    public void parsesNestedGenerics() {
        ParsedPhpDoc parsed = parser.parse(
                "/** @phpstan-type nested array<string, array<int, list<array{id: int}>>> */");

        assertEquals("array<string, array<int, list<array{id: int}>>>",
                parsed.aliases().get(0).typeExpression());
    }

    /** 条件付き型の中の `?` と `:` で切れない。 */
    @Test
    public void parsesAConditionalType() {
        ParsedPhpDoc parsed = parser.parse(
                "/** @phpstan-type picked ($mode is true ? list<int> : array<string, int>) */");

        assertEquals("($mode is true ? list<int> : array<string, int>)",
                parsed.aliases().get(0).typeExpression());
    }

    /** 改行が CRLF でも読める。 */
    @Test
    public void parsesWithCarriageReturns() {
        ParsedPhpDoc parsed = parser.parse(
                "/**\r\n * @phpstan-type row array{id: int}\r\n */");

        assertEquals(1, parsed.aliases().size());
        assertEquals("array{id: int}", parsed.aliases().get(0).typeExpression());
    }

    /** Psalm の綴りで、別名まで付けて持ち込む。 */
    @Test
    public void parsesPsalmImportWithAnAlias() {
        ParsedPhpDoc parsed = parser.parse("""
                /**
                 * @psalm-import-type user_row from Port as brought
                 */
                """);

        assertEquals(1, parsed.imports().size());
        assertEquals("user_row", parsed.imports().get(0).originalName());
        assertEquals("Port", parsed.imports().get(0).sourceClassText());
        assertEquals("brought", parsed.imports().get(0).localName());
    }
}
