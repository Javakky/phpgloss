package dev.javakky.phpstantypedoc.doc;

import com.intellij.psi.PsiFile;

/**
 * テストから「変数のホバーに出る型」を測るための入口。表示の判定は package-private の
 * target が持っているため、そこだけを開ける。
 */
public final class VariableHoverProbe {
    private VariableHoverProbe() {
    }

    /** ホバーに出る型。plugin が介入しないときは空。 */
    public static String typeAt(PsiFile file, int offset) {
        return SimplifiedVariableTypeTarget.at(file, offset)
                .map(SimplifiedVariableTypeTarget::typeText)
                .orElse("");
    }
}
