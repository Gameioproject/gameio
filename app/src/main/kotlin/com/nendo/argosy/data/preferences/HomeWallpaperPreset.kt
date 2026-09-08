package com.nendo.argosy.data.preferences

/**
 * Bundled home backgrounds in the style of the big consoles' home screens. A preset is stored as
 * the custom background path, as a resource URI, so the home screen draws it through the same
 * loader as a picture the user picked; [resourceName] is the token that identifies it there.
 */
enum class HomeWallpaperPreset(val resourceName: String?) {
    NONE(null),
    PLAYSTATION("wallpaper_playstation"),
    SWITCH("wallpaper_switch"),
    XBOX("wallpaper_xbox");

    fun uri(packageName: String): String? =
        resourceName?.let { "android.resource://$packageName/drawable/$it" }

    companion object {
        private const val MARKER = "/drawable/wallpaper_"

        /** The preset a stored path stands for, [NONE] for no path, null for the user's own file. */
        fun fromPath(path: String?): HomeWallpaperPreset? {
            if (path == null) return NONE
            if (!path.startsWith("android.resource://") || MARKER !in path) return null
            val name = path.substringAfterLast("/drawable/")
            return entries.firstOrNull { it.resourceName == name }
        }
    }
}
