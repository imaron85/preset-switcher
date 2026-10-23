plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
    id("presets-common")
}

@Suppress("UNCHECKED_CAST")
val metadata = extra["presetsMetadata"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val jarJarMetadata = extra["jarJarMetadata"] as (String) -> String
val modVersion = property("mod.version").toString()
/*
 * Early hook: FancyModLoader does not load jars that declare early services as mods, so the hook (an
 * IModLanguageLoader, created after mod discovery and before any mixin config or mod config is loaded) and the core
 * live in a library jar nested in the mod jar (jar-in-jar).
 */
neoForge {
    // Only the binary-patched game is needed to compile; skips decompiling/recompiling Minecraft
    enable {
        version = property("deps.neo_loader").toString()
        isDisableRecompilation = true
    }
    mods {
        register(property("mod.id").toString()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val core = extra["coreSourceSet"] as SourceSet
sourceSets.main {
    java.exclude("**/fabric/**", "**/forge/**")
}

// src/early: the IModLanguageLoader
val early = sourceSets.create("early") {
    java.include("io/github/imaron85/presetswitcher/early/neoforge/**")
    compileClasspath += configurations["compileClasspath"] + core.output
}
val earlyJar = tasks.register<Jar>("earlyJar") {
    archiveBaseName = "presetswitcher-early"
    archiveVersion = modVersion
    destinationDirectory = layout.buildDirectory.dir("early")
    from(core.output)
    from(early.output)
    from(resources.text.fromString("io.github.imaron85.presetswitcher.early.neoforge.LanguageLoaderHook\n")) {
        into("META-INF/services")
        rename { "net.neoforged.neoforgespi.language.IModLanguageLoader" }
    }
    manifest.attributes("FMLModType" to "LIBRARY", "Automatic-Module-Name" to "presetswitcher.early")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar {
    from(earlyJar) { into("META-INF/jarjar") }
    from(resources.text.fromString(jarJarMetadata("presetswitcher-early-$modVersion.jar"))) {
        into("META-INF/jarjar")
        rename { "metadata.json" }
    }
}

tasks.processResources {
    val props = metadata + mapOf(
        "loader_min" to project.property("deps.neo_loader_min").toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "*.mixins.json")) { expand(props) }
    exclude("fabric.mod.json", "META-INF/mods.toml", "pack.mcmeta")
}

tasks.register<Copy>("collectJar") {
    group = "build"
    description = "Copies the mod jar to the root build/libs"
    from(tasks.jar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}
