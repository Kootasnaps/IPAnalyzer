# Location Mover
Private native Android app. Start/Stop fixed mock GPS location, saved favourites, offline-only. No IP/VPN change.

## Install
Download `Location-Mover-Android-APK` from the successful GitHub Actions run, unzip to get `app-debug.apk`, install on Android. Android Settings → Developer options → Select mock location app → Location Mover; then allow precise location and use Start.

## Build
Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17, Android API 35:
`gradle --no-daemon :app:assembleDebug`.
Debug APK is automatically signed with Android's debug signing key, suitable for sideloading (not for Play Store production).

Notes: Android mock locations are detectable. GPS only; no VPN, IP change, root, ads, or network permission. Actual compatibility on Galaxy S25 Ultra still requires physical-device testing.
