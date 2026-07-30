package dev.javakky.phpstantypedoc.doc;

import java.util.Optional;

/**
 * Quick Documentation 内で使うリンク。
 *
 * <p>リンク先はクラスの FQN だけにする。独自 scheme は documentation renderer から link handler
 * へ渡らず、{@code psi_element://} に fragment を付ける形は platform が先に PSI 解決を試みて
 * 「ドキュメントが見つかりません」になる。どちらも実機で確認した。素の FQN なら platform の
 * 解決がそのまま通り、alias は定義元クラスの PHPDoc として読める。
 */
final class PhpStanTypeDocumentationLink {
    static final String PSI_ELEMENT_PROTOCOL = "psi_element://";
    private static final String TYPE_ALIAS_FRAGMENT = "#phpstan-type=";

    private PhpStanTypeDocumentationLink() {
    }

    static String forClass(String classFqn) {
        return PSI_ELEMENT_PROTOCOL + normalize(classFqn);
    }

    /**
     * エイリアスへ遷移するリンク。owner クラスの FQN に alias 名を fragment として付ける。
     * この plugin の documentation target から出たリンクは、platform 標準の PSI handler が
     * 対象外とするのでこの plugin の handler が処理する。
     */
    static String forAlias(String ownerClassFqn, String aliasName) {
        return PSI_ELEMENT_PROTOCOL + normalize(ownerClassFqn) + TYPE_ALIAS_FRAGMENT + aliasName;
    }

    static Optional<AliasReference> parseAlias(String url) {
        if (!url.startsWith(PSI_ELEMENT_PROTOCOL)) {
            return Optional.empty();
        }
        int fragment = url.indexOf(TYPE_ALIAS_FRAGMENT, PSI_ELEMENT_PROTOCOL.length());
        if (fragment < 0) {
            return Optional.empty();
        }
        String ownerClassFqn = url.substring(PSI_ELEMENT_PROTOCOL.length(), fragment);
        String aliasName = url.substring(fragment + TYPE_ALIAS_FRAGMENT.length());
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
