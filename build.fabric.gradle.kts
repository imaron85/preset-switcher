plugins {
    // Applies fabric-loom-remap (obfuscated Minecraft, Mojang mappings) or fabric-loom (26.1+)
    id("dev.kikugie.loom-back-compat")
    id("presets-common")
}

@Suppress("UNCHECKED_CAST")
val metadata = extra["presetsMetadata"] as Map<String, String>

val core = extra["coreSourceSet"] as SourceSet
sourceSets.main {
    java.exclude("**/neoforge/**", "**/forge/**")
}
// Compile-time copy of Mod Menu's API (src/stubs; the integration is optional, so no Mod Menu artifact per version)
val stubs = sourceSets.create("stubs") {
    compileClasspath += configurations["compileClasspath"]
}
sourceSets.main { compileClasspath += stubs.output }

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
}

loom {
    runConfigs.all {
        runDirectory = rootProject.file("run")
    }
    if (sc.current.parsed < "26.1") {
        // Obfuscated Minecraft: remap mixin targets through a refmap (Mixin annotation processor) instead of rewriting
        // the annotations (see the Sturdy Elytras port: rewritten annotations break older MixinExtras/Quilt)
        mixin {
            useLegacyMixinAp = true
            defaultRefmapName = "presetswitcher.refmap.json"
        }
    }
}

tasks.jar {
    from(core.output)
}

tasks.processResources {
    val props = metadata + mapOf(
        "fabric_loader" to project.property("deps.fabric_loader_min").toString(),
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    exclude("META-INF/*.toml", "pack.mcmeta")
}

tasks.register<Copy>("collectJar") {
    group = "build"
    description = "Copies the mod jar to the root build/libs"
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}
