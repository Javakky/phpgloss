package io.github.javakky.phpgloss.parser;

import io.github.javakky.phpgloss.model.SourceRange;

public record DetectedTypeReference(String name, SourceRange range) {
}

