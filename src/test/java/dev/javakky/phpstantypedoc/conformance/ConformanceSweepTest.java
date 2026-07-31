package dev.javakky.phpstantypedoc.conformance;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.php.lang.psi.elements.PhpTypedElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import dev.javakky.phpstantypedoc.type.TypeUnionSimplifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHPStan を正解として、実プロジェクトの全変数代入の推論結果を突き合わせる。
 *
 * <p>手で見つけた漏れを潰すだけでは、こちらが気付いていない漏れが残る。正解は
 * {@code tools/conformance/extract.py} が採る (ソースは変更せず PHPStan のルールで報告させる)。
 *
 * <p>正解ファイルと対象プロジェクトが無い環境では何も検査しない。実プロジェクトへの依存を
 * ビルドの前提にしないため。
 */
public final class ConformanceSweepTest extends BasePlatformTestCase {
    /**
     * 走査先は差し替えられる。既定はこの plugin と同じ hobby の the sample project で、
     * 他のプロジェクト (context の違うものを含む) を見るときは -D で渡す。
     * その場合、レポートの置き場も呼び出し側が指定する。
     */
    private static final Path ANSWERS = Path.of(System.getProperty("conformance.answers",
            System.getProperty("user.home") + "/tools/conformance/out/project-types.json"));
    private static final Path PROJECT = Path.of(System.getProperty("conformance.project",
            System.getProperty("user.home") + "/projects/the sample project"));
    private static final Path REPORT = Path.of(System.getProperty("conformance.report",
            System.getProperty("user.home") + "/tools/conformance/out/sweep-report.md"));
    /** 走査するディレクトリ。プロジェクトによって置き場が違う。 */
    private static final List<String> DIRECTORIES =
            List.of(System.getProperty("conformance.directories", "src,tests").split(","));

    public void testSweepsEveryAssignmentAgainstPhpStan() throws IOException {
        if (!Files.exists(ANSWERS) || !Files.isDirectory(PROJECT)) {
            return;
        }
        List<Answer> answers = Answer.parse(Files.readString(ANSWERS));
        Map<String, PsiFile> loaded = loadSources();

        List<String> rows = new ArrayList<>();
        int matched = 0;
        int silent = 0;
        int conflicting = 0;
        int skipped = 0;
        for (Answer answer : answers) {
            PsiFile file = loaded.get(answer.file());
            if (file == null) {
                continue;
            }
            Optional<String> ours;
            try {
                ours = typeAt(file, answer.line(), answer.variable());
            } catch (Exception | AssertionError failure) {
                // fixture には vendor が無いので、PHP 標準の provider が解けずに落ちることがある
                // (実測: array_map で TypeProvider contract violation)。走査は続ける
                skipped++;
                continue;
            }
            if (ours.isEmpty()) {
                continue;
            }
            Verdict verdict = Verdict.of(ours.get(), answer.type());
            switch (verdict) {
                case MATCH -> matched++;
                case SILENT -> silent++;
                case CONFLICT -> conflicting++;
            }
            if (verdict != Verdict.MATCH) {
                rows.add("| " + verdict + " | " + answer.file() + ":" + answer.line()
                        + " | $" + answer.variable() + " | " + escape(answer.type()) + " | " + escape(ours.get()) + " |");
            }
        }
        rows.sort(Comparator.naturalOrder());
        Files.writeString(REPORT, """
                # 適合スイープ — PHPStan を正解とした照合

                対象: the sample project (全変数代入)。正解は tools/conformance/extract.py が採取。

                - 一致 (構造が同じか、こちらがより精密): %d
                - 沈黙 (PHPStan は解けているがこちらは抽象型のまま): %d
                - **矛盾 (構造が食い違う。バグの候補)**: %d
                - 測れず (fixture 環境で PHP 標準の解決が落ちた): %d

                沈黙のうち、次の 2 つはこちらの漏れではない。

                - **上界の無い型変数** (`@template E` のみ) — PHPStan は `E (function ...)` と型変数の
                  ままを表示するが、PhpStorm の型には型変数を表す形が無い。上界があればそこまでは
                  言えるので (`@template G of array-key` は int と string)、無いものだけが残る
                - **PHP 標準関数の戻り値** (`array_key_first` / `array_is_list` / `fopen` 等) —
                  fixture には PHP の stub が無いため、この走査では測れないだけ。実機では PhpStorm が解く

                | 判定 | 位置 | 変数 | PHPStan | このプラグイン |
                |---|---|---|---|---|
                %s
                """.formatted(matched, silent, conflicting, skipped, String.join("\n", rows)));

        if (Boolean.getBoolean("conformance.reportOnly")) {
            return;
        }
        assertEquals("PHPStan と構造が食い違う推論がある。詳細は " + REPORT, 0, conflicting);
    }

    /** 表の区切りと衝突する `|` を逃がす。 */
    private static String escape(String type) {
        return type.replace("|", "\\\\|");
    }

    /** the sample project の src / tests を fixture に載せる。 */
    private Map<String, PsiFile> loadSources() throws IOException {
        Map<String, PsiFile> loaded = new LinkedHashMap<>();
        for (String directory : DIRECTORIES) {
            Path root = PROJECT.resolve(directory);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".php")).toList()) {
                    String relative = PROJECT.relativize(path).toString();
                    try {
                        loaded.put(relative,
                                myFixture.addFileToProject(relative, Files.readString(path)));
                    } catch (Exception failure) {
                        // 読めないファイルがあっても走査は続ける
                    }
                }
            }
        }
        return loaded;
    }

    /** 指定行に代入されている変数の、こちらが出す型。 */
    private Optional<String> typeAt(PsiFile file, int line, String variable) {
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        if (document == null || line > document.getLineCount()) {
            return Optional.empty();
        }
        int start = document.getLineStartOffset(line - 1);
        int end = document.getLineEndOffset(line - 1);
        return PsiTreeUtil.findChildrenOfType(file, Variable.class).stream()
                .filter(v -> variable.equals(v.getName()))
                .filter(v -> v.getTextRange().getStartOffset() >= start
                        && v.getTextRange().getStartOffset() <= end)
                .findFirst()
                .map(v -> {
                    // 表示に出る型で測る。plugin が介入しないときは PhpStorm の型がそのまま出る
                    String shown = dev.javakky.phpstantypedoc.doc.VariableHoverProbe.typeAt(
                            file, v.getTextRange().getStartOffset() + 1);
                    return shown.isEmpty()
                            ? String.join("|", TypeUnionSimplifier.normalize(
                                    ((PhpTypedElement) v).getType().global(getProject())
                                            .getTypesWithParametrisedParts()))
                            : shown;
                });
    }

    private enum Verdict {
        MATCH, SILENT, CONFLICT;

        /**
         * 判定は「両立するか」で行う。PHPStan は値レベル (`array{1, 2, 3}` / `'null:null'`) まで
         * 追跡するので、こちらの型がその上位 (`array<int, int>` / `string`) であることは正しく、
         * 矛盾ではない。矛盾と呼ぶのは、両者が同じ値を指しえない場合だけにする。
         */
        static Verdict of(String ours, String theirs) {
            Set<Shape> mine = Shape.setOf(ours);
            Set<Shape> expected = Shape.setOf(theirs);
            if (mine.isEmpty()) {
                // 型が付かなかった。PHPStan が解けているなら、こちらの取りこぼし
                return expected.isEmpty() ? MATCH : SILENT;
            }
            if (expected.isEmpty()) {
                return MATCH;
            }
            // union は「どれかであり得る」。ひとつでも両立する組があれば同じ値を指しうる
            for (Shape a : mine) {
                for (Shape b : expected) {
                    if (a.compatibleWith(b)) {
                        return MATCH;
                    }
                }
            }
            return CONFLICT;
        }
    }

    /** 比べられる粒度に落とした型の形。 */
    private enum Shape {
        /** 連番キーと分かっている配列 (`list<X>` / `array{1, 2}`) */
        SEQUENTIAL,
        /** 整数キーの配列。連番かどうかは分からない */
        INT_KEYED,
        /** 文字列キーを含む配列 */
        STRING_KEYED,
        /** キーの分からない配列 */
        ANY_ARRAY,
        SCALAR, BOOL, NULL_ONLY, UNKNOWN;

        /** 同じ値を指しうるか。片方がもう片方の上位でも真。 */
        boolean compatibleWith(Shape other) {
            if (this == other) {
                return true;
            }
            if (isArray() != other.isArray()) {
                // null は配列の union に混ざるため、片方が NULL_ONLY のときは矛盾と言い切れない
                return this == NULL_ONLY || other == NULL_ONLY;
            }
            if (!isArray()) {
                // スカラー同士。BOOL と SCALAR の食い違いだけを矛盾にする
                return this == NULL_ONLY || other == NULL_ONLY;
            }
            if (this == ANY_ARRAY || other == ANY_ARRAY) {
                return true;
            }
            // 連番は整数キーの一種。文字列キーとは両立しない
            return (this == SEQUENTIAL && other == INT_KEYED)
                    || (this == INT_KEYED && other == SEQUENTIAL);
        }

        boolean isArray() {
            return this == SEQUENTIAL || this == INT_KEYED || this == STRING_KEYED || this == ANY_ARRAY;
        }

        private static final Pattern LIST_LIKE = Pattern.compile("(^|\\|)\\\\?(non-empty-)?list<");
        private static final Pattern KEYED = Pattern.compile("(^|\\|)\\\\?(non-empty-)?array<([^,>]+),");

        /**
         * union を成分ごとに分けた形の集合。`null` と型の付かない成分は判定に使わない
         * (`non-empty-list|null` と `null|array` は同じ値を指しうる)。
         */
        static Set<Shape> setOf(String type) {
            Set<Shape> shapes = new java.util.LinkedHashSet<>();
            for (String member : splitUnion(type)) {
                Shape shape = of(member);
                if (shape != UNKNOWN && shape != NULL_ONLY) {
                    shapes.add(shape);
                }
            }
            return shapes;
        }

        /** 最上位の `|` で分ける。型引数や shape の中の `|` は区切りにしない。 */
        private static List<String> splitUnion(String type) {
            List<String> members = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (c == '<' || c == '{' || c == '(') {
                    depth++;
                } else if (c == '>' || c == '}' || c == ')') {
                    depth--;
                } else if (c == '|' && depth == 0) {
                    members.add(type.substring(start, i));
                    start = i + 1;
                }
            }
            members.add(type.substring(start));
            return members.stream().map(String::trim).filter(m -> !m.isEmpty()).toList();
        }

        static Shape of(String type) {
            String normalized = type.trim().toLowerCase().replace("\\", "");
            if (normalized.isEmpty() || normalized.startsWith("mixed") || normalized.equals("?")
                    || normalized.equals("null|mixed")) {
                // `mixed~object` は PHPStan の型減算。mixed の一種で、こちらは表せない
                return UNKNOWN;
            }
            if (normalized.equals("list") || normalized.equals("non-empty-list")) {
                return SEQUENTIAL;
            }
            if (normalized.equals("array") || normalized.equals("iterable")) {
                return ANY_ARRAY;
            }
            if (normalized.equals("null")) {
                return NULL_ONLY;
            }
            if (LIST_LIKE.matcher(normalized).find()) {
                return SEQUENTIAL;
            }
            if (normalized.startsWith("array{") || normalized.contains("|array{")) {
                return shapeOfLiteralArray(normalized);
            }
            Matcher keyed = KEYED.matcher(normalized);
            if (keyed.find()) {
                String key = keyed.group(3).trim();
                if (key.equals("int") || key.startsWith("int<") || key.matches("[0-9|]+")) {
                    return INT_KEYED;
                }
                return key.equals("array-key") || key.contains("mixed") ? ANY_ARRAY : STRING_KEYED;
            }
            if (normalized.contains("array") || normalized.endsWith("[]") || normalized.contains("[]|")) {
                return ANY_ARRAY;
            }
            if (normalized.startsWith("bool") || normalized.equals("true") || normalized.equals("false")) {
                return BOOL;
            }
            return SCALAR;
        }

        /** `array{1, 2, 3}` は連番、`array{a: 1}` は文字列キー。 */
        private static Shape shapeOfLiteralArray(String type) {
            int brace = type.indexOf('{');
            String body = type.substring(brace + 1, Math.max(brace + 1, type.lastIndexOf('}')));
            if (body.isBlank()) {
                return ANY_ARRAY;
            }
            // 外側のキーだけを見る。入れ子の shape と文字列リテラル (URL の `:` 等) の中は数えない
            String outer = body.replaceAll("'[^']*'", "''").replaceAll("\"[^\"]*\"", "''");
            String previous;
            do {
                previous = outer;
                outer = outer.replaceAll("\\{[^{}]*}", "");
            } while (!outer.equals(previous));
            if (!outer.contains(":")) {
                return SEQUENTIAL;
            }
            body = outer;
            // `array{10: 'a', 20: 'b'}` のように数字キーが書かれる。連番ではないが整数キーではある
            boolean allNumeric = true;
            for (String entry : body.split(",")) {
                int colon = entry.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                if (!entry.substring(0, colon).trim().matches("-?[0-9]+")) {
                    allNumeric = false;
                    break;
                }
            }
            return allNumeric ? INT_KEYED : STRING_KEYED;
        }
    }

    /** 正解ファイルの 1 行。 */
    private record Answer(String file, int line, String variable, String type) {
        private static final Pattern ROW = Pattern.compile(
                "\\{\\s*\"file\":\\s*\"(.*?)\",\\s*\"line\":\\s*(\\d+),"
                        + "\\s*\"var\":\\s*\"(.*?)\",\\s*\"type\":\\s*\"(.*?)\"\\s*}",
                Pattern.DOTALL);

        static List<Answer> parse(String json) {
            List<Answer> answers = new ArrayList<>();
            Matcher matcher = ROW.matcher(json);
            while (matcher.find()) {
                answers.add(new Answer(matcher.group(1), Integer.parseInt(matcher.group(2)),
                        matcher.group(3), matcher.group(4).replace("\\\\", "\\")));
            }
            return answers;
        }
    }
}
