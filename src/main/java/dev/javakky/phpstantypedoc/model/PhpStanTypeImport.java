package dev.javakky.phpstantypedoc.model;

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

