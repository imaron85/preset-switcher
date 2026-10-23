plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.148"
    id("neoforge-mutex")
    id("presets-common")
}

@Suppress("UNCHECKED_CAST")
val metadata = extra["presetsMetadata"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val jarJarMetadata = extra["jarJarMetadata"] as (String) -> String
val modId = property("mod.id").toString()
val modVersion = property("mod.version").toString()
val mixinConfig = "$modId.mixins.json"
val refmap = "$modId.refmap.json"

legacyForge {
    enable {
        forgeVersion = property("deps.forge").toString()
        isDisableRecompilation = true
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Forge 1.20.1 runs with SRG names: the jar is reobfuscated and mixins need a refmap
mixin {
    add(sourceSets.main.get(), refmap)
    config(mixinConfig)
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

/*
 * Early hook: Forge excludes jars that declare early services from mod loading, so the hook and the core live in a
 * nested language-provider jar (jar-in-jar). Forge creates language providers right after mod discovery.
 */
val core = extra["coreSourceSet"] as SourceSet
sourceSets.main {
    java.exclude("**/fabric/**", "**/neoforge/**")
}
// src/early: the IModLanguageProvider of Forge 1.20.1
val early = sourceSets.create("early") {
    java.include("io/github/imaron85/presetswitcher/early/forge/**")
    compileClasspath += configurations["compileClasspath"] + core.output
}
val earlyJar = tasks.register<Jar>("earlyJar") {
    archiveBaseName = "presetswitcher-early"
    archiveVersion = modVersion
    destinationDirectory = layout.buildDirectory.dir("early")
    from(core.output)
    from(early.output)
    from(resources.text.fromString("io.github.imaron85.presetswitcher.early.forge.LanguageProviderHook\n")) {
        into("META-INF/services")
        rename { "net.minecraftforge.forgespi.language.IModLanguageProvider" }
    }
    manifest.attributes("FMLModType" to "LANGPROVIDER", "Automatic-Module-Name" to "presetswitcher.early")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    val props = metadata + mapOf(
        "loader_min" to project.property("deps.forge_min").toString(),
        "fml_min" to project.property("deps.forge_min").toString(),
        "refmap" to ",\n  \"refmap\": \"$refmap\"",
        // Forge 1.20.1 warns at startup about mod jars without pack.mcmeta (resource pack format 15)
        "pack_format" to "15",
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "*.mixins.json", "pack.mcmeta")) { expand(props) }
    exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
}

tasks.jar {
    manifest.attributes["MixinConfigs"] = mixinConfig
    from(earlyJar) { into("META-INF/jarjar") }
    from(resources.text.fromString(jarJarMetadata("presetswitcher-early-$modVersion.jar"))) {
        into("META-INF/jarjar")
        rename { "metadata.json" }
    }
}

tasks.register<Copy>("collectJar") {
    group = "build"
    description = "Copies the (reobfuscated) mod jar to the root build/libs"
    from(tasks.named("reobfJar"))
    into(rootProject.layout.buildDirectory.dir("libs"))
}
