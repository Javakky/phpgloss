package dev.javakky.phpstantypedoc;

import com.intellij.openapi.vfs.VirtualFile;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.parser.DetectedTypeReference;

public record ResolvedPhpStanTypeTarget(
        PhpStanResolvedType resolvedType,
        DetectedTypeReference reference,
        VirtualFile definitionFile
) {
}

