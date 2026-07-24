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

            // ===== Hook 4: no App A — fake callback via reflection =====
            final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
            XposedHelpers.findAndHookMethod(of8Class, "onViewCreated",
                android.view.View.class, android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final Object fragment = param.thisObject;
                        XposedBridge.log("[Bypass] onViewCreated -> schedule fake callback");
                        new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    try {
                                        Object lz0 = XposedHelpers.getObjectField(fragment, "oO0o0Oo");
                                        Object callback = null;
                                        for (java.lang.reflect.Field f : lz0.getClass().getDeclaredFields()) {
                                            if (f.getType().getName().contains("ActivityResultCallback")) {
                                                f.setAccessible(true);
                                                callback = f.get(lz0);
                                                break;
                                            }
                                        }
                                        if (callback != null) {
                                            Object fake = createFakeLlf(llfClass);
                                            XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", fake);
                                            XposedHelpers.setBooleanField(fragment, "oO0o0OOo", true);
                                            java.lang.reflect.Method apply = callback.getClass()
                                                .getDeclaredMethod("apply", Object.class);
                                            apply.setAccessible(true);
                                            apply.invoke(callback, fake);
                                            XposedBridge.log("[Bypass] Fake callback OK!");
                                        } else {
                                            XposedBridge.log("[Bypass] callback not found in Lz0");
                                        }
                                    } catch (Throwable t) {
                                        XposedBridge.log("[Bypass] callback FAIL: " + t);
                                    }
                                }
                            }, 2500);
                    }
                });
            XposedBridge.log("[Bypass] Hook 4: fake callback injector");

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
