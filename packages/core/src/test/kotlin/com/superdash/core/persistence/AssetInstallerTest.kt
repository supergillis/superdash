package com.superdash.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.FilterInputStream

class AssetInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `installs asset to target when missing`() {
        val target = temporaryFolder.newFolder().resolve("model.bin")
        val payload = "hello-asset".toByteArray()
        val installer = AssetInstaller(openAsset = { ByteArrayInputStream(payload) })

        val result = installer.installIfMissing("model.bin", target)

        assertNotNull(result)
        assertEquals(payload.size.toLong(), target.length())
        assertEquals("hello-asset", target.readText())
    }

    @Test
    fun `returns null and removes temp file when stream is truncated`() {
        val parent = temporaryFolder.newFolder()
        val target = parent.resolve("model.bin")
        // available() reports 16 but only 4 bytes are actually delivered.
        val installer =
            AssetInstaller(
                openAsset = {
                    object : FilterInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))) {
                        override fun available(): Int = 16
                    }
                },
            )

        val result = installer.installIfMissing("model.bin", target)

        assertNull(result)
        assertFalse(parent.listFiles().orEmpty().any { file -> file.name.endsWith(".tmp") })
    }

    @Test
    fun `is a no-op when target already matches expected length`() {
        val target = temporaryFolder.newFolder().resolve("model.bin")
        target.writeBytes(byteArrayOf(9, 9, 9, 9))
        var opens = 0
        val installer =
            AssetInstaller(
                openAsset = {
                    opens += 1
                    ByteArrayInputStream(ByteArray(4))
                },
            )

        val result = installer.installIfMissing("model.bin", target)

        assertNotNull(result)
        assertEquals(listOf(9, 9, 9, 9), target.readBytes().map { byte -> byte.toInt() })
        // One open to discover length; no second open to stream content.
        assertEquals(1, opens)
    }
}
