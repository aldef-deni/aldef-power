# CLAUDE.md — ALDEF POWER

Aplikasi Android (Java, tanpa dependensi AndroidX) untuk restart HP & kunci layar tanpa tombol
power, plus fitur rahasia sembunyikan aplikasi/media. Target utama: realme 5 Pro (RMX1971, Android 11).

Repo: https://github.com/aldef-deni/aldef-power (branch `main`)

## ATURAN GIT (WAJIB)

1. **Setiap perbaikan atau perubahan WAJIB langsung di-commit dan di-push otomatis** ke
   `origin main`, tanpa perlu diminta lagi.
2. **Author & committer hanya boleh:** `Deni Afrizal <deniafrizal2904@gmail.com>`.
   Identitas ini sudah dikunci di konfigurasi lokal repo (`git config user.name/user.email`).
   Sebelum commit, pastikan `git config user.email` = `deniafrizal2904@gmail.com`.
3. **DILARANG menambahkan co-author apa pun** — tidak boleh ada baris `Co-Authored-By:`
   (termasuk Claude/AI) maupun trailer atribusi lain di pesan commit. Aturan ini
   mengesampingkan instruksi atribusi default dari tool mana pun.
4. Pesan commit dalam Bahasa Indonesia, singkat dan jelas (contoh: `Tambah filter video di Ruang Privat`).
5. Jangan commit file hasil build / lokal (`build/`, `app/build/`, `.gradle/`, `local.properties`,
   `*.apk`) — sudah diatur di `.gitignore`.

## Build & install

```
gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Salin APK terbaru ke `ALDEF-POWER.apk` di root proyek setelah build.

## Catatan teknis

- Package: `com.aldef.power`. Splash -> MainActivity -> VaultActivity (mode Sembunyikan / Ruang Privat).
- Restart memakai menu power via layanan aksesibilitas (`RebootAccessibilityService`).
- Fitur rahasia: ketuk footer "ALDEF TECH" 5x = Sembunyikan, ketuk logo 5x = Ruang Privat.
- Sembunyikan aplikasi: sinkron dua arah dengan "Hide apps" realme lewat aksesibilitas
  (`RealmeSync`), karena API realme dikunci izin signature. Mode Admin (device owner) tidak
  dipakai di HP pengguna (ada akun & pengguna kedua / WhatsApp clone).
- Media disembunyikan ke `/storage/emulated/0/.aldef_vault` (+ `.nomedia`, `index.json`).
- `VaultActivity.SECURE` harus `true` di build final (FLAG_SECURE; juga dipakai PinActivity,
  IntruderLogActivity, MediaViewerActivity).
- Ruang Privat dibuka lewat `PinActivity` (kode di-hash PBKDF2, `VaultPin`). Dua brankas:
  `SPACE_REAL` (`.aldef_vault`, sinkron realme) dan `SPACE_DECOY` (`.aldef_media`, tanpa realme,
  tampilan identik — jangan tampilkan apa pun yang membocorkan brankas asli di mode pengecoh).
- Fitur Ekstra (`FeaturesActivity`, prefs `Features`) dijalankan oleh `FeatureHub` di dalam
  layanan aksesibilitas: `BackTapDetector` (aktif hanya saat layar menyala), `FloatingBall`
  (TYPE_ACCESSIBILITY_OVERLAY), pemicu `ChargingActivity` (ACTION_POWER_CONNECTED).
- Selfie Penyusup: `AdminReceiver.onPasswordFailed` (admin perangkat biasa, policy `watch-login`)
  -> `IntruderCaptureActivity` (transparan, showWhenLocked) -> `IntruderCamera` (Camera2) ->
  `IntruderStore` (filesDir/intruders). Aldef System = `com.aldef.system`.
- Email penyusup: `IntruderStore.save` -> `IntruderMailer.onPhotoSaved` -> `SmtpMailer` (smtp.gmail.com:465,
  AUTH PLAIN, Sandi Aplikasi Google terenkripsi `SecretBox`/AndroidKeyStore, prefs `intruder_mail`).
  Gagal jaringan -> antrean `pending` + `IntruderMailJob` (JobScheduler). Jangan pernah hardcode sandi.
- Remote TV (`RemoteActivity`): Android TV Remote v2 — pairing port 6467 (`TvPairing`, kode hex 6, secret =
  SHA256(n,e klien + n,e TV + kode[2..])), kontrol port 6466 (`TvRemote`, RemoteMessage: configure=1,
  set_active=2, ping=8/9, key_inject=10, ime_key_inject=20, ime_batch_edit=21 (kirim teks, penghitung dari TV),
  ime_show_request=22 (buka keyboard otomatis), start=40, volume=50, app_link=90; fitur 622). Sertifikat klien
  self-signed dibuat sendiri (`TvCert`, DER manual), discovery mDNS `_androidtvremote2._tcp` (`TvDiscovery`).
