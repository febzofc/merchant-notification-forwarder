# Bayarin - Merchant Notification Forwarder 🚀

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Release](https://img.shields.io/github/v/release/febzofc/merchant-notification-forwarder?style=for-the-badge)
![Build](https://github.com/febzofc/merchant-notification-forwarder/actions/workflows/release.yml/badge.svg)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

**Aplikasi Android untuk meneruskan notifikasi pembayaran QRIS, e-wallet, dan transaksi merchant ke webhook / REST API server secara real-time.**

*Dikembangkan oleh **Febriansyah** ([@febzofc](https://github.com/febzofc))*

---
</div>

## 📥 Unduh Aplikasi

APK siap pasang tersedia di halaman **[Releases](https://github.com/febzofc/merchant-notification-forwarder/releases/latest)**.

1. Buka halaman **Releases**, lalu unduh berkas `Bayarin-vX.Y.apk` pada bagian *Assets*.
2. Pindahkan ke perangkat Android, lalu pasang (*install*). Karena APK ini tidak diunduh dari Play Store, Anda perlu mengizinkan **Install unknown apps** untuk aplikasi pengelola berkas/browser yang Anda gunakan.
3. Lanjutkan ke [Panduan Instalasi & Penggunaan](#-panduan-instalasi--penggunaan) bagian konfigurasi izin.

> Catatan: APK pada rilis ini di-sign dengan *debug keystore* agar siapa pun bisa memasangnya tanpa kunci rilis pribadi. Untuk distribusi komersial/Play Store, build ulang dengan keystore rilis Anda sendiri.

---

## 📌 Tentang Aplikasi

**Bayarin** adalah solusi jembatan (*bridge*) otomatis bagi para pemilik toko (*merchant*), developer kasir (POS), dan pegiat automasi pembayaran di Indonesia. Aplikasi ini berjalan di latar belakang (*background service*) menggunakan Android `NotificationListenerService` untuk menangkap notifikasi transaksi masuk (seperti dari **GoPay / GoBiz**, **DANA**, **OVO**, **ShopeePay**, **LinkAja**, maupun perbankan seperti **BCA** dan **Livin' Mandiri**) lalu mengirimkannya secara instan dalam format JSON ke URL Webhook yang Anda tentukan.

---

## ✨ Fitur Utama

- ⚡ **Real-time Forwarding**: Notifikasi pembayaran langsung diteruskan ke webhook dalam hitungan milidetik setelah diterima.
- 🛡️ **Smart Anti-Spam & Promo Filter**:
  - Memfilter dan memblokir notifikasi promo, voucher diskon, cashback, DANA Kaget, pinjaman/paylater, dan pesan marketing.
  - Memastikan hanya notifikasi transaksi pembayaran sah yang diteruskan ke server Anda.
- 💰 **Auto Extract Amount**: Mengekstrak nominal pembayaran (`Rp` / `IDR`) secara otomatis ke format angka numerik (`Long`).
- 🎯 **Mode Penyaringan Fleksibel**:
  - **GoPay & DANA** (Rekomendasi untuk UMKM)
  - **Semua QRIS & E-Wallet** (GoPay, DANA, OVO, ShopeePay, LinkAja, BCA, Livin by Mandiri)
  - **Semua Aplikasi** (Meneruskan semua notifikasi sistem)
  - **Custom Package Name** (Filter aplikasi berdasarkan *package name* spesifik, contoh: `com.whatsapp`)
- 🧪 **Simulator & Pengujian Langsung**:
  - Uji koneksi Webhook langsung dari dalam aplikasi.
  - Simulasi validasi filter pembayaran QRIS vs pesan promo tanpa perlu menunggu transaksi asli.
- 📜 **Log Aktivitas & Riwayat Transaksi**:
  - Memantau status pengiriman (`SUCCESS`, `FAILED`, `SENDING`).
  - Menampilkan kode status HTTP response dan pesan balasan dari server Anda.
- 🔋 **Panduan Optimalisasi Baterai**: Dilengkapi panduan izin latar belakang untuk berbagai vendor Android (Infinix, Xiaomi, Samsung, Oppo, Vivo) agar layanan tidak dimatikan oleh sistem.

---

## 🏗️ Alur Kerja (Workflow)

```mermaid
flowchart LR
    A["Pelanggan Bayar via QRIS"] --> B["E-Wallet / Bank Kirim Notifikasi"]
    B --> C["Bayarin Notification Service"]
    C --> D{"Smart Filter & Anti-Spam"}
    D -- "Promo / Spam / Chat" --> E["Diabaikan (Blocked)"]
    D -- "Transaksi Sah" --> F["Ekstraksi Nominal (Rp)"]
    F --> G["HTTP POST (JSON Payload)"]
    G --> H["Webhook Server / Backend Anda"]
```

---

## 📡 Format Payload Webhook

Setiap kali notifikasi yang valid diterima, aplikasi akan mengirimkan HTTP POST request dengan header `Content-Type: application/json; charset=utf-8` dan `User-Agent: Bayarin-Forwarder/1.0`:

```json
{
  "app": "Bayarin",
  "id": 101,
  "uniqueId": "id.dana_101_1729345200000",
  "provider": "DANA",
  "packageName": "id.dana",
  "title": "DANA Bisnis",
  "text": "Pembayaran QRIS Rp 50.000 berhasil diterima dari BUDI",
  "message": "Pembayaran QRIS Rp 50.000 berhasil diterima dari BUDI",
  "rawText": "DANA Bisnis: Pembayaran QRIS Rp 50.000 berhasil diterima dari BUDI",
  "amount": 50000,
  "nominal": 50000,
  "subText": "",
  "postTime": 1729345200000,
  "timestamp": 1729345200150
}
```

### Keterangan Field:
| Field | Tipe Data | Keterangan |
|---|---|---|
| `app` | String | Nama aplikasi pengirim (`Bayarin`) |
| `id` | Integer | ID internal notifikasi Android |
| `uniqueId` | String | Identitas unik notifikasi (`package_id_timestamp`) |
| `provider` | String | Provider terdeteksi (`DANA`, `GOPAY`, `OVO`, `SHOPEEPAY`, `LINKAJA`, `QRIS`, `OTHER`) |
| `packageName`| String | Package name aplikasi sumber (misal: `id.dana` atau `com.gojek.gobiz`) |
| `title` | String | Judul notifikasi |
| `text` / `message` | String | Isi teks notifikasi |
| `rawText` | String | Gabungan judul dan isi notifikasi |
| `amount` / `nominal` | Long | Nominal uang yang berhasil diekstrak (dalam integer murni) |
| `postTime` | Long | Timestamp saat notifikasi dibuat di sistem Android |
| `timestamp` | Long | Timestamp saat forwarder mengirimkan data |

---

## 🚀 Panduan Instalasi & Penggunaan

### 1. Prasyarat
- Perangkat Android dengan OS **Android 7.0 (Nougat / API 24)** atau lebih tinggi.
- Server penerima webhook dengan endpoint aktif (contoh: webhook Discord, Telegram bot bridge, endpoint Node.js, Laravel, Go, dsb.).

### 2. Konfigurasi Izin di HP
Agar forwarder dapat berjalan lancar tanpa terhenti:
1. **Izin Akses Notifikasi**: Buka aplikasi, klik banner peringatan atau tombol **Buka Pengaturan Notifikasi**, lalu aktifkan **Bayarin Notification Forwarder**.
2. **Izin Aplikasi Berbahaya / Khusus (Android 13+)**:
   - Jika tombol akses notifikasi berwarna abu-abu (*Restricted Setting*):
   - Masuk ke *Settings > Apps > Bayarin > Titik tiga di pojok kanan atas > Allow restricted settings*.
3. **Pengecualian Optimasi Baterai**:
   - Izinkan Bayarin berjalan tanpa batasan latar belakang (*No restrictions / Unrestricted battery*).
4. **Auto-start**:
   - Di perangkat Infinix, Xiaomi, Oppo, Vivo, pastikan Bayarin diberi izin *Autostart* dan dikunci pada menu Recent Apps.

### 3. Konfigurasi Webhook
1. Masukkan URL endpoint server Anda pada kolom **URL Webhook Server**.
2. Klik tombol **Uji Koneksi** untuk memverifikasi server Anda merespons dengan HTTP status 200.
3. Klik **Simpan Pengaturan**.

---

## 🛠️ Kompilasi dari Source Code

Aplikasi ini dibangun menggunakan stack Android modern:
- **Language**: Kotlin 2.2.10
- **Build System**: Gradle 9+ (Kotlin DSL)
- **UI Toolkit**: Jetpack Compose & Material 3
- **Network**: OkHttp3 & Retrofit

### Langkah Build APK:
```bash
# Clone repository
git clone https://github.com/febzofc/merchant-notification-forwarder.git
cd merchant-notification-forwarder

# Build APK Debug via Gradle
./gradlew assembleDebug

# Output APK akan berada di:
# app/build/outputs/apk/debug/app-debug.apk
```

### Membuat Rilis Baru (Otomatis)

Rilis dipublikasikan otomatis oleh GitHub Actions (`.github/workflows/release.yml`). Cukup buat dan push tag:

```bash
git tag v1.0
git push origin v1.0
```

Setelah tag terkirim, GitHub akan otomatis:
1. Menyiapkan JDK, Android SDK, dan Gradle,
2. Membangun APK debug (`assembleDebug`),
3. Membuat GitHub Release bernama sesuai tag, dan
4. Melampirkan APK tersebut sebagai aset rilis.

Anda juga bisa menjalankannya manual dari tab **Actions → Build & Release APK → Run workflow** dengan mengisi nama tag.

---

## 🔒 Privasi & Keamanan

- **100% Direct Forwarding**: Data notifikasi dikirimkan langsung dari perangkat Android Anda ke endpoint webhook yang Anda tentukan sendiri.
- **Tanpa Server Pihak Ketiga**: Aplikasi ini tidak menggunakan layanan perantara pihak ketiga, analitik tersembunyi, ataupun pelacak data pengguna.
- **Transparan**: Seluruh kode sumber terbuka (*open source*) dan dapat diaudit secara bebas.

---

## 👨‍💻 Kontributor & Kredit

Aplikasi ini dirancang, dikembangkan, dan dipelihara oleh:

**Febriansyah**
- GitHub: [@febzofc](https://github.com/febzofc)
- Repository: [merchant-notification-forwarder](https://github.com/febzofc/merchant-notification-forwarder)

---

## 📄 Lisensi

Proyek ini dirilis di bawah lisensi [MIT License](LICENSE). Bebas digunakan, dimodifikasi, dan didistribusikan untuk keperluan pribadi maupun komersial.
