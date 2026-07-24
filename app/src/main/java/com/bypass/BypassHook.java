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

        // ===== Hook 4: of8.O00OO() — 在 ActivityResult 启动前拦截 =====
        // Don't replace the whole method, just hack the launcher to fake success
        try {
            final Class<?> of8Class = lpparam.classLoader.loadClass("of8");
            XposedHelpers.findAndHookMethod(of8Class, "O00OO",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("[Bypass] O00OO() called - injecting fake result");
                        // After the original runs, the launch would happen. 
                        // We intercept the launcher via a delayed post.
                        final Object fragment = param.thisObject;
                        // Settle subscription in cache before the launch is reached
                        XposedHelpers.setBooleanField(fragment, "oO0o0OOo", true);
                        // Store fake Llf so any re-check passes
                        try {
                            Class<?> zccClass = fragment.getClass().getClassLoader().loadClass("zcc");
                            Class<?> llfClass = fragment.getClass().getClassLoader().loadClass("lf");
                            Object freshFake = createFakeLlf(llfClass);
                            XposedHelpers.setStaticObjectField(zccClass, "oO0OOO0", freshFake);
                        } catch (Throwable ig) {}
                    }
                });
            XposedBridge.log("[Bypass] Hook 4: O00OO() [before]");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 4 FAIL: " + t);
        }

        // ===== Hook 4b: Fragment ActivityResultLauncher — 劫持启动 =====
        // Hook Lz0.oO0OO00 (the launch method) specifically for of8's launcher
        try {
            final Class<?> lz0Class = lpparam.classLoader.loadClass("z0");
            XposedHelpers.findAndHookMethod(lz0Class, "oO0OO00",
                java.lang.Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // This is called for ALL Lz0 launchers. 
                        // We want to only intercept the one in of8.
                        // The launcher object is param.thisObject
                        try {
                            Class<?> of8Class = lpparam.classLoader.loadClass("of8");
                            // Check if this launcher belongs to of8 by trying to find
                            // the of8 fragment that owns it. We can't easily do this,
                            // so just set a flag and always return.
                            // Actually, let's just skip ALL activity results within this process.
                            // Since our module only targets app.unique.one, this is safe.
                            XposedBridge.log("[Bypass] ActivityResult launcher intercepted, skip");
                            param.setResult(null);
                        } catch (Throwable ig) {
                            XposedBridge.log("[Bypass] Launcher intercept error: " + ig);
                        }
                    }
                });
            XposedBridge.log("[Bypass] Hook 5: Lz0.oO0OO00()");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 5 FAIL: " + t);
        }

        // No hook 6 - removed onViewCreated hijack, was too aggressive

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
