package dev.javakky.phpstantypedoc.resolution;

import dev.javakky.phpstantypedoc.parser.PhpFileModel;

import java.util.Optional;

public interface PhpStanTypeSourceProvider {
    Optional<PhpFileModel.ClassLikeDocBlock> findClass(String fqcn, String aliasName);
}
