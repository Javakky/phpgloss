import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.javakky"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
        intellijIdea("2025.3.6")
        plugin("com.jetbrains.php", "253.32098.37")
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
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.release.set(21)
    }

    test {
        useJUnit()
    }
}
