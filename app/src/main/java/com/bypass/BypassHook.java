package com.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BypassHook implements IXposedHookLoadPackage {

    private static Object fakeLlf;

    private static Object createFakeLlf(Class<?> llfClass) throws Exception {
        Object obj = llfClass.getDeclaredConstructor().newInstance();
        XposedHelpers.setBooleanField(obj, "oO0OOO", true);
        XposedHelpers.setBooleanField(obj, "oO0OO0O", true);
        XposedHelpers.setLongField(obj, "oO0OO0Oo", Long.MAX_VALUE);
        XposedHelpers.setIntField(obj, "oO0OooO0", 0);
        return obj;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"app.unique.one".equals(lpparam.packageName)) return;

        XposedBridge.log("[Bypass] === Module loaded ===");

        try {
            final Class<?> zccClass = lpparam.classLoader.loadClass("zcc");
            final Class<?> llfClass = lpparam.classLoader.loadClass("lf");
            fakeLlf = createFakeLlf(llfClass);

            // ===== Hook 1: Lzcc.oO0OO0O() — 完全替换返回假订阅 =====
            XposedHelpers.findAndHookMethod(zccClass, "oO0OO0O",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] REPLACE oO0OO0O()");
                        // Re-create fresh each call to avoid state corruption
                        try { fakeLlf = createFakeLlf(llfClass); } catch (Throwable ig) {}
                        XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", fakeLlf);
                        return fakeLlf;
                    }
                });
            XposedBridge.log("[Bypass] Hook 1: oO0OO0O() [REPLACEMENT]");

            // ===== Hook 2: Lzcc.oO0OOOO() — 完全替换返回 true =====
            XposedHelpers.findAndHookMethod(zccClass, "oO0OOOO",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] REPLACE oO0OOOO() -> true");
                        return Boolean.TRUE;
                    }
                });
            XposedBridge.log("[Bypass] Hook 2: oO0OOOO() [REPLACEMENT]");

            // ===== Hook 3: of8$oO0OO0Oo.oO0OO0Oo — 完全替换 ActivityResult =====
            try {
                Class<?> rh = lpparam.classLoader.loadClass("of8$oO0OO0Oo");
                XposedHelpers.findAndHookMethod(rh, "oO0OO0Oo", int.class,
                    android.content.Intent.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[Bypass] REPLACE ActivityResult handler");
                            try { fakeLlf = createFakeLlf(llfClass); } catch (Throwable ig) {}
                            XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", fakeLlf);
                            return fakeLlf;
                        }
                    });
                XposedBridge.log("[Bypass] Hook 3: ActivityResult [REPLACEMENT]");
            } catch (Throwable t) {
                XposedBridge.log("[Bypass] Hook 3 FAIL: " + t);
            }

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hooks FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
