package com.roanokeresistance.lovelace.plugins

/**
 * A bundled IITC-CE plugin (§3 of the architecture doc — plugins run as
 * normal, injected after core). These are vendored as raw, unbuilt source
 * from the IITC-CE plugins/ directory (ISC-licensed, same project as the
 * bundled core script) rather than run through IITC-CE's own Python build
 * tooling, so `PluginInjector.wrapPluginScript` wraps each one at injection
 * time instead of relying on a pre-built self-inject footer.
 */
data class IitcPlugin(
    val id: String,
    val displayName: String,
    val description: String,
    val scriptAsset: String,
    val styleAsset: String? = null
)

val IITC_PLUGIN_CATALOG = listOf(
    IitcPlugin(
        id = "player-activity-tracker",
        displayName = "Player Activity Tracker",
        description = "Draws trails showing where other agents have been active, based on COMM chat.",
        scriptAsset = "plugins/player-activity-tracker.js",
        styleAsset = "plugins/player-tracker.css"
    ),
    IitcPlugin(
        id = "draw-tools",
        displayName = "Draw Tools",
        description = "Draw lines and shapes on the map to plan routes and ops.",
        scriptAsset = "plugins/draw-tools.js"
    ),
    IitcPlugin(
        id = "bookmarks",
        displayName = "Bookmarks",
        description = "Save favorite portals and map views for quick navigation.",
        scriptAsset = "plugins/bookmarks.js",
        styleAsset = "plugins/bookmarks.css"
    )
)
