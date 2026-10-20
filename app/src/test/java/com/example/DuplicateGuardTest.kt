package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateGuardTest {

    private var now = 1_000_000L
    private val guard = DuplicateGuard(windowMs = 15_000L) { now }

    @Test
    fun firstNotificationIsNotDuplicate() {
        assertFalse(guard.isDuplicate("id.dana|1|QRIS Rp 10.000"))
    }

    @Test
    fun sameNotificationWithinWindowIsDuplicate() {
        val key = "id.dana|1|QRIS Rp 10.000"
        assertFalse(guard.isDuplicate(key))
        assertTrue(guard.isDuplicate(key))
        assertTrue(guard.isDuplicate(key))
    }

    @Test
    fun sameNotificationAfterWindowIsForwardedAgain() {
        val key = "id.dana|1|QRIS Rp 10.000"
        assertFalse(guard.isDuplicate(key))
        now += 15_001L
        assertFalse(guard.isDuplicate(key))
    }

    @Test
    fun differentPaymentIsNotDuplicate() {
        assertFalse(guard.isDuplicate("id.dana|1|QRIS Rp 10.000"))
        assertFalse(guard.isDuplicate("id.dana|1|QRIS Rp 25.000"))
    }

    @Test
    fun differentPackageIsNotDuplicate() {
        assertFalse(guard.isDuplicate("id.dana|1|QRIS Rp 10.000"))
        assertFalse(guard.isDuplicate("com.gojek.gobiz|1|QRIS Rp 10.000"))
    }

    @Test
    fun repeatedUpdatesKeepBeingBlockedWhileTheyArrive() {
        val key = "id.dana|1|QRIS Rp 10.000"
        assertFalse(guard.isDuplicate(key))
        repeat(10) {
            now += 10_000L
            assertTrue("update ke-${it + 1} harus diabaikan", guard.isDuplicate(key))
        }
    }

    @Test
    fun entriesAreCapped() {
        val smallGuard = DuplicateGuard(windowMs = 15_000L, maxEntries = 2) { now }
        assertFalse(smallGuard.isDuplicate("a"))
        assertFalse(smallGuard.isDuplicate("b"))
        assertFalse(smallGuard.isDuplicate("c"))
        // "a" sudah keluar dari cache, jadi dianggap baru lagi.
        assertFalse(smallGuard.isDuplicate("a"))
    }
}
