package com.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BypassHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"app.unique.one".equals(lpparam.packageName)) return;

        XposedBridge.log("[Bypass] === Module loaded ===");

        // ===== Hook 1: Lzcc.oO0OO0O() — 始终返回有效订阅 =====
        try {
            final Class<?> zccClass = lpparam.classLoader.loadClass("zcc");
            final Class<?> llfClass = lpparam.classLoader.loadClass("lf");

            XposedHelpers.findAndHookMethod(zccClass, "oO0OO0O", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object obj = llfClass.newInstance();
                    XposedHelpers.setBooleanField(obj, "oO0OOO", true);
                    XposedHelpers.setBooleanField(obj, "oO0OO0O", true);
                    XposedHelpers.setLongField(obj, "oO0OO0Oo", Long.MAX_VALUE);
                    XposedHelpers.setIntField(obj, "oO0OooO0", 0);
                    XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", obj);
                    param.setResult(obj);
                }
            });
            XposedBridge.log("[Bypass] Hook 1: oO0OO0O()");

            // ===== Hook 2: Lzcc.oO0OOOO() — 始终返回 true =====
            XposedHelpers.findAndHookMethod(zccClass, "oO0OOOO", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });
            XposedBridge.log("[Bypass] Hook 2: oO0OOOO()");

            // ===== Hook 3: of8$oO0OO0Oo.oO0OO0Oo — ActivityResult 拦截 =====
            try {
                Class<?> rh = lpparam.classLoader.loadClass("of8$oO0OO0Oo");
                XposedHelpers.findAndHookMethod(rh, "oO0OO0Oo", int.class,
                    android.content.Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object obj = llfClass.newInstance();
                        XposedHelpers.setBooleanField(obj, "oO0OOO", true);
                        XposedHelpers.setBooleanField(obj, "oO0OO0O", true);
                        XposedHelpers.setLongField(obj, "oO0OO0Oo", Long.MAX_VALUE);
                        XposedHelpers.setIntField(obj, "oO0OooO0", 0);
                        param.setResult(obj);
                    }
                });
                XposedBridge.log("[Bypass] Hook 3: ActivityResult handler");
            } catch (Throwable t) {
                XposedBridge.log("[Bypass] Hook 3 FAIL: " + t);
            }

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hooks 1-2 FAIL: " + t);
        }

        // ===== Hook 4: Activity.isTaskRoot() — 返回 false 防止 finish() =====
        try {
            XposedHelpers.findAndHookMethod(
                android.app.Activity.class, "isTaskRoot",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(false);
                        XposedBridge.log("[Bypass] isTaskRoot -> false");
                    }
                }
            );
            XposedBridge.log("[Bypass] Hook 4: isTaskRoot()");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 4 FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
