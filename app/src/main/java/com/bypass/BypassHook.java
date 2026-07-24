package com.bypass;

import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BypassHook implements IXposedHookLoadPackage {

    private static final String TARGET = "app.unique.one";
    private static final AtomicReference<Class<?>> sZcc = new AtomicReference<>();
    private static final AtomicReference<Class<?>> sLlf = new AtomicReference<>();
    private static final AtomicReference<Class<?>> sOf8 = new AtomicReference<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET.equals(lpparam.packageName)) return;
        XposedBridge.log("[Bypass] Loading for " + TARGET);

        // Step 1: discover key classes (try reflection, fallback to known names)
        discoverClasses(lpparam.classLoader);

        Class<?> zcc = sZcc.get(), llf = sLlf.get(), of8 = sOf8.get();
        if (zcc == null || llf == null) {
            XposedBridge.log("[Bypass] FATAL: key classes not found");
            return;
        }
        XposedBridge.log("[Bypass] Zcc=" + zcc.getName() + " Llf=" + llf.getName()
            + " Of8=" + (of8 != null ? of8.getName() : "?"));

        // Step 2: install hooks using discovered classes
        installAllHooks(zcc, llf, of8);
    }

    // ==================== CLASS DISCOVERY ====================

    private void discoverClasses(ClassLoader cl) {
        // Strategy: enumerate loaded classes, match by structural fingerprint.
        // If structural discovery fails, fall back to known R8-obfuscated names.

        try {
            // Get the class list via reflection on BaseDexClassLoader
            Class<?> bdc = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = bdc.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(cl);
            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);

            for (Object element : dexElements) {
                Field dexFileField = element.getClass().getDeclaredField("dexFile");
                dexFileField.setAccessible(true);
                Object dexFile = dexFileField.get(element);
                Method entriesMethod = dexFile.getClass().getDeclaredMethod("entries");
                Object entries = entriesMethod.invoke(dexFile);
                // entries() returns an Enumeration<String> of class names
                Method hasMore = entries.getClass().getMethod("hasMoreElements");
                Method next = entries.getClass().getMethod("nextElement");
                while ((Boolean) hasMore.invoke(entries)) {
                    String name = (String) next.invoke(entries);
                    try {
                        Class<?> c = cl.loadClass(name);
                        examineClass(c);
                    } catch (Throwable ig) {}
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Enumeration failed, using fallback: " + t.getMessage());
        }

        // Fallback to known names if discovery missed anything
        try { if (sZcc.get() == null) sZcc.set(cl.loadClass("zcc")); } catch (Throwable ig) {}
        try { if (sLlf.get() == null) sLlf.set(cl.loadClass("lf")); } catch (Throwable ig) {}
        try { if (sOf8.get() == null) sOf8.set(cl.loadClass("of8")); } catch (Throwable ig) {}
    }

    private void examineClass(Class<?> c) {
        if (sLlf.get() != null && sZcc.get() != null && sOf8.get() != null) return;

        // Llf fingerprint: 7+ fields (3+ int, 2+ boolean, 2+ long), constructor sets int field
        int ints = 0, bools = 0, longs = 0;
        for (Field f : c.getDeclaredFields()) {
            Class<?> t = f.getType();
            if (t == int.class) ints++;
            else if (t == boolean.class) bools++;
            else if (t == long.class) longs++;
        }
        if (ints >= 3 && bools >= 2 && longs >= 2 && sLlf.get() == null
            && !c.getName().startsWith("android")) {
            sLlf.set(c);
        }

        // Zcc fingerprint: has static Llf field + has getSharedPreferences method
        if (sLlf.get() != null && sZcc.get() == null) {
            boolean hasLlfField = false, hasGetSP = false;
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == sLlf.get()) {
                    hasLlfField = true;
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == SharedPreferences.class) {
                    hasGetSP = true;
                }
            }
            if (hasLlfField && hasGetSP) sZcc.set(c);
        }

        // Of8 fingerprint: extends Fragment-like class, has boolean field named *OOo
        if (sOf8.get() == null) {
            if (c.getSuperclass() != null) {
                String superName = c.getSuperclass().getName();
                if (superName.contains("ragment") || superName.contains("oOO0OO")) {
                    for (Field f : c.getDeclaredFields()) {
                        if (f.getType() == boolean.class && f.getName().matches(".*OOo.*")) {
                            sOf8.set(c);
                            break;
                        }
                    }
                }
            }
        }
    }

    // ==================== HOOK INSTALLATION ====================

    private void installAllHooks(Class<?> zcc, Class<?> llf, Class<?> of8) {
        Method mGetSub = findMethod(zcc, llf, 0);           // oO0OO0O()
        Method mDaily  = findMethod(zcc, boolean.class, 0); // oO0OOOO()
        Field  fCache  = findStaticField(zcc, llf);

        if (mGetSub == null || mDaily == null) {
            XposedBridge.log("[Bypass] Cannot find Zcc methods");
            return;
        }
        XposedBridge.log("[Bypass] getSub=" + mGetSub.getName() + " daily=" + mDaily.getName());

        // ----- Hook 1: getSubscription → fake -----
        XposedBridge.hookMethod(mGetSub, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                Object obj = buildFake(llf);
                if (fCache != null) fCache.set(null, obj);
                return obj;
            }
        });
        XposedBridge.log("[Bypass] Hook 1: getSubscription");

        // ----- Hook 2: dailyCheck → true -----
        XposedBridge.hookMethod(mDaily, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return Boolean.TRUE;
            }
        });
        XposedBridge.log("[Bypass] Hook 2: dailyCheck");

        // ----- Hook 3: SP.getString("as") → fake JSON -----
        try {
            XposedHelpers.findAndHookMethod(SharedPreferences.class, "getString",
                String.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if ("as".equals(param.args[0])) {
                            param.setResult("{\"st\":1,\"pu\":true,\"ex\":" + Long.MAX_VALUE
                                + ",\"al\":true,\"ca\":0,\"v\":1,\"te\":0}");
                        }
                    }
                });
            XposedBridge.log("[Bypass] Hook 3: SP getString");
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] Hook 3 FAIL: " + t);
        }

        // ----- Hook 4: O00OO replacement (if of8 found) -----
        if (of8 != null) {
            Method mCheck = findMethod(of8, void.class, 0);
            Method mTrans = findMethodByName(of8, "O00OO0");
            Field fFlag = findBoolField(of8);

            if (mCheck != null) {
                XposedBridge.hookMethod(mCheck, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Object fake = buildFake(llf);
                        if (fCache != null) fCache.set(null, fake);
                        if (fFlag != null) fFlag.setBoolean(param.thisObject, true);
                        if (mTrans != null) mTrans.invoke(param.thisObject);
                        return null;
                    }
                });
                XposedBridge.log("[Bypass] Hook 4: O00OO replacement");
            }

            // ----- Hook 5: O00OO0 blocker -----
            if (mTrans != null) {
                XposedBridge.hookMethod(mTrans, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        try {
                            if (fFlag != null) fFlag.setBoolean(param.thisObject, true);
                        } catch (Throwable ig) {}
                        return null;
                    }
                });
                XposedBridge.log("[Bypass] Hook 5: O00OO0 blocker");
            }
        }

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }

    // ==================== REFLECTION HELPERS ====================

    private Object buildFake(Class<?> llf) throws Exception {
        Constructor<?> ctor = llf.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object obj = ctor.newInstance();
        for (Field f : llf.getDeclaredFields()) {
            f.setAccessible(true);
            Class<?> t = f.getType();
            if (t == int.class) f.setInt(obj, 1);
            else if (t == boolean.class) f.setBoolean(obj, true);
            else if (t == long.class) f.setLong(obj, Long.MAX_VALUE);
        }
        return obj;
    }

    private static Method findMethod(Class<?> c, Class<?> returnType, int paramCount) {
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                && m.getReturnType() == returnType
                && m.getParameterCount() == paramCount) {
                return m;
            }
        }
        return null;
    }

    private static Method findMethodByName(Class<?> c, String name) {
        try { return c.getDeclaredMethod(name); } catch (Throwable t) { return null; }
    }

    private static Field findStaticField(Class<?> c, Class<?> type) {
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == type)
                return f;
        }
        return null;
    }

    private static Field findBoolField(Class<?> c) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getType() == boolean.class && f.getName().matches(".*OOo.*"))
                return f;
        }
        return null;
    }
}
