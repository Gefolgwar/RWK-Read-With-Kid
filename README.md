# Read With Kid

An app for checking children's reading aloud (working title; the final name will be decided in ticket 02).

For now the app supports one end-to-end scenario in Ukrainian and German: a parent pastes a text, the child reads it aloud with word highlighting, and when they finish a result is shown (accuracy, words per minute, duration). Speech recognition runs offline via Vosk in Full Recognition mode. Texts are kept in memory only.

## Requirements

- Java Development Kit (JDK) 17
- Android SDK
- A physical Android device running Android 8.0+ (API 26+) for proper testing of the microphone and performance (emulators can show misleading latencies).
- A USB cable to connect the phone.

---

## 1. Language models (Play Asset Delivery)

Texts can be in Ukrainian or German; the app suggests the language based on the text's alphabet, and the parent can change it. Each language has its own Vosk model, and neither is part of the base APK: they are delivered by separate on-demand asset packs `model_uk` (module `model_uk/`) and `model_de` (module `model_de/`). The first time a parent opens a text in a given language, the app immediately downloads that language's model on its own, shows the progress and unpacks it on the device. The model is removed together with the app.

Model archives are not stored in git. To build an App Bundle with the asset packs:

1. Download the archives (both Apache 2.0):
   - **`vosk-model-small-uk-v3-small.zip`**: [https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip](https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip)
   - **`vosk-model-small-de-0.15.zip`**: [https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip](https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip). Do not use `vosk-model-small-de-zamia` — it is LGPL-3.0.
2. Put each one **as is, without unpacking or renaming**: the Ukrainian one in `model_uk/src/main/assets/`, the German one in `model_de/src/main/assets/` (create the folders if they don't exist).

Without the archives, unit tests and `assembleDebug` / `installDebug` work, but `bundleDebug` will fail.

---

## 2. Build and run (from the terminal / command line)

You can compile the app and install it on a phone directly from the terminal (PowerShell, CMD, Bash) using the Gradle Wrapper.

### Step 2.1: Prepare the phone

1. Enable **Developer Options** on your Android device.
2. Enable **USB Debugging**.
3. Connect the phone to the computer with a USB cable.
4. The phone may show the prompt _"Allow USB debugging from this computer?"_ — tap **Allow**.
5. Check that the computer sees your device using `adb` (Android Debug Bridge):
   ```powershell
   adb devices
   ```
   _Your device should appear in the list next to the word `device`._

### Step 2.2: Compile and install

Open a terminal in the project folder:

```powershell
cd "D:\Project\RWK2"
```

To **compile the APK and install it right away** on the connected phone, run:

**On Windows (PowerShell/CMD):**

```powershell
.\gradlew installDebug
```

**On Mac/Linux (or Bash on Windows):**

```bash
./gradlew installDebug
```

> **Note:** If you only need to build the APK file without installing it (for example, to transfer it manually), run `.\gradlew assembleDebug`. The resulting file will be at `build/outputs/apk/debug/Read-With-Kid-debug.apk`.

### Step 2.3: Testing with a language model (bundletool, local testing)

`installDebug` installs only the base APK without the asset packs, so downloading a model in such a build will fail.

The simplest way is a single command: the script downloads the model archives and bundletool by itself, builds the AAB and installs it on the connected phone (`-Release` for a release build, `-Serial <serial>` if several phones are connected):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-on-phone.ps1
```

Manually: to test model download locally, without Google Play, use [bundletool](https://github.com/google/bundletool/releases) in local testing mode (you need the model archive from section 1 and `adb` on your `PATH`):

```powershell
.\gradlew bundleDebug
java -jar bundletool.jar build-apks --bundle=build\outputs\bundle\debug\Read-With-Kid-debug.aab --output=build\read-with-kid.apks --local-testing --connected-device
java -jar bundletool.jar install-apks --apks=build\read-with-kid.apks
```

In local testing mode the asset pack is copied to the device during `install-apks`, and the "download" in the app takes it from there, so network errors can't be tested this way.

---

## 3. Build and run (from Android Studio)

If you prefer a graphical interface:

1. Open Android Studio.
2. Click **File → Open** and select the `D:\Project\RWK2` folder.
3. Wait for the Gradle sync to finish (progress at the bottom of the screen).
4. Connect the phone via USB (as described in Step 2.1).
5. Select your device in the device drop-down on the top toolbar.
6. Click **Run** (the green triangle) or press `Shift + F10`. Like `installDebug`, this installs the app without the asset packs; to test the model, see step 2.3.

---

## 4. Release build for Google Play

The release build is shrunk by R8 (rules for JNA and Vosk are in `proguard-rules.pro`) and signed with the owner's upload key. The key and passwords are not stored in git.

1. Generate the upload key once and keep it (and the passwords) somewhere safe outside the repository:
   ```powershell
   keytool -genkeypair -v -keystore C:\keys\rwk-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Provide the key parameters in one of these ways:
   - a `keystore.properties` file in the project root (it is in `.gitignore`):
     ```properties
     storeFile=C:/keys/rwk-upload.jks
     storePassword=...
     keyAlias=upload
     keyPassword=...
     ```
   - or the environment variables `RWK_UPLOAD_STORE_FILE`, `RWK_UPLOAD_STORE_PASSWORD`, `RWK_UPLOAD_KEY_ALIAS`, `RWK_UPLOAD_KEY_PASSWORD`.

   Without these parameters the release build comes out unsigned, and Google Play will not accept it.
3. Add the model archives (section 1) and build the App Bundle with the `model_uk` and `model_de` asset packs:
   ```powershell
   .\gradlew bundleRelease
   ```
   Result: `build/outputs/bundle/release/Read-With-Kid-release.aab`.

Before uploading a new version, increase `versionCode` (and `versionName` if needed) in `build.gradle`.

---

## How to use

1. Launch the app on the phone. If the language model hasn't been downloaded yet, the app starts downloading it right away on the reading-text screen: download progress is shown, along with a clear message and a **Try again** button if there is no internet or not enough space. After that, reading works offline.
2. Paste or type a text. Below the field you can see how the text is split into sentences and words.
3. Tap **Start reading**. On the first start the app explains why it needs the microphone and asks for permission.
4. The child reads aloud: the current line is banded, words already read are underlined in green.
5. **Pause** / **Continue** turn the microphone off and on again. If the app goes to the background, reading is paused automatically.
6. Reading ends when the last word is recognised, or with the **Done** button. The result screen shows accuracy, WCPM and duration.
