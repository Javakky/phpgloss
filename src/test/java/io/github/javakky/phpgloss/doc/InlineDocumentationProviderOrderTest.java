package io.github.javakky.phpgloss.doc;

import com.intellij.platform.backend.documentation.InlineDocumentationProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * レンダリング表示 (罫線モード) の 2 経路それぞれで、この plugin の provider が勝つ位置に
 * 登録されていることを固定する。
 *
 * <p>{@code DocRenderPassFactory} は全 provider の項目を {@code Map<TextRange, Item>} に put するので
 * 同じ範囲なら後勝ちになる。一方 {@code findInlineDocumentation} は最初に非 null を返した provider で
 * 打ち切る。片端にしか登録しないと、表示される HTML は PHP 標準・リンクだけ自前という
 * 食い違った状態になる (0.23.0 までの実機症状)。
 */
public final class InlineDocumentationProviderOrderTest extends BasePlatformTestCase {
    public void testRegisteredAtBothEnds() {
        List<InlineDocumentationProvider> providers =
                InlineDocumentationProvider.EP_NAME.getExtensionList();

        assertInstanceOf(providers.getFirst(), PhpStanInlineDocumentationProvider.class);
        assertInstanceOf(providers.getLast(), PhpStanInlineDocumentationProvider.class);
    }

    /**
     * platform の互換実装 ({@code CompatibilityInlineDocumentationProvider}) も {@code order="last"} で
     * 登録されている。同着なので、こちらがその後ろに来ることを実際の並びで確認する。
     */
    public void testWinsOverPlatformCompatibilityProvider() {
        List<InlineDocumentationProvider> providers =
                InlineDocumentationProvider.EP_NAME.getExtensionList();

        int compatibility = -1;
        int ours = -1;
        for (int i = 0; i < providers.size(); i++) {
            String name = providers.get(i).getClass().getName();
            if (name.endsWith("CompatibilityInlineDocumentationProvider")) {
                compatibility = i;
            }
            if (providers.get(i) instanceof PhpStanInlineDocumentationProvider) {
                ours = i;
            }
        }

        assertTrue("互換実装が登録されていない", compatibility >= 0);
        assertTrue("互換実装より後ろに登録されていない"
                + " (compatibility=" + compatibility + ", ours=" + ours + ")", ours > compatibility);
    }
}
