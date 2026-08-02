package dev.javakky.phpstantypedoc;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 公開してよい状態であること。
 *
 * <p>作者の作業環境や、開発に使った他のプロジェクトの名前は、公開物に混ざってはいけない。
 * 一度取り除いても、後から書いたスクリプトやテストで戻る — 実際に戻った。人が気をつけるのでは
 * 続かないので、ここで検出する。
 */
public final class PublishabilityTest extends TestCase {
    /** 作業環境を指す形。ホームディレクトリの絶対パスと、この作者の作業ディレクトリ名。 */
    private static final List<Pattern> ENVIRONMENT = List.of(
            Pattern.compile("/Users/[a-z]+/"),
            Pattern.compile("/home/[a-z]+/"),
            Pattern.compile("C:\\\\Users\\\\"),
            Pattern.compile("the workspace"));

    /**
     * かつて混ざっていた、他のプロジェクト由来の名前。
     *
     * <p>この一覧を持つこと自体が、その名前を公開物に残す。それでも列挙するのは、**一度混ざった
     * ものは再び混ざる**ため — 実際、取り除いた後に書いたスクリプトで作業環境のパスが戻った。
     * ここに並ぶのは検出のための語であって、この plugin がそれらに依存しているわけではない。
     */
    private static final List<Pattern> OTHER_PROJECTS = List.of(
            Pattern.compile("(?i)the sample project"),
            Pattern.compile("Example"),
            Pattern.compile("(?i)dic\\.pixiv"),
            Pattern.compile("row_shape"),
            Pattern.compile("RecordPort"));

    /** 作業管理の識別子。タスク ID やその置き場。 */
    private static final List<Pattern> TASK_TRACKING = List.of(
            Pattern.compile("\\bPT-\\d{4}\\b"),
            Pattern.compile("\\bruns/"));

    public void testNoTraceOfTheAuthorsEnvironment() throws IOException {
        assertClean("作業環境が読み取れる", ENVIRONMENT);
    }

    public void testNoTraceOfOtherProjects() throws IOException {
        assertClean("他のプロジェクトの名前が残っている", OTHER_PROJECTS);
    }

    public void testNoTraceOfTaskTracking() throws IOException {
        assertClean("作業管理の識別子が残っている", TASK_TRACKING);
    }

    private void assertClean(String what, List<Pattern> patterns) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : List.of(Path.of("src"), Path.of("tools"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    if (path.toString().contains("/vendored/")
                            || path.getFileName().toString().equals("PublishabilityTest.java")) {
                        // 同梱した外部ソースはそのまま。この検査自体はパターンを持つので除く
                        continue;
                    }
                    String text = Files.readString(path);
                    for (Pattern pattern : patterns) {
                        var matcher = pattern.matcher(text);
                        if (matcher.find()) {
                            hits.add(path + " -> " + matcher.group());
                        }
                    }
                }
            }
        }
        for (String file : List.of("README.md", "CHANGELOG.md", "NOTICE", "build.gradle.kts")) {
            Path path = Path.of(file);
            if (!Files.exists(path)) {
                continue;
            }
            String text = Files.readString(path);
            for (Pattern pattern : patterns) {
                var matcher = pattern.matcher(text);
                if (matcher.find()) {
                    hits.add(file + " -> " + matcher.group());
                }
            }
        }
        assertTrue(what + ":\n  " + String.join("\n  ", hits), hits.isEmpty());
    }
}
