package io.github.javakky.phpgloss.parser;

import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

/**
 * ファイル単位の {@link PhpFileModel} をキャッシュする。
 *
 * <p>モデルの生成はファイル全体の正規表現パースなので、キー補完のように 1 打鍵ごとに
 * 呼ばれる経路では毎回作り直すと間に合わない。PSI が変わるまで使い回す。
 *
 * <p>キーは {@code getOriginalFile()}。補完中は PSI がコピーされたファイル上で動くため、
 * コピーをキーにするとキャッシュが毎回捨てられる。エイリアスの宣言はクラスの PHPDoc にあり
 * 補完位置の変更に影響されないので、元ファイルのモデルで足りる。
 */
public final class PhpFileModelCache {
    private static final PhpFileModelParser PARSER = new PhpFileModelParser();

    private PhpFileModelCache() {
    }

    public static PhpFileModel of(PsiFile psiFile) {
        PsiFile original = psiFile.getOriginalFile();
        return CachedValuesManager.getCachedValue(original, () ->
                CachedValueProvider.Result.create(PARSER.parse(original.getText()), original));
    }
}
