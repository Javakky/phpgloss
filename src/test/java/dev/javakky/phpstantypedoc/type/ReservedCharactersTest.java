package dev.javakky.phpstantypedoc.type;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 型のキーと符号化の区切りに使う文字が、互いに重ならないこと。
 *
 * <p>プロバイダのキーは型の先頭に付き、区切りはその後ろに現れる。同じ文字を両方に使うと、
 * 型の中の区切りをキーと見分けられない。実際に `ᨭ` が両方に使われていた。
 */
public final class ReservedCharactersTest extends TestCase {
    private static final Pattern CHARACTER = Pattern.compile("char (\\w+) = '(.)'");
    private static final Pattern INLINE_KEY = Pattern.compile("getKey\\(\\) \\{\\s*return '(.)'", Pattern.DOTALL);

    public void testEveryReservedCharacterIsUsedOnce() throws IOException {
        Map<String, List<String>> byCharacter = new LinkedHashMap<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String owner = path.getFileName().toString().replace(".java", "");
                String text = Files.readString(path);
                Matcher declared = CHARACTER.matcher(text);
                while (declared.find()) {
                    byCharacter.computeIfAbsent(declared.group(2), key -> new ArrayList<>())
                            .add(owner + "." + declared.group(1));
                }
                Matcher inline = INLINE_KEY.matcher(text);
                while (inline.find()) {
                    byCharacter.computeIfAbsent(inline.group(1), key -> new ArrayList<>())
                            .add(owner + ".getKey");
                }
            }
        }
        List<String> collisions = byCharacter.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .toList();
        assertTrue("同じ文字を二つの用途で使っている: " + collisions, collisions.isEmpty());
    }
}
