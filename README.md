# Telugu Payment Voice Alert - Native Android App

Plug-and-Play native Android Soundbox app built with **Kotlin**, **Jetpack Compose**, **Android BroadcastReceiver (`SMS_RECEIVED`)**, and **Telugu Text-To-Speech (te-IN)**.

## Features
- **Auto SMS Interception**: Background `BroadcastReceiver` wakes up instantly when incoming bank SMS arrives.
- **Payer Name Extraction**: Detects the sender / remitter name ("so and so guy", e.g. *Ramesh Kumar*, *Suresh*, *Priya*) from SMS and UPI messages.
- **Telugu Speech Engine**: Automatically announces in Telugu:
  - `"[Payer] నుండి [Amount] రూపాయలు మీ ఖాతాలో జమ అయ్యాయి."`
  - Dual-frequency payment chime audio.
- **WakeLock Support**: Speaks aloud even when phone screen is locked or turned off.
- **Jetpack Compose UI**: Shows live received payments feed, active permission status, and quick voice test buttons.

## 🚀 Quick Start (Plug & Play)

### 1. Open in Android Studio
1. Open **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer).
2. Click **File > Open...** and select this `android` folder.
3. Android Studio will automatically sync Gradle and download dependencies.

### 2. Run on your Phone
1. Connect your Android phone via USB (or Wireless Debugging).
2. Enable **Developer Options** > **USB Debugging** on your phone.
3. Click the green **Run (▶)** button in Android Studio.
4. On the phone, tap **"Grant SMS"** when prompted to allow `RECEIVE_SMS`.

### 3. Generate APK (No Computer Cable Needed)
To export an `.apk` file that you can share or install directly on any phone:
1. In Android Studio, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Once finished, click **"locate"** to get your `app-debug.apk`.
3. Transfer `app-debug.apk` to any phone via WhatsApp, Drive, or Bluetooth and tap **Install**!
