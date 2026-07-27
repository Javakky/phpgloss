package dev.javakky.phpstantypedoc.parser;

import dev.javakky.phpstantypedoc.model.PhpStanTypeAliasDefinition;
import dev.javakky.phpstantypedoc.model.PhpStanTypeImport;
import dev.javakky.phpstantypedoc.model.SourceRange;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record PhpFileModel(
        String namespace,
        Map<String, String> uses,
        List<ClassLikeDocBlock> classLikeDocBlocks
) {
    public Optional<ClassLikeDocBlock> findDocBlockContainingOffset(int offset) {
        return classLikeDocBlocks.stream()
                .filter(block -> block.docRange().containsExclusiveEnd(offset))
                .findFirst();
    }

    public Optional<ClassLikeDocBlock> findClass(String fqcn) {
        return classLikeDocBlocks.stream()
                .filter(block -> block.fqcn().equals(fqcn))
                .findFirst();
    }

    public record ClassLikeDocBlock(
            String fqcn,
            String shortName,
            String namespace,
            Map<String, String> uses,
            SourceRange docRange,
            String rawDoc,
            List<PhpStanTypeAliasDefinition> aliases,
            List<PhpStanTypeImport> imports
    ) {
    }
}
