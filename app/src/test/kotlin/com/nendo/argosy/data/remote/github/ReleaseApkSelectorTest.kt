package com.nendo.argosy.data.remote.github

import org.junit.Assert.*
import org.junit.Test

class ReleaseApkSelectorTest {
    private fun asset(name: String) = GitHubAsset(name, "https://example.test/$name", 1)

    @Test fun `selects Gradle armv7 and arm64 split filenames`() {
        val arm64 = asset("app-arm64-v8a-release.apk")
        val arm32 = asset("app-armeabi-v7a-release.apk")
        val assets = listOf(arm64, arm32)
        assertEquals(arm32, selectReleaseApk(assets, 1_000_329))
        assertEquals(arm64, selectReleaseApk(assets, 2_000_329))
    }

    @Test fun `universal installations require universal updates without a version code downgrade`() {
        val universal = asset("app-universal-release.apk")
        val assets = listOf(asset("app-arm64-v8a-release.apk"), universal)
        assertEquals(universal, selectReleaseApk(assets, 3_000_329))
        assertNull(selectReleaseApk(assets.take(1), 3_000_329))
    }

    @Test fun `compatible universal fallback never chooses the wrong ABI or a checksum file`() {
        val universal = asset("gameio-1.0.1.apk")
        assertEquals(universal, selectReleaseApk(listOf(asset("app-arm64.apk"), universal), 1_000_329))
        assertNull(selectReleaseApk(listOf(asset("app-arm64.apk"), asset("app-arm32.apk.sha256")), 1_000_329))
    }
}
