# Warehouse for Android

Native Android rewrite of the Russian warehouse PWA using Kotlin and Jetpack Compose.

## Build

Open this directory in Android Studio and install Android SDK Platform 35. Then run:

```sh
./gradlew assembleDebug
```

If you already have Gradle and Android SDK 35 installed, `gradle assembleDebug` also works from this directory.

The app requires Android 8.0 (API 26) or later. Camera access is requested only when barcode scanning starts.

## Included workflows

- Dashboard search by product name, article, or barcode
- Product catalog: add, edit, search, and remove products
- Manual receiving and separate B-Ware quantities
- Barcode and QR scanning with the device camera
- Inventory counts saved by warehouse address
- Receipt history with stock adjustment when a batch is removed
- Local task list
- CSV and XLSX catalog import/export
- JSON backup/restore, including the legacy `my_off_db` and `my_off_arr6` browser data keys
- Persistent light/dark theme preference

Data is stored on the device using app-private preferences. JSON backups include products, receipts, inventory addresses, tasks, and appearance settings. CSV/XLSX files are product-catalog exports.

The original web app also has workflows backed by separately configured services (invoice OCR, Google Drive, Gemini chat, and account-synced tasks/profile). Those services need their existing endpoint/account configuration before they can be ported; this Android build does not simulate their responses.