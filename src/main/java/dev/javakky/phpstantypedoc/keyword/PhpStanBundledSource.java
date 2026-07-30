package dev.javakky.phpstantypedoc.keyword;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 同梱した解析ツールのソースを、IDE から開ける実ファイルとして取り出す。
 *
 * <p>プロジェクトに Psalm / PHPStan が入っていなくても定義へ飛べるようにするため、
 * キーワードを列挙しているファイルだけを同梱している (どちらも MIT。ライセンス全文も同梱)。
 *
 * <p>jar の中を直接指すこともできるが、jar のパスを組み立てる手間と配布形態への依存が増える。
 * IDE のシステム領域へ一度書き出し、書き込み不可にして開く方が経路が短い。
 */
public final class PhpStanBundledSource {
    private static final Logger LOG = Logger.getInstance(PhpStanBundledSource.class);
    private static final String RESOURCE_DIR = "/dev/javakky/phpstantypedoc/vendored/";
    private static final String CACHE_DIR = "phpstan-type-documentation";

    private PhpStanBundledSource() {
    }

    public static Optional<VirtualFile> extract(String fileName) {
        try {
            Path target = PathManager.getSystemDir().resolve(CACHE_DIR).resolve(fileName);
            byte[] content = read(fileName);
            if (content == null) {
                return Optional.empty();
            }
            writeOnce(target, content);
            return Optional.ofNullable(
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target));
        } catch (IOException e) {
            LOG.warn("同梱ソースを取り出せない: " + fileName, e);
            return Optional.empty();
        }
    }

    public static Optional<String> text(String fileName) {
        byte[] content = read(fileName);
        return content == null ? Optional.empty() : Optional.of(new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] read(String fileName) {
        try (InputStream stream = PhpStanBundledSource.class.getResourceAsStream(RESOURCE_DIR + fileName)) {
            return stream == null ? null : stream.readAllBytes();
        } catch (IOException e) {
            LOG.warn("同梱ソースを読めない: " + fileName, e);
            return null;
        }
    }

    /**
     * 中身が変わっていなければ書き直さない。開いているエディタを毎回作り直させないため。
     */
    private static void writeOnce(Path target, byte[] content) throws IOException {
        if (Files.exists(target) && Files.size(target) == content.length) {
            return;
        }
        Files.createDirectories(target.getParent());
        target.toFile().setWritable(true);
        Files.write(target, content);
        // 読むためのファイルなので書き換えさせない
        target.toFile().setWritable(false);
    }
}
