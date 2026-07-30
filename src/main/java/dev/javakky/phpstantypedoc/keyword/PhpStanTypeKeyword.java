package dev.javakky.phpstantypedoc.keyword;

import java.util.List;

/**
 * PHPStan / Psalm の型キーワード一つ分の説明。
 *
 * @param tools 受け付けるツール (`psalm` / `phpstan`)
 */
public record PhpStanTypeKeyword(String name, List<String> tools, String description) {
}
