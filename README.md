# Quest Pro 5 GHz Controller Enforcer (`com.lunarstar.qpro5ghz`)

An Xposed / Vector module for Meta Quest Pro (`seacliff`) that forces the Touch Pro (*Starlet*) controllers to maintain high-bandwidth **5 GHz Wi-Fi Direct** (80 MHz bandwidth, e.g. Channel 124 / 5620 MHz) at all times.

---

## Background & Problem

Meta's Connection Management Service (`CMSHeadset.apk`, package `com.oculus.os.cm`, running under UID 1000) manages the Wi-Fi Direct P2P link between the headset and the two Touch Pro controllers.

When normal applications or games request `WIFI_MODE_FULL_LOW_LATENCY` (e.g., VR games, Air Link, Virtual Desktop, Parallax), `CMSHeadset` calls:
`com.oculus.os.cm.P2PGroupLeader$WifiStateObserver.switchLowLatencyBand()`

In RSDB (Real Simultaneous Dual Band) mode, Meta hardcodes low-latency controller mode to **2462 MHz (2.4 GHz Channel 11)** in:
`P2pChannelManager.getChannelWhenLowLatency(int, int, String)`

This causes a P2P Channel Switch Announcement (`P2P_CSA_ATTEMPT`) that demotes both controllers from 5 GHz down to 2.4 GHz Channel 11, introducing jitter, packet drops, and higher tracking/haptic latency in congested 2.4 GHz environments.

---

## Solution

This module injects into `com.oculus.os.cm` in RAM using the **Vector** (Xposed) framework:

1. **Blocks CSA Switch to 2.4 GHz:**
   Hooks `P2PGroupLeader$WifiStateObserver.switchLowLatencyBand()` with `XC_MethodReplacement.DO_NOTHING`.
2. **Overrides Low Latency State Flag:**
   Hooks `P2PGroupLeader$WifiStateObserver.isLowLatency()` to always return `false`.
3. **Redirects Channel Selection:**
   Hooks `P2pChannelManager.getChannelWhenLowLatency(int, int, String)` to forward directly to `getChannelWhenNotLowLatency(..., false)`.

### Safety & Reversibility
- **Zero modification** to `/persist`, `/boot`, `/vendor`, or `/system` partitions.
- Runs purely as an unprivileged user-space Android APK in RAM.
- **100% reversible** at any time by toggling off the module in Vector or uninstalling the APK.

---

## Project Structure

```
QPro5GHzEnforcer/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Xposed module declarations and scope
│   │   ├── assets/xposed_init                   # Points to HookEntry
│   │   ├── java/com/lunarstar/qpro5ghz/
│   │   │   └── HookEntry.java                   # Hook logic
│   │   └── res/values/
│   │       ├── arrays.xml                       # xposedscope: com.oculus.os.cm
│   │       └── strings.xml                      # Module name & description
│   └── build.gradle
├── xposed-stubs/                                # Local offline Xposed API stubs (no remote maven needed)
│   ├── src/main/java/de/robv/android/xposed/
│   └── build.gradle
├── gradlew / gradlew.bat                        # Gradle build wrapper
├── build.gradle                                 # Root project build script
└── settings.gradle                              # Project settings
```

---

## Building from Source

### Using Command Line:
```powershell
.\gradlew.bat :app:assembleDebug
```
The output APK will be generated at:
`app\build\outputs\apk\debug\app-debug.apk`

### Using Android Studio:
Open this folder (`QPro5GHzEnforcer`) directly in Android Studio and run **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

---

## Installation & Setup

1. **Install the APK:**
   ```powershell
   adb install -r app-debug.apk
   ```

2. **Add Vector / LSPosed Scope:**
   ```powershell
   adb shell su -c "/data/adb/lspd/cli scope add com.lunarstar.qpro5ghz com.oculus.os.cm/0"
   ```

3. **Enable the Module:**
   ```powershell
   adb shell su -c "/data/adb/lspd/cli modules enable com.lunarstar.qpro5ghz"
   ```

4. **Restart Connection Management Service:**
   ```powershell
   adb shell su -c "killall com.oculus.os.cm"
   ```

---

## Verification

### Check Logcat:
```powershell
adb logcat -d -s QPro5GHz:V
```
Expected output:
```text
QPro5GHz: Target process detected: com.oculus.os.cm (Connection Management Service)
QPro5GHz: Successfully hooked P2PGroupLeader$WifiStateObserver.switchLowLatencyBand()
QPro5GHz: Successfully hooked P2PGroupLeader$WifiStateObserver.isLowLatency()
QPro5GHz: Successfully hooked P2pChannelManager.getChannelWhenLowLatency()
QPro5GHz: All 5GHz controller enforcer hooks successfully installed!
```

### Check Wi-Fi Direct Frequency:
```powershell
adb shell "dumpsys cm_wifi | grep -E '(mFrequency|mBandwidth|csaTargetChannel)'"
```
Expected output:
```text
mFrequency: 5620
mBandwidth: 80
csaTargetChannel: 124
```

---

## How to Uninstall / Revert

To disable without uninstalling:
```powershell
adb shell su -c "/data/adb/lspd/cli modules disable com.lunarstar.qpro5ghz; killall com.oculus.os.cm"
```

To completely remove:
```powershell
adb uninstall com.lunarstar.qpro5ghz
```
