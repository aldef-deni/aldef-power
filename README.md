# ALDEF POWER

Aplikasi Android untuk **restart HP & kunci layar tanpa tombol power** (dibuat untuk realme 5 Pro, berjalan di Android 8.0+).

APK siap pakai: `ALDEF-POWER.apk`

## Cara kerja
Android tidak mengizinkan aplikasi biasa me-restart HP. ALDEF POWER memakai layanan **Aksesibilitas** untuk membuka menu power bawaan, lalu Anda geser bulatan **ke atas** untuk Restart.

> ⚠️ Di menu power realme, geser bulatan **ke ATAS** untuk Restart. JANGAN ke bawah (**Power off**). Tanpa tombol power, HP akan sulit dinyalakan lagi.

## Instalasi
1. Salin `ALDEF-POWER.apk` ke HP, buka, lalu izinkan "Instal dari sumber tidak dikenal".
2. Buka ALDEF POWER, ketuk **Aksesibilitas** (di bagian Status & Pengaturan), lalu aktifkan **ALDEF POWER**.
3. Agar tidak dimatikan oleh sistem realme: Info Aplikasi > Penggunaan baterai > izinkan aktivitas latar belakang, lalu kunci aplikasi di menu Recent.
4. (Opsional) Tambahkan tile **Restart HP** & **Kunci Layar** di panel notifikasi (tarik ke bawah > edit).

Atau install lewat PC: `adb install ALDEF-POWER.apk`

## Fitur rahasia: sembunyikan aplikasi & media
Tidak ada tombol yang terlihat. Ketuk cepat **5x** (jeda < 0,7 detik):

| Ketuk 5x | Membuka | Fungsi |
|---|---|---|
| Teks footer **ALDEF TECH** | **Sembunyikan** | Tab Aplikasi & Media, pilih lebih dari satu, lalu **Sembunyikan (n)** |
| **Logo ALDEF** di atas | **Ruang Privat** | Buka aplikasi/media tersembunyi; tahan untuk memilih, lalu **Tampilkan Kembali** |

**Media (foto & video)**: dipindahkan ke folder tersembunyi `/storage/emulated/0/.aldef_vault`
(berisi `.nomedia`, jadi tidak tampil di Galeri). Folder tetap ada walau aplikasi di-uninstall.
Butuh izin "Akses semua file" (diminta otomatis).

**Aplikasi**: memakai fitur bawaan realme **Hide apps** (Pengaturan > Privasi > Hide apps),
**tersinkron dua arah**:
- Sembunyikan / Tampilkan Kembali di ALDEF → sakelar Hide apps realme dinyalakan / dimatikan otomatis.
- Aplikasi yang disembunyikan langsung di realme → ikut masuk Ruang Privat lewat tombol **Sinkron**
  (atau menu ⋮ > Sinkronkan dengan realme).
- Setiap sinkron, realme meminta **sandi privasi** Anda; setelah itu ALDEF bekerja otomatis
  dan kembali sendiri ke Ruang Privat.
- Dari Ruang Privat aplikasi bisa dibuka langsung **tanpa unhide**.

Catatan teknis: daftar Hide apps realme dikunci izin sistem (signature), jadi sinkron dilakukan
oleh layanan aksesibilitas ALDEF yang membaca/mengetuk sakelar di layar Hide apps — hanya saat
sinkron dimulai dari ALDEF.

**Cari & filter**: di kedua layar ada kolom cari (nama aplikasi / nama file) dan,
di tab Media, filter **Semua / Foto / Video**. Item yang sudah dipilih tetap terpilih
walau tersaring.

Ruang Privat & penampil media diproteksi dari screenshot dan tidak tampil di layar Recent.

## Build dari source
```
gradlew.bat assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Struktur kode
- `RebootHelper.java`: logika restart & kunci layar
- `RebootAccessibilityService.java`: membuka menu power / mengunci layar / auto-tap "Hide apps"
- `VaultActivity.java`: layar Sembunyikan & Ruang Privat (2 tab)
- `AppHider.java`, `RealmePrivacy.java`: daftar aplikasi tersembunyi & jembatan ke Hide apps realme
- `MediaVault.java`, `ThumbLoader.java`, `MediaViewerActivity.java`: brankas foto/video
- `QuickActionActivity.java`: target shortcut ikon & tile
- `RestartTileService.java`, `LockTileService.java`: tile di panel notifikasi
