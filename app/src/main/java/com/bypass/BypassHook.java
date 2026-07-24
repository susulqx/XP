package com.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BypassHook implements IXposedHookLoadPackage {

    private static Object createFakeLlf(Class<?> llfClass) throws Exception {
        Object obj = llfClass.getDeclaredConstructor().newInstance();
        XposedHelpers.setIntField(obj, "oO0OO00", 1);
        XposedHelpers.setBooleanField(obj, "oO0OOO", true);
        XposedHelpers.setBooleanField(obj, "oO0OO0O", true);
        XposedHelpers.setLongField(obj, "oO0OO0Oo", Long.MAX_VALUE);
        XposedHelpers.setIntField(obj, "oO0OooO0", 0);
        XposedHelpers.setLongField(obj, "oO0OOO0", System.currentTimeMillis());
        XposedHelpers.setIntField(obj, "oO0OOOO0", 1);
        return obj;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"app.unique.one".equals(lpparam.packageName)) return;

        XposedBridge.log("[Bypass] === Module loaded ===");

        try {
            final Class<?> zccClass = lpparam.classLoader.loadClass("zcc");
            final Class<?> llfClass = lpparam.classLoader.loadClass("lf");

            // ===== Hook 1: oO0OO0O() =====
            XposedHelpers.findAndHookMethod(zccClass, "oO0OO0O",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] oO0OO0O()");
                        try {
                            Object obj = createFakeLlf(llfClass);
                            XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", obj);
                            return obj;
                        } catch (Throwable t) { return null; }
                    }
                });

            // ===== Hook 2: oO0OOOO() =====
            XposedHelpers.findAndHookMethod(zccClass, "oO0OOOO",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] oO0OOOO() -> true");
                        return Boolean.TRUE;
                    }
                });

            // ===== Hook 3: ActivityResult handler =====
            try {
                Class<?> rh = lpparam.classLoader.loadClass("of8$oO0OO0Oo");
                XposedHelpers.findAndHookMethod(rh, "oO0OO0Oo", int.class,
                    android.content.Intent.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[Bypass] ActivityResult handler");
                            try {
                                Object obj = createFakeLlf(llfClass);
                                XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", obj);
                                return obj;
                            } catch (Throwable t) { return null; }
                        }
                    });
                XposedBridge.log("[Bypass] Hook 3 OK");
            } catch (Throwable t) {
                XposedBridge.log("[Bypass] Hook 3 FAIL: " + t);
            }

            // ===== Hook 4: capture Lyp6 callback instance for of8 =====
            final java.util.concurrent.atomic.AtomicReference<Object> callbackRef =
                new java.util.concurrent.atomic.AtomicReference<>();
            Class<?> yp6Class = lpparam.classLoader.loadClass("yp6");
            XposedHelpers.findAndHookConstructor(yp6Class, int.class, Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int arg0 = (Integer) param.args[0];
                        Object arg1 = param.args[1];
                        if (arg0 == 4 && arg1.getClass().getName().equals("of8")) {
                            callbackRef.set(param.thisObject);
                            XposedBridge.log("[Bypass] Captured of8 callback instance");
                        }
                    }
                });
            XposedBridge.log("[Bypass] Hook 4: Lyp6 constructor");

            // ===== Hook 5: onViewCreated → invoke callback directly =====
            final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
            XposedHelpers.findAndHookMethod(of8Class, "onViewCreated",
                android.view.View.class, android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[Bypass] onViewCreated -> schedule fake callback");
                        new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    Object cb = callbackRef.get();
                                    if (cb == null) {
                                        XposedBridge.log("[Bypass] callback not captured yet");
                                        return;
                                    }
                                    try {
                                        Object fake = createFakeLlf(llfClass);
                                        XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", fake);
                                        java.lang.reflect.Method apply =
                                            cb.getClass().getDeclaredMethod("apply", Object.class);
                                        apply.setAccessible(true);
                                        apply.invoke(cb, fake);
                                        XposedBridge.log("[Bypass] Callback invoked!");
                                    } catch (Throwable t) {
                                        XposedBridge.log("[Bypass] Callback FAIL: " + t);
                                    }
                                }
                            }, 3000);
                    }
                });
            XposedBridge.log("[Bypass] Hook 5: invoke callback");

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
