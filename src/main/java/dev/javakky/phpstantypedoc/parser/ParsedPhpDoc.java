package dev.javakky.phpstantypedoc.parser;

import dev.javakky.phpstantypedoc.model.PhpStanTypeAliasDefinition;
import dev.javakky.phpstantypedoc.model.PhpStanTypeImport;

import java.util.List;

public record ParsedPhpDoc(
        List<PhpStanTypeAliasDefinition> aliases,
        List<PhpStanTypeImport> imports
) {
}

