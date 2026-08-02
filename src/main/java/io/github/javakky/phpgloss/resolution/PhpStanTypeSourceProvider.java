package io.github.javakky.phpgloss.resolution;

import io.github.javakky.phpgloss.parser.PhpFileModel;

import java.util.Optional;

public interface PhpStanTypeSourceProvider {
    Optional<PhpFileModel.ClassLikeDocBlock> findClass(String fqcn, String aliasName);
}
