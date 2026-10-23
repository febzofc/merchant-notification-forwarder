package com.example

import org.junit.Assert.*
import org.junit.Test

class NotificationFilterTest {

    @Test
    fun testAmountExtraction() {
        assertEquals(50000L, NotificationForwarderService.extractAmount("Pembayaran QRIS Rp 50.000 berhasil diterima"))
        assertEquals(25000L, NotificationForwarderService.extractAmount("Pembayaran QRIS sebesar Rp 25.000,00"))
        assertEquals(100000L, NotificationForwarderService.extractAmount("Diterima IDR 100.000 dari BUDI"))
        assertEquals(10000L, NotificationForwarderService.extractAmount("Rp. 10.000"))
        assertEquals(1500000L, NotificationForwarderService.extractAmount("Transfer masuk Rp 1.500.000"))
        assertNull(NotificationForwarderService.extractAmount("Promo Diskon Belanja Hari Ini!"))
    }

    @Test
    fun testDanaQrisPaymentAllowed() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.dana",
            title = "DANA Bisnis",
            text = "Pembayaran QRIS Rp 50.000 berhasil diterima dari BUDI",
            subText = "",
            mode = "qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 50000L,
            provider = "DANA"
        )
        assertTrue("Real QRIS payment must be allowed", decision.allowed)
    }

    @Test
    fun testDanaPersonalTransferBlocked() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.dana",
            title = "DANA",
            text = "Berhasil menerima uang sebesar Rp 100.000 dari SITI",
            subText = "",
            mode = "merchant",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 100000L,
            provider = "DANA"
        )
        assertFalse("Transfer pribadi tidak boleh diteruskan", decision.allowed)
    }

    @Test
    fun testBankTransferBlockedInMerchantMode() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "com.bca",
            title = "m-BCA",
            text = "Transfer masuk Rp 1.500.000 dari BUDI",
            subText = "",
            mode = "merchant",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 1500000L,
            provider = "OTHER"
        )
        assertFalse("Transfer bank biasa tidak boleh diteruskan", decision.allowed)
    }

    @Test
    fun testBankTransferBlockedInAllQrisMode() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "com.bca",
            title = "m-BCA",
            text = "Transfer masuk Rp 1.500.000 dari BUDI",
            subText = "",
            mode = "all_qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 1500000L,
            provider = "OTHER"
        )
        assertFalse("Transfer bank tetap harus ditolak di mode all_qris", decision.allowed)
    }

    @Test
    fun testOtherAppMentioningQrisTextBlocked() {
        // Kata "qris"/"dana" di teks tidak boleh lagi membuat aplikasi lain lolos.
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "com.whatsapp",
            title = "Grup Merchant",
            text = "Pembayaran QRIS Rp 50.000 sudah masuk ya",
            subText = "",
            mode = "merchant",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 50000L,
            provider = "QRIS"
        )
        assertFalse("Hanya paket merchant resmi yang boleh diteruskan", decision.allowed)
    }

    @Test
    fun testOvoQrisPaymentAllowed() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.co.ovo",
            title = "OVO Merchant",
            text = "Pembayaran QRIS Rp 20.000 berhasil diterima",
            subText = "",
            mode = "merchant",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 20000L,
            provider = "OVO"
        )
        assertTrue("Pembayaran QRIS OVO harus diteruskan", decision.allowed)
    }

    @Test
    fun testGobizPaymentWithoutQrisWordAllowed() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "com.gojek.gobiz",
            title = "GoBiz",
            text = "Pembayaran sebesar Rp 15.000 berhasil diterima dari pelanggan",
            subText = "",
            mode = "merchant",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 15000L,
            provider = "GOPAY"
        )
        assertTrue("Pembayaran merchant tanpa kata QRIS tetap valid", decision.allowed)
    }

    @Test
    fun testGojekConsumerTransferBlocked() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "com.gojek.app",
            title = "GoPay",
            text = "Kamu menerima transfer Rp 75.000 dari BUDI",
            subText = "",
            mode = "merchant",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 75000L,
            provider = "GOPAY"
        )
        assertFalse("Aplikasi Gojek konsumen bukan merchant", decision.allowed)
    }

    @Test
    fun testDanaPromoCashbackBlocked() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.dana",
            title = "DANA",
            text = "Promo Spesial Hari Ini! Dapatkan Cashback 50% hingga Rp 15.000 belanja di Alfamart!",
            subText = "",
            mode = "qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 15000L,
            provider = "DANA"
        )
        assertFalse("Promo cashback must be blocked", decision.allowed)
        assertTrue(decision.reason.contains("promo") || decision.reason.contains("spam"))
    }

    @Test
    fun testDanaKagetBlocked() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.dana",
            title = "DANA Kaget",
            text = "Buka amplop sekarang dan klaim saldo gratis!",
            subText = "",
            mode = "qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = null,
            provider = "DANA"
        )
        assertFalse("DANA Kaget must be blocked", decision.allowed)
    }

    @Test
    fun testDanaDiskonVoucherBlocked() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.dana",
            title = "DANA",
            text = "Klaim voucher diskon jajan hemat Rp 10.000 hari ini",
            subText = "",
            mode = "qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 10000L,
            provider = "DANA"
        )
        assertFalse("Voucher diskon must be blocked", decision.allowed)
    }

    @Test
    fun testNotificationWithoutAmountBlocked() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "id.dana",
            title = "DANA Bisnis",
            text = "Ada pembaruan sistem merchant",
            subText = "",
            mode = "qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = null,
            provider = "DANA"
        )
        assertFalse("Notification without amount must be blocked in payment mode", decision.allowed)
    }

    @Test
    fun testGopayQrisPaymentAllowed() {
        val decision = NotificationForwarderService.evaluateFilter(
            packageName = "com.gojek.gobiz",
            title = "GoBiz",
            text = "Pembayaran QRIS sebesar Rp 35.000 berhasil diterima",
            subText = "",
            mode = "qris",
            customPackage = "",
            blockPromos = true,
            requireAmount = true,
            amount = 35000L,
            provider = "GOPAY"
        )
        assertTrue("GoBiz QRIS payment must be allowed", decision.allowed)
    }
}
