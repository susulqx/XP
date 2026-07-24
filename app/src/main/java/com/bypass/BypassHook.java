package com.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BypassHook implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "app.unique.one";
    private static final String CLASS_ZCC = "zcc";    // Lzcc;
    private static final String CLASS_LLF = "lf";      // Llf;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log("[Bypass] Hook module loaded for " + TARGET_PKG);

        // ===== Hook 1: Lzcc.oO0OO0O() -> always return valid Llf =====
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_ZCC,
                lpparam.classLoader,
                "oO0OO0O",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] oO0OO0O() hooked");
                        try {
                            Class<?> llfClass = XposedHelpers.findClass(CLASS_LLF, lpparam.classLoader);
                            Object obj = llfClass.newInstance();

                            // Set fields: al=true, pu=true, ex=MAX_LONG, te=0
                            XposedHelpers.setBooleanField(obj, "oO0OOO", true);
                            XposedHelpers.setBooleanField(obj, "oO0OO0O", true);
                            XposedHelpers.setLongField(obj, "oO0OO0Oo", Long.MAX_VALUE);
                            XposedHelpers.setIntField(obj, "oO0OooO0", 0);

                            // Store in static cache
                            XposedHelpers.setStaticObjectField(
                                lpparam.classLoader.loadClass(CLASS_ZCC),
                                "oO0OOO0",
                                obj
                            );

                            param.setResult(obj);
                            XposedBridge.log("[Bypass] Faked subscription object");
                        } catch (Throwable t) {
                            XposedBridge.log("[Bypass] Error creating Llf: " + t);
                        }
                    }
                }
            );
            XposedBridge.log("[Bypass] Hook 1 installed: oO0OO0O()");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAILED Hook 1: " + t);
        }

        // ===== Hook 2: Lzcc.oO0OOOO() -> always return true =====
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_ZCC,
                lpparam.classLoader,
                "oO0OOOO",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] oO0OOOO() hooked -> true");
                        param.setResult(true);
                    }
                }
            );
            XposedBridge.log("[Bypass] Hook 2 installed: oO0OOOO()");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAILED Hook 2: " + t);
        }

        XposedBridge.log("[Bypass] All hooks installed for " + TARGET_PKG);
    }
}
