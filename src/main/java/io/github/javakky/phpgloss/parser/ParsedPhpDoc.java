package io.github.javakky.phpgloss.parser;

import io.github.javakky.phpgloss.model.PhpStanTypeAliasDefinition;
import io.github.javakky.phpgloss.model.PhpStanTypeImport;

import java.util.List;

public record ParsedPhpDoc(
        List<PhpStanTypeAliasDefinition> aliases,
        List<PhpStanTypeImport> imports
) {
}

