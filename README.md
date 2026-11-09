# compass for Android

An Android client for [compass](https://github.com/kossLAN/compass), the WireGuard mesh VPN.

## Building

```sh
git clone --recursive https://github.com/kossLAN/compass-android
cd compass-android
nix develop
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
