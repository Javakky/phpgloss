package dev.javakky.phpstantypedoc.parser;

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
}
