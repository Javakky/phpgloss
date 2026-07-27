package dev.javakky.phpstantypedoc.parser;

import dev.javakky.phpstantypedoc.model.SourceRange;

public record DetectedTypeReference(String name, SourceRange range) {
}

