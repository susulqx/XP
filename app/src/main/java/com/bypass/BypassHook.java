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

            // ===== Hook 4: createIntent — 无 App A 时返回有效 Intent 避免抛异常 =====
            try {
                Class<?> contract = lpparam.classLoader.loadClass("of8$oO0OO0Oo");
                XposedHelpers.findAndHookMethod(contract, "oO0OO00",
                    android.content.Context.class, Object.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            XposedBridge.log("[Bypass] createIntent -> fake launcher");
                            // Return an Intent that resolves to App B's own Launcher
                            // This prevents the of8$oO0OO0O exception, allowing
                            // ActivityResult to complete and Hook 3 to fire
                            return new android.content.Intent()
                                .setClassName("app.unique.one", "app.unique.one.Launcher")
                                .addCategory(android.content.Intent.CATEGORY_DEFAULT);
                        }
                    });
                XposedBridge.log("[Bypass] Hook 4: createIntent");

        } catch (Throwable t) {
            XposedBridge.log("[Bypass] FAIL: " + t);
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }
}
