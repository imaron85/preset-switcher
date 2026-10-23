pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    // https://stonecutter.kikugie.dev/
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies fabric-loom-remap (obfuscated Minecraft) or fabric-loom (26.1+) as needed
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        /**
         * One node per published jar: `versions/{project}-{loader}`, built with `build.{loader}.gradle.kts`.
         * [project] is the Minecraft version the jar is compiled against; the Minecraft range each jar
         * supports is set in `stonecutter.properties.toml` (`mod.mc_compat` / `mod.mc_releases`).
         */
        fun match(project: String, vararg loaders: String) {
            for (loader in loaders) version("$project-$loader", project).buildscript("build.$loader.gradle.kts")
        }

        match("1.20.1", "fabric", "forge")
        match("1.20.2", "fabric")
        match("1.20.4", "fabric")
        match("1.20.6", "fabric")
        match("1.21.1", "fabric", "neoforge")
        match("1.21.8", "fabric", "neoforge")
        match("1.21.10", "fabric", "neoforge")
        match("26.1.2", "fabric", "neoforge")
        match("26.2", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "preset-switcher"
