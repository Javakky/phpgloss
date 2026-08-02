package io.github.javakky.phpgloss.keyword;

import java.util.List;
import java.util.Locale;

/**
 * PHPStan / Psalm の型キーワード一つ分の説明。
 *
 * @param tools    受け付けるツール (`psalm` / `phpstan`)
 * @param english  英語の説明
 * @param japanese 日本語の説明
 */
public record PhpStanTypeKeyword(
        String name,
        List<String> tools,
        String english,
        String japanese
) {
    /**
     * @return IDE の表示言語に合わせた説明。日本語が無ければ英語で出す
     */
    public String description(Locale locale) {
        boolean japaneseUi = locale != null && Locale.JAPANESE.getLanguage().equals(locale.getLanguage());
        if (japaneseUi && japanese != null && !japanese.isBlank()) {
            return japanese;
        }
        return english == null || english.isBlank() ? japanese : english;
    }
}
