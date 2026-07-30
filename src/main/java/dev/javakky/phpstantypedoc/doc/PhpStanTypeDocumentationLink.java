package dev.javakky.phpstantypedoc.doc;

import java.util.Optional;

/**
 * Quick Documentation 内で使うリンク。
 *
 * <p>エイリアスへのリンクは独自 scheme を使う。{@code psi_element://} に alias 名を fragment として
 * 付ける形は platform 側の PSI 解決が先に走って失敗するため使えない。
 * {@code DocumentationLinkHandler} を回す platform の実装は URL を見ずに全 handler を試すので、
 * 独自 scheme でも {@link PhpStanTypeDocumentationLinkHandler} に届く。
 *
 * <p>クラスへのリンクは PHP plugin と同じ {@code psi_element://} を使う。
 */
final class PhpStanTypeDocumentationLink {
    static final String PSI_ELEMENT_PROTOCOL = "psi_element://";
    /**
     * alias を運ぶ区切り。独自 scheme は documentation の UI 層がリンクとして扱わないため、
     * {@code psi_element://} の中に埋め込む。`#` は platform の PSI 解決を壊すので使えない。
     */
    private static final String SEPARATOR = "::";

    private PhpStanTypeDocumentationLink() {
    }

    static String forClass(String classFqn) {
        return PSI_ELEMENT_PROTOCOL + normalize(classFqn);
    }

    /**
     * エイリアス自身の documentation へのリンク。
     *
     * @param ownerClassFqn エイリアスを宣言 / import しているクラス。ここを起点に解決する
     */
    static String forAlias(String ownerClassFqn, String aliasName) {
        return PSI_ELEMENT_PROTOCOL + normalize(ownerClassFqn) + SEPARATOR + aliasName;
    }

    static Optional<AliasReference> parseAlias(String url) {
        if (!url.startsWith(PSI_ELEMENT_PROTOCOL)) {
            return Optional.empty();
        }
        String body = url.substring(PSI_ELEMENT_PROTOCOL.length());
        int separator = body.indexOf(SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        String ownerClassFqn = body.substring(0, separator);
        String aliasName = body.substring(separator + SEPARATOR.length());
        if (ownerClassFqn.isBlank() || aliasName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AliasReference(ownerClassFqn, aliasName));
    }

    private static String normalize(String fqn) {
        return fqn.startsWith("\\") ? fqn : "\\" + fqn;
    }

    record AliasReference(String ownerClassFqn, String aliasName) {
    }
}
