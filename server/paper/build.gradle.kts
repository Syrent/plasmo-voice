import su.plo.voice.extension.slibPlatform
import su.plo.voice.util.copyJarToRootProject

plugins {
    id("su.plo.voice.relocate")
    id("su.plo.voice.maven-publish")
}

group = "$group.server"

repositories {
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.sayandev.org/snapshots")
}

dependencies {
    compileOnly(libs.paper)
    compileOnly(libs.papi)
    compileOnly(libs.supervanish)
    compileOnly(libs.sayanvanish.api)
    compileOnly(libs.sayanvanish.bukkit)

    compileOnly("org.bstats:bstats-bukkit:${libs.versions.bstats.get()}")

    api(project(":server:common"))

    // shadow projects
    listOf(
        project(":api:common"),
        project(":api:server"),
        project(":api:server-proxy-common"),
        project(":server:common"),
        project(":server-proxy-common"),
        project(":common"),
        project(":protocol")
    ).forEach {
        shadow(it) { isTransitive = false }
    }

    // shadow external deps
    shadow(kotlin("stdlib-jdk8"))
    shadow(libs.kotlinx.coroutines)
    shadow(libs.kotlinx.coroutines.jdk8)
    shadow(libs.kotlinx.json)

    shadow(libs.opus.jni)
    shadow(libs.opus.concentus)
    shadow(libs.config)
    shadow(libs.crowdin) { isTransitive = false }
    shadow("org.bstats:bstats-bukkit:${libs.versions.bstats.get()}")

    slibPlatform(
        "spigot",
        libs.versions.slib.get(),
        implementation = ::compileOnly,
        shadow = ::shadow
    )
}

tasks {
    processResources {
        filesMatching(mutableListOf("plugin.yml", "paper-plugin.yml")) {
            expand(
                mutableMapOf(
                    "version" to version
                )
            )
        }
    }

    shadowJar {
        configurations = listOf(project.configurations.shadow.get())

        archiveBaseName.set("PlasmoVoice-Paper")
        archiveAppendix.set("")

        dependencies {
            exclude(dependency("org.slf4j:slf4j-api"))
            exclude("META-INF/**")
        }
    }

    build {
        dependsOn.add(shadowJar)

        doLast {
            copyJarToRootProject(shadowJar.get())
        }
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}
