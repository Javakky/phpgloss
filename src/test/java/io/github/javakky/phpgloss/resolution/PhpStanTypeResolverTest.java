package io.github.javakky.phpgloss.resolution;

import io.github.javakky.phpgloss.model.PhpStanResolvedType;
import io.github.javakky.phpgloss.parser.PhpFileModel;
import io.github.javakky.phpgloss.parser.PhpFileModelParser;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class PhpStanTypeResolverTest {
    private final PhpFileModelParser fileParser = new PhpFileModelParser();
    private final PhpStanTypeResolver resolver = new PhpStanTypeResolver(new ClassNameResolver());

    @Test
    public void localAliasHasPriorityOverImport() {
        Fixture fixture = fixture("""
                <?php
                namespace App;
                /**
                 * @phpstan-import-type UserData from Source
                 * @phpstan-type UserData array{id: int}
                 * @param UserData $data
                 */
                final class Target {}
                """);
        PhpStanResolvedType result = resolver.resolve("UserData", fixture.file(), fixture.firstClass(), fixture.provider());
        assertFalse(result.unresolved());
        assertFalse(result.imported());
        assertEquals("array{id: int}", result.typeBody());
    }

    @Test
    public void resolvesImportAndAliasedImport() {
        Fixture fixture = fixture("""
                <?php
                namespace App\\Service;
                use App\\Domain\\User;
                /**
                 * @phpstan-import-type UserData from User as CreateUserData
                 */
                final class Target {}
                """, """
                <?php
                namespace App\\Domain;
                /**
                 * @phpstan-type UserData array{name: string}
                 */
                final class User {}
                """);
        PhpStanResolvedType result = resolver.resolve("CreateUserData", fixture.file(), fixture.firstClass(), fixture.provider());
        assertFalse(result.unresolved());
        assertTrue(result.imported());
        assertEquals("UserData", result.originalName());
        assertEquals("App\\Domain\\User", result.definingClassFqcn());
    }

    @Test
    public void missingSourceIsUnresolved() {
        Fixture fixture = fixture("""
                <?php
                namespace App;
                /**
                 * @phpstan-import-type UserData from Missing
                 */
                final class Target {}
                """);
        assertTrue(resolver.resolve("UserData", fixture.file(), fixture.firstClass(), fixture.provider()).unresolved());
    }

    @Test
    public void circularImportIsDetected() {
        Fixture fixture = fixture("""
                <?php
                namespace App;
                /**
                 * @phpstan-import-type BType from B as AType
                 */
                final class A {}
                /**
                 * @phpstan-import-type AType from A as BType
                 */
                final class B {}
                """);
        PhpStanResolvedType result = resolver.resolve("AType", fixture.file(), fixture.firstClass(), fixture.provider());
        assertTrue(result.circular());
    }

    @Test
    public void directLocalAliasCycleIsDetected() {
        Fixture fixture = fixture("""
                <?php
                namespace App;
                /**
                 * @phpstan-type A B
                 * @phpstan-type B A
                 */
                final class Types {}
                """);

        PhpStanResolvedType result = resolver.resolve("A", fixture.file(), fixture.firstClass(), fixture.provider());

        assertTrue(result.circular());
    }

    @Test
    public void maxDepthIsDetected() {
        StringBuilder source = new StringBuilder("<?php\nnamespace App;\n");
        for (int i = 0; i < 35; i++) {
            source.append("/**\n")
                    .append(" * @phpstan-import-type T").append(i + 1).append(" from C").append(i + 1).append(" as T").append(i).append("\n")
                    .append(" */\n")
                    .append("final class C").append(i).append(" {}\n");
        }
        source.append("/** @phpstan-type T35 int */\nfinal class C35 {}\n");
        Fixture fixture = fixture(source.toString());
        PhpStanResolvedType result = resolver.resolve("T0", fixture.file(), fixture.firstClass(), fixture.provider());
        assertTrue(result.circular());
    }

    private Fixture fixture(String first, String... rest) {
        Map<String, PhpFileModel.ClassLikeDocBlock> classes = new HashMap<>();
        PhpFileModel firstFile = fileParser.parse(first);
        addClasses(classes, firstFile);
        for (String text : rest) {
            addClasses(classes, fileParser.parse(text));
        }
        PhpStanTypeSourceProvider provider = (fqcn, aliasName) -> Optional.ofNullable(classes.get(fqcn));
        return new Fixture(firstFile, firstFile.classLikeDocBlocks().getFirst(), provider);
    }

    private void addClasses(Map<String, PhpFileModel.ClassLikeDocBlock> classes, PhpFileModel file) {
        for (PhpFileModel.ClassLikeDocBlock block : file.classLikeDocBlocks()) {
            classes.put(block.fqcn(), block);
        }
    }

    private record Fixture(PhpFileModel file, PhpFileModel.ClassLikeDocBlock firstClass, PhpStanTypeSourceProvider provider) {
    }
}
