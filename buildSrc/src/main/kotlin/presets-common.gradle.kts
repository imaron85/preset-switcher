// Shared configuration of every loader/version node (applied by build.<loader>.gradle.kts).
// All values come from stonecutter.properties.toml.
plugins {
    java
}

val loader = project.name.substringAfter('-')
val modId = property("mod.id").toString()
val modVersion = property("mod.version").toString()
val javaVersion = property("java.version").toString().toInt()

// <mod version>+<loader>-<Minecraft range>, e.g. 1.1.0+fabric-1.21-1.21.1
version = "$modVersion+$loader-${property("mod.mc_label")}"
group = property("mod.group").toString()
base.archivesName = "preset-switcher"

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

val metadata: Map<String, String> = buildMap {
    put("id", modId)
    put("name", property("mod.name").toString())
    put("version", project.version.toString())
    put("description", property("mod.description").toString())
    put("authors", property("mod.authors").toString())
    put("license", property("mod.license").toString())
    put("homepage", property("mod.homepage").toString())
    put("sources", property("mod.sources").toString())
    put("issues", property("mod.issues").toString())
    put("minecraft", property("mod.mc_compat").toString())
    put("java", javaVersion.toString())
    put("mixin_java", "JAVA_$javaVersion")
    put("refmap", "")
}
extra["presetsMetadata"] = metadata

tasks.withType<Jar>().configureEach {
    inputs.property("mod_id", modId)
    from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    // Reproducible jars: same sources -> byte-identical file
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Loader-independent core (src/core): pure Java + SLF4J. Fabric and NeoForge 21.9+ jars contain it; on ModLauncher
// loaders (Forge, NeoForge 21.0-21.8) it goes into the nested early-hook jar instead.
val core = sourceSets.create("core") {
    compileClasspath += configurations["compileClasspath"]
}
sourceSets.main {
    compileClasspath += core.output
    runtimeClasspath += core.output
}
extra["coreSourceSet"] = core

// Unit tests of the core run in one node only
if (project.name == "26.3-fabric") {
    sourceSets.test {
        compileClasspath += core.output
        runtimeClasspath += core.output
    }
    repositories { mavenCentral() }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
} else {
    tasks.named("compileTestJava") { enabled = false }
    tasks.withType<Test>().configureEach { enabled = false }
}

/** JarJar metadata (Forge / NeoForge jar-in-jar) for the nested early-hook jar. */
fun jarJarMetadata(file: String): String = """
{
  "jars": [
    {
      "identifier": { "group": "io.github.imaron85", "artifact": "presetswitcher-early" },
      "version": { "range": "[$modVersion,)", "artifactVersion": "$modVersion" },
      "path": "META-INF/jarjar/$file",
      "isObfuscated": false
    }
  ]
}
""".trimIndent()
extra["jarJarMetadata"] = ::jarJarMetadata
