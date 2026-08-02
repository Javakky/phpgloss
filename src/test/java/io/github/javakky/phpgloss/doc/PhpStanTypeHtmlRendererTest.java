package io.github.javakky.phpgloss.doc;

import io.github.javakky.phpgloss.model.PhpStanResolvedType;
import io.github.javakky.phpgloss.model.SourceRange;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PhpStanTypeHtmlRendererTest {
    private final PhpStanTypeHtmlRenderer renderer = new PhpStanTypeHtmlRenderer();

    @Test
    public void escapesHtmlAndKeepsMultilineBody() {
        String html = renderer.render(new PhpStanResolvedType(
                "User<Data>",
                "User<Data>",
                "array{\n    key: \"<&'\\\"\"\n}",
                "App\\User",
                new SourceRange(0, 4),
                false,
                false,
                false
        ), null);
        assertTrue(html.contains("User&lt;Data&gt;"));
        assertTrue(html.contains("&quot;&lt;&amp;&#39;"));
        assertTrue(html.contains("<pre><code>array{\n    key:"));
    }

    @Test
    public void rendersImportedMetadata() {
        String html = renderer.render(new PhpStanResolvedType(
                "CreateUserData",
                "UserData",
                "array{name: string}",
                "App\\Domain\\User",
                new SourceRange(0, 4),
                true,
                false,
                false
        ), null);
        assertTrue(html.contains("Imported type: UserData"));
        // 定義元クラスはリンクになる
        assertTrue(html, html.contains("psi_element://\\App\\Domain\\User"));
        assertTrue(html, html.contains(">App\\Domain\\User</a>"));
    }
}

