package dev.javakky.phpstantypedoc.doc;

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

    private PhpStanTypeDocumentationLink() {
    }

    static String forClass(String classFqn) {
        String normalizedFqn = classFqn.startsWith("\\") ? classFqn : "\\" + classFqn;
        return PSI_ELEMENT_PROTOCOL + normalizedFqn;
    }
}
