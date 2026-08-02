package io.github.javakky.phpgloss.model;

public record PhpStanTypeAliasDefinition(
        String aliasName,
        String typeExpression,
        String containingClassFqcn,
        SourceRange aliasNameRange,
        SourceRange declarationRange,
        SourceRange typeBodyRange
) {
}

