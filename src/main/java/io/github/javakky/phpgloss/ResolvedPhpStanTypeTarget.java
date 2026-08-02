package io.github.javakky.phpgloss;

import com.intellij.openapi.vfs.VirtualFile;
import io.github.javakky.phpgloss.model.PhpStanResolvedType;
import io.github.javakky.phpgloss.parser.DetectedTypeReference;

/**
 * @param variableName 変数にキャレットがある場合の変数名 (`$` を除く)。それ以外は null。
 *                     PHP の変数ドキュメントに合わせて `$payload: CreateUserData` の形で出すために持つ
 */
public record ResolvedPhpStanTypeTarget(
        PhpStanResolvedType resolvedType,
        DetectedTypeReference reference,
        VirtualFile definitionFile,
        String variableName
) {
    public ResolvedPhpStanTypeTarget(
            PhpStanResolvedType resolvedType,
            DetectedTypeReference reference,
            VirtualFile definitionFile
    ) {
        this(resolvedType, reference, definitionFile, null);
    }
}

