plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Version- and loader-specific properties from `stonecutter.properties.toml`
    properties {
        tags(version, loader)
    }

    // `//? if fabric {`, `//? if neoforge {`, `//? if forge {`
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }
}
