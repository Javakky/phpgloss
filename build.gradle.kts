import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.javakky"
version = "0.27.0"

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
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaUltimate, platformVersion)
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
    }
}
