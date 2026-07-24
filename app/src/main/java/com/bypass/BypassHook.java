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

            // ===== Hook 4: intercept Lyp6.apply() when it handles of8's result =====
            // Instead of guessing the arg0 value, just hook apply() and filter by instance type
            Class<?> yp6Class = lpparam.classLoader.loadClass("yp6");
            XposedHelpers.findAndHookMethod(yp6Class, "apply", Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            // Check if this Lyp6 instance belongs to of8
                            java.lang.reflect.Field objField = yp6Class.getDeclaredField("oO0Ooo");
                            objField.setAccessible(true);
                            Object stored = objField.get(param.thisObject);
                            if (stored != null && stored.getClass().getName().equals("of8")) {
                                XposedBridge.log("[Bypass] Intercepted of8's apply(), replacing result");
                                // Replace the parameter with fake Llf
                                param.args[0] = createFakeLlf(llfClass);
                            }
                        } catch (Throwable ig) {}
                    }
                });
            XposedBridge.log("[Bypass] Hook 4: Lyp6.apply interceptor");

            // ===== Hook 5: createIntent → skip dialog =====
            // Throwing RuntimeException instead of of8$oO0OO0O makes the
            // catch block jump to :cond_8 → return-void (no dialog shown)
            try {
                Class<?> contract = lpparam.classLoader.loadClass("of8$oO0OO0Oo");
                XposedHelpers.findAndHookMethod(contract, "oO0OO00",
                    android.content.Context.class, Object.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[Bypass] createIntent -> throw Runtime (skip dialog)");
                            try {
                                Object obj = createFakeLlf(llfClass);
                                XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", obj);
                            } catch (Throwable ig) {}
                            throw new RuntimeException("Bypass");
                        }
                    });
                XposedBridge.log("[Bypass] Hook 5: createIntent");
            } catch (Throwable t) {
                XposedBridge.log("[Bypass] Hook 5 FAIL: " + t);
            }

            // ===== Hook 6: O00OO replacement — simulate successful App A result =====
            // When O00OO runs, instead of launching ActivityResult (which would fail),
            // directly execute the logic from :pswitch_2 that would happen if App A returned valid data
            try {
                final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
                XposedHelpers.findAndHookMethod(of8Class, "O00OO",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[Bypass] O00OO replaced — simulating App A success");
                            try {
                                Object fake = createFakeLlf(llfClass);
                                XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", fake);
                                XposedHelpers.setBooleanField(param.thisObject, "oO0o0OOo", true);
                                // Call O00OO0() which sets the flag and transitions UI
                                java.lang.reflect.Method o00oo0 = of8Class.getDeclaredMethod("O00OO0");
                                o00oo0.setAccessible(true);
                                o00oo0.invoke(param.thisObject);
                                XposedBridge.log("[Bypass] O00OO0() called to transition UI");
                            } catch (Throwable t) {
                                XposedBridge.log("[Bypass] O00OO error: " + t);
                            }
                            return null;
                        }
                    });
                XposedBridge.log("[Bypass] Hook 6: O00OO replacement");
            } catch (Throwable t) {
                XposedBridge.log("[Bypass] Hook 6 FAIL: " + t);
            }

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAIL: " + t);
        }

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
