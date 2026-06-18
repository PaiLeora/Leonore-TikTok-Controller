# Leonore TikTok Controller

Accessibility-based auto controller untuk TikTok / Instagram Reels / YouTube Shorts.
Dibangun oleh **Leonore Tech Team**. 100% lokal di device, tanpa root, tanpa backend/server.

## Fitur

- **Accessibility Control**: swipe up (next), swipe down (previous), tap like otomatis.
- **Voice Control offline**: command "up", "down", "like", "pause", "resume" via `SpeechRecognizer` bawaan Android dengan `EXTRA_PREFER_OFFLINE`.
- **Gesture Control (basic placeholder)**: tombol simulasi trigger gesture, siap diganti dengan input kamera/MediaPipe nanti.
- **Auto Scroll Mode**: scroll otomatis setiap N detik (default 5s, adjustable 3–30s lewat slider).
- **Dashboard**: status Accessibility Service & Microphone, kontrol manual, loading screen branded.

## Struktur Project

```
LeonoreTikTokController/
├── app/
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/leonoretech/tiktokcontroller/
│   │   │   ├── SplashActivity.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── LeonoreAccessibilityService.kt
│   │   │   ├── VoiceControlManager.kt
│   │   │   └── AutoScrollForegroundService.kt
│   │   └── res/ (layout, values, xml, drawable, mipmap)
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
└── codemagic.yaml
```

## Cara Build di Codemagic (tanpa Android Studio lokal)

1. Push seluruh folder project ini ke repo GitHub baru (root repo = isi folder `LeonoreTikTokController/`).
2. Login ke [codemagic.io](https://codemagic.io) → **Add application** → pilih repo GitHub kamu.
3. Codemagic akan otomatis mendeteksi `codemagic.yaml` di root repo. Pilih workflow **android-release**.
4. Klik **Start new build**.
5. Tunggu sampai selesai → APK release otomatis muncul di tab **Artifacts** pada hasil build (`app/build/outputs/apk/release/app-release.apk`).
6. Download APK, lalu install manual di Android (aktifkan "Install from unknown sources" jika diminta).

### Catatan penting soal `gradle-wrapper.jar`

Repo ini menyertakan `gradlew`, `gradlew.bat`, dan `gradle-wrapper.properties`, tapi **file binary `gradle-wrapper.jar` tidak disertakan** (binary tidak bisa digenerate di sandbox pembuatan project ini). Untuk mengatasi ini ada 2 opsi, **pilih salah satu**:

- **Opsi A (paling mudah, sudah otomatis)**: Step `Generate Gradle wrapper` di `codemagic.yaml` akan menjalankan `gradle wrapper --gradle-version 8.7` menggunakan Gradle yang sudah terinstall di image Codemagic, sehingga `gradle-wrapper.jar` otomatis dibuat saat build pertama. Tidak perlu tindakan tambahan — cukup push dan build.
- **Opsi B (kalau mau commit wrapper sendiri)**: Jalankan `gradle wrapper --gradle-version 8.7` sekali di komputer mana pun yang punya Gradle terinstall (tidak harus Android Studio, cukup Gradle CLI), lalu commit file `gradle/wrapper/gradle-wrapper.jar` yang ter-generate ke repo.

## Cara Pakai di HP

1. Install APK hasil build.
2. Buka app → tunggu splash screen "Leonore" selesai.
3. Tap **Aktifkan Accessibility Service** → di halaman Settings Android, cari "Leonore TikTok Controller" → toggle ON.
4. Balik ke app, status Accessibility akan berubah jadi **AKTIF**.
5. (Opsional) Tap **Aktifkan Voice Control** → izinkan permission microphone.
6. Buka TikTok / Instagram / YouTube → kembali ke background, controller akan bekerja:
   - Tombol manual di dashboard untuk swipe/like langsung.
   - Voice command "up"/"down"/"like"/"pause"/"resume".
   - Auto Scroll Mode untuk scroll otomatis berkala.

## Catatan Teknis

- **Like detection** bekerja secara heuristik (mencari teks/content-description "like", "Suka", atau view ID yang mengandung kata "like"). TikTok/Instagram/YouTube sering mengubah struktur UI mereka, jadi detection like button bisa butuh penyesuaian dari waktu ke waktu jika app target update.
- Package name yang didukung: `com.zhiliaoapp.musically` & `com.ss.android.ugc.trill` (TikTok), `com.instagram.android` (Instagram Reels), `com.google.android.youtube` (YouTube Shorts).
- Voice recognition offline bergantung pada model bahasa offline yang sudah didownload user di **Settings → System → Languages & input → Voice input → Offline speech recognition** (biasanya lewat Google app / Gboard). Tanpa model offline, sistem akan fallback ke online recognition jika ada koneksi internet — app sendiri tidak mengirim data ke server manapun.
- Signing config release memakai debug keystore (auto-generate) supaya APK release langsung installable tanpa setup keystore tambahan. Untuk rilis ke Play Store, ganti dengan signing config produksi.
