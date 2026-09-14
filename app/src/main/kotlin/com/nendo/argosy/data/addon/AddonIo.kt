package com.nendo.argosy.data.addon

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readAddonBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (count < 0) return output.toByteArray()
        output.write(buffer, 0, count)
        if (output.size() > limit) throw AddonException(AddonFailure.TOO_LARGE)
    }
}
