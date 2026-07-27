package dev.javakky.phpstantypedoc.doc;

import dev.javakky.phpstantypedoc.model.PhpStanResolvedType;
import dev.javakky.phpstantypedoc.model.SourceRange;
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
        ));
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
        ));
        assertTrue(html.contains("Imported type: UserData"));
        assertTrue(html.contains("Defined in App\\Domain\\User"));
    }
}

