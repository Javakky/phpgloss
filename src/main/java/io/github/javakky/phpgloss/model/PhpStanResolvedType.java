package io.github.javakky.phpgloss.model;

public record PhpStanResolvedType(
        String requestedName,
        String originalName,
        String typeBody,
        String definingClassFqcn,
        SourceRange definitionNavigationRange,
        boolean imported,
        boolean circular,
        boolean unresolved
) {
    public static PhpStanResolvedType unresolved(String requestedName) {
        return new PhpStanResolvedType(requestedName, requestedName, "", null, null, false, false, true);
    }

    public static PhpStanResolvedType circular(String requestedName, String originalName, String definingClassFqcn) {
        return new PhpStanResolvedType(requestedName, originalName, "", definingClassFqcn, null, true, true, false);
    }
}

