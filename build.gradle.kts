plugins {
    id("org.openjfx.javafxplugin") version "0.0.13" apply false
}

subprojects {
    group = "com.todo.desktop"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        google()
    }

    plugins.apply("java")

    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
