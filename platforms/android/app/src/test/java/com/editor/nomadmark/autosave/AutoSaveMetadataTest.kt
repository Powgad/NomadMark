package com.editor.nomadmark.autosave

import org.junit.Test
import org.junit.Assert.*

/**
 * AutoSaveMetadata 数据类测试
 *
 * 纯 JUnit 测试，无需 Android 环境
 */
class AutoSaveMetadataTest {

    @Test
    fun testMetadataValidation() {
        val validMetadata = AutoSaveMetadata(
            id = "test-id",
            timestamp = System.currentTimeMillis(),
            originalFilePath = "/test/path.md",
            originalFileUri = null,
            fileName = "test",
            contentLength = 100,
            lastSavedHash = 12345,
            isModified = true,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        assertTrue("Valid metadata should pass validation", validMetadata.isValid())
    }

    @Test
    fun testMetadataExpiry() {
        val now = System.currentTimeMillis()
        val oldMetadata = AutoSaveMetadata(
            id = "old-id",
            timestamp = now - (8 * 24 * 60 * 60 * 1000L), // 8 days ago
            originalFilePath = "/test/path.md",
            originalFileUri = null,
            fileName = "test",
            contentLength = 100,
            lastSavedHash = 12345,
            isModified = true,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        val sevenDaysInMillis = 7L * 24L * 60L * 60L * 1000L
        assertTrue("Old metadata should be expired", oldMetadata.isExpired(sevenDaysInMillis))
    }

    @Test
    fun testRecentMetadataNotExpired() {
        val recentMetadata = AutoSaveMetadata(
            id = "recent-id",
            timestamp = System.currentTimeMillis() - (6 * 60 * 60 * 1000L), // 6 hours ago
            originalFilePath = "/test/path.md",
            originalFileUri = null,
            fileName = "test",
            contentLength = 100,
            lastSavedHash = 12345,
            isModified = true,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        val sevenDaysInMillis = 7L * 24L * 60L * 60L * 1000L
        assertFalse("Recent metadata should not be expired", recentMetadata.isExpired(sevenDaysInMillis))
    }

    @Test
    fun testInvalidMetadata_EmptyId() {
        val invalidMetadata = AutoSaveMetadata(
            id = "", // Empty ID
            timestamp = System.currentTimeMillis(),
            originalFilePath = null,
            originalFileUri = null,
            fileName = "test",
            contentLength = 100,
            lastSavedHash = 0,
            isModified = false,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        assertFalse("Metadata with empty ID should fail validation", invalidMetadata.isValid())
    }

    @Test
    fun testInvalidMetadata_ZeroTimestamp() {
        val invalidMetadata = AutoSaveMetadata(
            id = "test-id",
            timestamp = 0, // Zero timestamp
            originalFilePath = null,
            originalFileUri = null,
            fileName = "test",
            contentLength = 100,
            lastSavedHash = 0,
            isModified = false,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        assertFalse("Metadata with zero timestamp should fail validation", invalidMetadata.isValid())
    }

    @Test
    fun testInvalidMetadata_EmptyFileName() {
        val invalidMetadata = AutoSaveMetadata(
            id = "test-id",
            timestamp = System.currentTimeMillis(),
            originalFilePath = null,
            originalFileUri = null,
            fileName = "", // Empty filename
            contentLength = 100,
            lastSavedHash = 0,
            isModified = false,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        assertFalse("Metadata with empty filename should fail validation", invalidMetadata.isValid())
    }

    @Test
    fun testInvalidMetadata_NegativeContentLength() {
        val invalidMetadata = AutoSaveMetadata(
            id = "test-id",
            timestamp = System.currentTimeMillis(),
            originalFilePath = null,
            originalFileUri = null,
            fileName = "test",
            contentLength = -1, // Negative length
            lastSavedHash = 0,
            isModified = false,
            appVersion = "1.0",
            deviceInfo = "Test Device"
        )

        assertFalse("Metadata with negative content length should fail validation", invalidMetadata.isValid())
    }

    @Test
    fun testMetadataConstants() {
        assertEquals("VERSION should be '1'", "1", AutoSaveMetadata.VERSION)
        assertEquals("KEY_VERSION should match", "version", AutoSaveMetadata.KEY_VERSION)
        assertEquals("KEY_ID should match", "id", AutoSaveMetadata.KEY_ID)
        assertEquals("KEY_TIMESTAMP should match", "timestamp", AutoSaveMetadata.KEY_TIMESTAMP)
        assertEquals("KEY_FILE_NAME should match", "fileName", AutoSaveMetadata.KEY_FILE_NAME)
    }
}
