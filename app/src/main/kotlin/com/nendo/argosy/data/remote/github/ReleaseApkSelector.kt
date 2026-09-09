package com.nendo.argosy.data.remote.github

internal fun selectReleaseApk(assets: List<GitHubAsset>, installedVersionCode: Int): GitHubAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    fun abi(asset: GitHubAsset): Int = when {
        asset.name.contains("arm64", ignoreCase = true) -> 2
        asset.name.contains("arm32", ignoreCase = true) ||
            asset.name.contains("armeabi-v7a", ignoreCase = true) -> 1
        asset.name.contains("x86", ignoreCase = true) -> -1
        else -> 3
    }
    val installedAbi = installedVersionCode / 1_000_000
    return apks.firstOrNull { abi(it) == installedAbi }
        ?: apks.firstOrNull { abi(it) == 3 }
}
