package com.dualclone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Smoke tests for the fresh shell's pure logic. */
class DualCloneUnitTest {
    @Test
    fun blockedPackages_areRefused() {
        assertNull(com.dualclone.app.data.InstalledApps.isBlocked("org.telegram.messenger"))
        assertNull(com.dualclone.app.data.InstalledApps.isBlocked("com.instagram.android"))
    }

    @Test
    fun displayName_fallsBackToLabel() {
        val r = com.dualclone.app.data.CloneRecord(packageName = "com.example", label = "Example", engineUserId = 1)
        assertEquals("Example", r.displayName)
        assertEquals("Work", r.copy(customName = "Work").displayName)
    }
}
