# Quest Pro Controller 5GHz Enforcer

An Xposed / Vector module for the Meta Quest Pro (`seacliff`) that stops the headset from demoting the Touch Pro controllers down to 2.4GHz Wi-Fi Direct.

## Why this exists

Meta's Connection Management Service (`CMSHeadset.apk`, `com.oculus.os.cm`) handles the Wi-Fi Direct link between the headset and the two Touch Pro controllers.

Whenever a game, Air Link, Virtual Desktop, or another app requests `WIFI_MODE_FULL_LOW_LATENCY`, `CMSHeadset` calls `switchLowLatencyBand()`. In RSDB mode, Meta hardcoded this "low-latency" mode to **2.4GHz Channel 11 (2462 MHz)**.

This sends a Channel Switch Announcement (CSA) that kicks both controllers off 5GHz and drops them onto 2.4GHz Channel 11. In crowded Wi-Fi environments, this introduces packet drops, haptic delay, and tracking jitter.

## What this module does

The module hooks `com.oculus.os.cm` via Vector (Xposed) and:
1. Replaces `P2PGroupLeader$WifiStateObserver.switchLowLatencyBand()` with a no-op so the channel switch to 2.4GHz is never sent.
2. Hooks `isLowLatency()` to always report `false`.
3. Hooks `P2pChannelManager.getChannelWhenLowLatency()` to always route to the normal 5GHz channel selector.

The controllers remain on an 80MHz 5GHz Wi-Fi Direct channel (typically Channel 124 / 5620 MHz) even when low-latency mode is requested.

## Safety

- Does not touch `/persist`, `/vendor`, or `/system`.
- Runs as an ordinary Android user-space app hooked in RAM.
- Reversible at any time by disabling the module in Vector or uninstalling the APK.

## Installation

1. Install the APK:
   ```bash
   adb install -r QuestPro-Controller-5GHz-Enforcer.apk
   ```
2. Add scope in Vector / LSPosed:
   ```bash
   adb shell su -c "/data/adb/lspd/cli scope add com.lunarstar.qpro5ghz com.oculus.os.cm/0"
   ```
3. Enable module:
   ```bash
   adb shell su -c "/data/adb/lspd/cli modules enable com.lunarstar.qpro5ghz"
   ```
4. Restart CMS to apply:
   ```bash
   adb shell su -c "killall com.oculus.os.cm"
   ```

## Verification

Check logcat:
```bash
adb logcat -d -s QPro5GHz:V
```
You should see:
```text
QPro5GHz: Target process detected: com.oculus.os.cm (Connection Management Service)
QPro5GHz: Successfully hooked P2PGroupLeader$WifiStateObserver.switchLowLatencyBand()
QPro5GHz: Successfully hooked P2PGroupLeader$WifiStateObserver.isLowLatency()
QPro5GHz: Successfully hooked P2pChannelManager.getChannelWhenLowLatency()
QPro5GHz: All 5GHz controller enforcer hooks successfully installed!
```

Check Wi-Fi Direct frequency:
```bash
adb shell "dumpsys cm_wifi | grep -E '(mFrequency|mBandwidth|csaTargetChannel)'"
```
Expected:
```text
mFrequency: 5620
mBandwidth: 80
csaTargetChannel: 124
```

## Uninstalling

Disable without removing:
```bash
adb shell su -c "/data/adb/lspd/cli modules disable com.lunarstar.qpro5ghz; killall com.oculus.os.cm"
```

To remove completely:
```bash
adb uninstall com.lunarstar.qpro5ghz
```

## Thx Gemini <3
