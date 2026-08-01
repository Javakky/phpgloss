import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.javakky"
version = "0.69.0"

// 開発対象とプラグイン検証対象を同じ IDE ビルドに揃える。
val platformVersion = "2026.2.0.1"
val phpPluginVersion = "262.8665.176"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    intellijPlatform {
        intellijIdea(platformVersion)
        plugin("com.jetbrains.php", phpPluginVersion)
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.javakky.phpstan-type-documentation"
        name = "PHPStan Type Documentation"
        version = project.version.toString()

        ideaVersion {
            // 実測で 2025.2 (252) まで Compatible。上限は置かない —
            // 使っている API はどれも安定側で、新しい IDE を弾く理由が無い
            sinceBuild = "252"
        }
    }

    pluginVerification {
        ides {
            // PhpStorm が主戦場。PHP を本体に持つため、IDEA + PHP plugin とは構成が違う
            create(IntelliJPlatformType.PhpStorm, platformVersion)
            create(IntelliJPlatformType.IntellijIdeaUltimate, platformVersion)
            // 互換範囲を広げられるかの実測用。ここが通らない版は sinceBuild に含めない
            create(IntelliJPlatformType.PhpStorm, "2026.1")
            create(IntelliJPlatformType.PhpStorm, "2025.2")
            create(IntelliJPlatformType.PhpStorm, "2025.1")
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.release.set(25)
    }

    test {
        useJUnit()

        // クラス走査に任せるとテスト内の補助 record (PhpStanTypeResolverTest$Fixture) まで
        // テストクラス候補として拾われるため、`*Test` の命名規約で対象を確定させる。
        isScanForTestClasses = false
        include("**/*Test.class")

        // 適合スイープの走査先を差し替えられるようにする (既定は the sample project)。
        for (key in listOf("conformance.answers", "conformance.project", "conformance.report",
                "conformance.directories", "conformance.reportOnly", "perf.report")) {
            System.getProperty(key)?.let { systemProperty(key, it) }
        }
    }
}
