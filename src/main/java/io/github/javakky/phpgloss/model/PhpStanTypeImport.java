package io.github.javakky.phpgloss.model;

public record PhpStanTypeImport(
        String originalName,
        String sourceClassText,
        String localName,
        SourceRange originalNameRange,
        SourceRange sourceClassRange,
        SourceRange localNameRange,
        SourceRange declarationRange
) {
}

