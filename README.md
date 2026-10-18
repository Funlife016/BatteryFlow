# BatteryFlow

Free and open-source battery monitor for Android (Material 3, Jetpack Compose, Room).
Inspired by Ampere / Ampere Flow. No ads, no tracking, no network permission — all data stays on your device.

## Features
- Live charge/discharge current (mA), power (W), voltage, temperature, level, health, status
- Background logging every 30 s via a foreground service (survives reboot)
- History charts (6 h / 24 h / 7 d / 30 d) for level, temperature and current
- Usage insight: drain rate with screen on vs. screen off
- Estimated battery capacity from the fuel-gauge charge counter
- Material You dynamic colors, light/dark
- Auto-prunes data older than 90 days

## Build
Open in Android Studio (Koala+) and run, or: `gradle wrapper && ./gradlew assembleDebug`.
CI builds a debug APK on every push (`.github/workflows/build.yml`).

## Notes
Current sensors differ per vendor (some report µA, some mA, some invert the sign); the app normalizes
the sign using the charging state. Android may need battery-optimization exemption for reliable logging on aggressive OEM ROMs (see dontkillmyapp.com).

## License
Suggested: GPL-3.0-or-later — add a `LICENSE` file before publishing.
