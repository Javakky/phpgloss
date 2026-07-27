package dev.javakky.phpstantypedoc.resolution;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ClassNameResolverTest {
    private final ClassNameResolver resolver = new ClassNameResolver();

    @Test
    public void resolvesFullyQualifiedClassName() {
        assertEquals("App\\Domain\\User", resolver.resolve("\\App\\Domain\\User", "App\\Service", Map.of()));
    }

    @Test
    public void resolvesCurrentNamespace() {
        assertEquals("App\\Service\\User", resolver.resolve("User", "App\\Service", Map.of()));
    }

    @Test
    public void resolvesUseStatement() {
        assertEquals("App\\Domain\\User", resolver.resolve("User", "App\\Service", Map.of("User", "App\\Domain\\User")));
    }

    @Test
    public void resolvesUseAlias() {
        assertEquals("App\\Domain\\User", resolver.resolve("DomainUser", "App\\Service", Map.of("DomainUser", "App\\Domain\\User")));
    }
}

