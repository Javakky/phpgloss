package dev.javakky.phpstantypedoc;

import com.intellij.openapi.vfs.VirtualFile;
import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.parser.DetectedTypeReference;

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

    /** 型本体を出す形へ切り替えた値を返す。変数表示のリンクから展開するときに使う。 */
    public ResolvedPhpStanTypeTarget expanded() {
        return new ResolvedPhpStanTypeTarget(resolvedType, reference, definitionFile, null);
    }
}

