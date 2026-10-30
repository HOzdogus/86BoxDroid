# 86BoxDroid

An Android port of [86Box](https://86box.net) for playing Windows 95/98-era PC games on a phone.
It uses 86Box's own SDL front end (with its on-screen menu) plus a small Android launcher.

**Download:** the newest APK is always on the [latest release](../../releases/tag/latest).

## First run
1. Allow file access (so you can drop files into `/sdcard/86BoxDroid`).
2. Tap **Download ROMs**: this fetches the 86Box BIOS set from github.com/86Box/roms.
3. Put a Windows 95/98 CD image in `/sdcard/86BoxDroid/isos/` (and a boot floppy in `floppies/` if your CD isn't bootable).
4. **Start PC**, press Back to open the menu, mount the CD/floppy, and reset.

## Touch controls
| Gesture | Action |
|---|---|
| Drag one finger | Move the mouse |
| Tap | Left click |
| Tap, then touch and drag | Hold left button |
| Two-finger tap | Right click |
| Three-finger tap | On-screen keyboard |
| Back | Menu (mount media, reset, quit) |

## Building
GitHub Actions builds everything (`.github/workflows/android.yml`).
Locally: set `ANDROID_NDK_HOME` and `VCPKG_ROOT`, run `android/build-native.sh`, then `gradle -p android assembleDebug`.

Android-specific changes to 86Box itself are marked `__ANDROID__` in `src/unix/sdl_main.c` and `ANDROID` in `src/CMakeLists.txt`.
