package dev.javakky.phpstantypedoc.keyword;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 型キーワードの説明カタログ。
 *
 * <p>説明は `type-keywords.md` に置き、ここでは読むだけにする。文章を Java の中に持つと、
 * 追記のたびにコードを触ることになるため。
 *
 * <p>書式は `## <キーワード>` の見出しに続けて `tools:` 行と説明本文。
 */
public final class PhpStanTypeKeywords {
    private static final String RESOURCE = "/dev/javakky/phpstantypedoc/type-keywords.md";
    private static final String TOOLS_PREFIX = "tools:";
    private static final String ENGLISH_PREFIX = "en:";

    private static final Map<String, PhpStanTypeKeyword> KEYWORDS = load();

    private PhpStanTypeKeywords() {
    }

    public static Optional<PhpStanTypeKeyword> find(String name) {
        return Optional.ofNullable(KEYWORDS.get(name));
    }

    public static Set<String> names() {
        return KEYWORDS.keySet();
    }

    private static Map<String, PhpStanTypeKeyword> load() {
        Map<String, PhpStanTypeKeyword> keywords = new LinkedHashMap<>();
        for (String section : readCatalog().split("(?m)^## ")) {
            parse(section).ifPresent(keyword -> keywords.put(keyword.name(), keyword));
        }
        return Map.copyOf(keywords);
    }

    private static String readCatalog() {
        try (InputStream stream = PhpStanTypeKeywords.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("型キーワードのカタログが見つからない: " + RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("型キーワードのカタログを読めない: " + RESOURCE, e);
        }
    }

    /**
     * @param section 見出しの `## ` を除いた 1 節。先頭行がキーワード名
     */
    private static Optional<PhpStanTypeKeyword> parse(String section) {
        List<String> lines = section.lines().toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        String name = lines.getFirst().trim();
        // ファイル冒頭の説明部分は見出しを持たないので、キーワードとして拾わない
        if (name.isEmpty() || name.contains(" ")) {
            return Optional.empty();
        }
        List<String> tools = List.of();
        String english = "";
        List<String> japanese = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.startsWith(TOOLS_PREFIX)) {
                tools = List.of(line.substring(TOOLS_PREFIX.length()).trim().split(",\\s*"));
            } else if (line.startsWith(ENGLISH_PREFIX)) {
                english = line.substring(ENGLISH_PREFIX.length()).trim();
            } else {
                japanese.add(line);
            }
        }
        String text = String.join("\n", japanese).strip();
        if (text.isEmpty() && english.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PhpStanTypeKeyword(name, tools, english, text));
    }
}
