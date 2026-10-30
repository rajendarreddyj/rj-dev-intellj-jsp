import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.rajendarreddyj.intellij.jsp"
version = "0.4.6"

java {
    toolchain {
        // The IntelliJ 2026.2 platform requires JDK 25; plugin output remains Java 17 bytecode.
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("javaToolchain").getOrElse("25").toInt())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// Override these properties to build or test against another supported IDE release.
val platformType = providers.gradleProperty("platformType").getOrElse("IntellijIdeaCommunity")
val platformVersion = providers.gradleProperty("platformVersion").getOrElse("2026.2.3")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IntelliJ 2025.3+ Community builds are only published as Maven artifacts.
        create(IntelliJPlatformType.valueOf(platformType), platformVersion) {
            useInstaller = providers.gradleProperty("useInstaller").map { it.toBoolean() }.getOrElse(false)
        }
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.JUnit5)
    }

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            // Open-ended: compatible with every release from IntelliJ IDEA 2026.2 onward.
            untilBuild = provider { null }
        }
    }
    // Credentials come from the environment; never commit them.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2026.2.3")
        }
        freeArgs = listOf("-mute", "TemplateWordInPluginId")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
