package com.codecsrayo.quizgate

/**
 * Canonical list of apps the gate knows how to block.
 *
 * Adding an app here makes it appear in the Setup-screen toggle list
 * AND in the default-blocked set used on first launch.
 */
data class BlockableApp(val pkg: String, val label: String)

object BlockableApps {
    val ALL: List<BlockableApp> = listOf(
        BlockableApp("com.facebook.katana", "Facebook"),
        BlockableApp("com.facebook.lite", "Facebook Lite"),
        BlockableApp("com.whatsapp", "WhatsApp"),
        BlockableApp("com.instagram.android", "Instagram"),
    )

    val DEFAULT_PACKAGES: Set<String> = ALL.map { it.pkg }.toSet()
}
