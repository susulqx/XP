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
        XposedHelpers.setIntField(obj, "oO0OO00", 1);         // st = 1 (status=valid)
        XposedHelpers.setBooleanField(obj, "oO0OOO", true);    // al = true (active)
        XposedHelpers.setBooleanField(obj, "oO0OO0O", true);   // pu = true (premium)
        XposedHelpers.setLongField(obj, "oO0OO0Oo", Long.MAX_VALUE); // ex = MAX (never expires)
        XposedHelpers.setIntField(obj, "oO0OooO0", 0);         // te = 0 (type)
        XposedHelpers.setLongField(obj, "oO0OOO0", System.currentTimeMillis()); // ca = now
        XposedHelpers.setIntField(obj, "oO0OOOO0", 1);         // v = 1 (version)
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

        // ===== Hook 4: of8.O00OO() — 跳过 ActivityResult，直接设已订阅 =====
        try {
            final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
            XposedHelpers.findAndHookMethod(of8Class, "O00OO",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] O00OO() -> skip, set subscribed");
                        XposedHelpers.setBooleanField(param.thisObject, "oO0o0OOo", true);
                        return null;
                    }
                });
            XposedBridge.log("[Bypass] Hook 4: O00OO()");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 4 FAIL: " + t);
        }

        // ===== Hook 5: of8.O00OO0() — 阻止空白页 =====
        try {
            final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
            XposedHelpers.findAndHookMethod(of8Class, "O00OO0",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] O00OO0() blocked");
                        XposedHelpers.setBooleanField(param.thisObject, "oO0o0OOo", true);
                        return null;
                    }
                });
            XposedBridge.log("[Bypass] Hook 5: O00OO0()");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 5 FAIL: " + t);
        }

        // ===== Hook 6: of8.onViewCreated — 确保标志位在所有流程后都设好 =====
        try {
            final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
            XposedHelpers.findAndHookMethod(of8Class, "onViewCreated",
                android.view.View.class, android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedHelpers.setBooleanField(param.thisObject, "oO0o0OOo", true);
                        XposedBridge.log("[Bypass] onViewCreated -> oO0o0OOo=true");
                    }
                });
            XposedBridge.log("[Bypass] Hook 6: onViewCreated");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 6 FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
