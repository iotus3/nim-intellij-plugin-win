import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.intellij.platform.grammarkit")
}

group = "com.laamella.nim"
version = "0.6.0"

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main { java.srcDir(layout.buildDirectory.dir("generated/sources/grammarkit-lexer/java/main")) }
}

dependencies {
    testImplementation(kotlin("test-junit"))

    intellijPlatform {
        intellijIdea("2026.1.1")
        bundledPlugin("com.intellij.java")
        plugin("com.redhat.devtools.lsp4ij:0.19.3")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks {
    named<GenerateLexerTask>("generateLexer") {
        sourceFile = file("src/main/flex/_NimLexer.flex")
    }
    compileKotlin { dependsOn("generateLexer") }
    compileJava { dependsOn("generateLexer") }
    runIde {
        jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
    }
}
