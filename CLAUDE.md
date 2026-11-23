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
- `VaultActivity.SECURE` harus `true` di build final (FLAG_SECURE).
