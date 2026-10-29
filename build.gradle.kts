import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.rajendarreddyj.intellij.jsp"
version = "0.4.5"

java {
    toolchain {
        // Toolchain must be >= the target IDE's bytecode level (2024.2: 17, 2025.x: 21, 2026.x: 25).
        // Output stays Java 17 bytecode so the plugin still loads on 2024.2 (sinceBuild 242).
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("javaToolchain").getOrElse("21").toInt())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// Override with -PplatformType=IntellijIdea -PplatformVersion=2026.2.3 -PjavaToolchain=25 to build/test against a newer IDE.
val platformType = providers.gradleProperty("platformType").getOrElse("IntellijIdeaCommunity")
val platformVersion = providers.gradleProperty("platformVersion").getOrElse("2024.2.6")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IntelliJ 2025.3+ Community builds are only published as Maven artifacts: add -PuseInstaller=false.
        create(IntelliJPlatformType.valueOf(platformType), platformVersion) {
            useInstaller = providers.gradleProperty("useInstaller").map { it.toBoolean() }.getOrElse(true)
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
            sinceBuild = "242"
            // Open-ended: compatible with every release from 2024.2 onward (verified up to 2026.2).
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
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")
            create(IntelliJPlatformType.IntellijIdea, "2025.3")
            create(IntelliJPlatformType.IntellijIdea, "2026.2.3")
        }
        freeArgs = listOf("-mute", "TemplateWordInPluginId")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
