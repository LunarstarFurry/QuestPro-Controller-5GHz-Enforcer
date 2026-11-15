package com.lunarstar.qpro5ghz;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "QPro5GHz";

    private static void log(String msg) {
        Log.i(TAG, msg);
        XposedBridge.log("[" + TAG + "] " + msg);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"com.oculus.os.cm".equals(lpparam.packageName)) {
            return;
        }

        log("Target process detected: com.oculus.os.cm (Connection Management Service)");

        // Hook 1: Suppress switchLowLatencyBand in WifiStateObserver
        try {
            Class<?> observerCls = XposedHelpers.findClassIfExists(
                    "com.oculus.os.cm.P2PGroupLeader$WifiStateObserver", lpparam.classLoader);
            if (observerCls != null) {
                XposedHelpers.findAndHookMethod(observerCls, "switchLowLatencyBand", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        log("switchLowLatencyBand() called -> BLOCKED (preventing CSA downgrade to 2.4GHz)");
                        return null;
                    }
                });
                log("Successfully hooked P2PGroupLeader$WifiStateObserver.switchLowLatencyBand()");

                // Hook isLowLatency to always report false to P2PGroupLeader
                XposedHelpers.findAndHookMethod(observerCls, "isLowLatency", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return false;
                    }
                });
                log("Successfully hooked P2PGroupLeader$WifiStateObserver.isLowLatency()");
            } else {
                log("Warning: P2PGroupLeader$WifiStateObserver class not found");
            }
        } catch (Throwable t) {
            log("Error hooking WifiStateObserver: " + t.getMessage());
        }

        // Hook 2: Override getChannelWhenLowLatency, getChannelWhenNotLowLatency, and getNewChannel in P2pChannelManager
        try {
            Class<?> channelMgrCls = XposedHelpers.findClassIfExists(
                    "com.oculus.os.cm.P2pChannelManager", lpparam.classLoader);
            if (channelMgrCls != null) {
                XposedHelpers.findAndHookMethod(channelMgrCls, "getChannelWhenLowLatency",
                        int.class, int.class, String.class, new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                int staFreq = (int) param.args[0];
                                int curFreq = (int) param.args[1];
                                String countryCode = (String) param.args[2];
                                log("getChannelWhenLowLatency(staFreq=" + staFreq + ", curFreq=" + curFreq + ", cc=" + countryCode + ") intercepted -> redirecting to getChannelWhenNotLowLatency()");
                                // Call getChannelWhenNotLowLatency(int, int, String, boolean)
                                Object result = XposedHelpers.callStaticMethod(channelMgrCls, "getChannelWhenNotLowLatency",
                                        staFreq, curFreq, countryCode, true);
                                log("getChannelWhenNotLowLatency returned channel: " + result);
                                return result;
                            }
                        });
                log("Successfully hooked P2pChannelManager.getChannelWhenLowLatency()");

                XposedHelpers.findAndHookMethod(channelMgrCls, "getChannelWhenNotLowLatency",
                        int.class, int.class, String.class, boolean.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                int ch = (int) param.getResult();
                                if (ch <= 14) {
                                    int staFreq = (int) param.args[0];
                                    String countryCode = (String) param.args[2];
                                    int forcedCh = resolveSafe5GHzChannel(channelMgrCls, countryCode);
                                    log("getChannelWhenNotLowLatency(staFreq=" + staFreq + ", cc=" + countryCode + ") returned 2.4GHz ch=" + ch + " -> forcing 5GHz ch=" + forcedCh);
                                    param.setResult(forcedCh);
                                }
                            }
                        });
                log("Successfully hooked P2pChannelManager.getChannelWhenNotLowLatency()");

                XposedHelpers.findAndHookMethod(channelMgrCls, "getNewChannel",
                        int.class, int.class, boolean.class, boolean.class, String.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                int ch = (int) param.getResult();
                                if (ch <= 14) {
                                    int staFreq = (int) param.args[0];
                                    String countryCode = (String) param.args[4];
                                    int forcedCh = resolveSafe5GHzChannel(channelMgrCls, countryCode);
                                    log("getNewChannel(staFreq=" + staFreq + ", cc=" + countryCode + ") returned 2.4GHz ch=" + ch + " -> forcing 5GHz ch=" + forcedCh);
                                    param.setResult(forcedCh);
                                }
                            }
                        });
                log("Successfully hooked P2pChannelManager.getNewChannel()");
            } else {
                log("Warning: P2pChannelManager class not found");
            }
        } catch (Throwable t) {
            log("Error hooking P2pChannelManager: " + t.getMessage());
        }

        log("All 5GHz controller enforcer hooks successfully installed!");
    }

    private static int resolveSafe5GHzChannel(Class<?> channelMgrCls, String countryCode) {
        try {
            int safeFreq = (int) XposedHelpers.callStaticMethod(channelMgrCls, "getSafeFrequency", countryCode);
            if (safeFreq < 5000) {
                safeFreq = 5180; // Channel 36 fallback
            }
            return (int) XposedHelpers.callStaticMethod(channelMgrCls, "getChannelFromFrequency", safeFreq);
        } catch (Throwable t) {
            log("Error resolving safe 5GHz channel, defaulting to Ch 36: " + t.getMessage());
            return 36;
        }
    }
}
