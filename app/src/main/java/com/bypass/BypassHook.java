package com.bypass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import android.content.SharedPreferences;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 通用订阅绕过模块（通杀版）
 *
 * <h3>设计目标</h3>
 * 不依赖任何硬编码的混淆类名 / 方法名 / 字段名，仅依靠<b>不随混淆变化的功能性特征</b>
 * 在运行时动态定位目标类与方法，从而在软件版本更新或重新混淆后仍能正常工作。
 *
 * <h3>核心原理</h3>
 * 目标 App 的订阅校验入口（原 of8.O00OO）会读取 SharedPreferences：
 * <ul>
 *   <li>"oas" == 1 → 已订阅 → 检查版本("osv") → 版本不满足 → 直接显示内容（路径1，最佳路径）</li>
 *   <li>"oas" == 0 → 未订阅 → 走 ActivityResult/AIDL 向 App A 请求验证（需 App A 存在）</li>
 * </ul>
 * 本模块让 "oas"=1 且 "osv"=0，使校验走"路径1"直接显示内容，完全绕过对 App A 的依赖。
 *
 * <h3>定位策略（零硬编码类名）</h3>
 * <ol>
 *   <li><b>切入点</b>：Hook {@code ContextImpl.getSharedPreferences(String, int)}，
 *       过滤 name == "app_setting"（订阅 SP 文件名，功能性字符串，不会被混淆器修改）</li>
 *   <li><b>定位订阅管理类 (zcc)</b>：getSharedPreferences("app_setting") 的调用者即订阅管理类，
 *       通过调用栈获取第一个非系统类</li>
 *   <li><b>定位订阅数据类 (lf)</b>：扫描订阅管理类的静态方法，找到返回类型满足特征 ——
 *       7 个字段(3×int + 2×boolean + 2×long)，每个字段注解 value() 返回
 *       "st"/"pu"/"ex"/"al"/"ca"/"v"/"te" 之一</li>
 *   <li><b>定位方法</b>：通过方法签名（参数类型 + 返回类型）定位，不依赖方法名</li>
 *   <li><b>设置字段</b>：通过注解 value()（JSON key）确定字段含义，不依赖字段名</li>
 * </ol>
 *
 * <h3>稳定特征清单</h3>
 * <ul>
 *   <li>SP 文件名 "app_setting" — 定位切入点</li>
 *   <li>SP keys "oas" / "osv" — 控制订阅状态</li>
 *   <li>lf 字段注解 value "st"/"pu"/"ex"/"al"/"ca"/"v"/"te" — 定位数据类与字段</li>
 *   <li>lf 字段类型组合 3×int + 2×boolean + 2×long — 验证数据类</li>
 *   <li>zcc 方法签名 (String,int)→int、()→lf — 定位读 SP / 获取数据方法</li>
 * </ul>
 */
public class BypassHook implements IXposedHookLoadPackage {

    // ===== 稳定常量（功能性字符串，不随混淆变化） =====
    private static final String TARGET_PKG = "app.unique.one";
    private static final String SP_NAME = "app_setting";
    private static final String KEY_OAS = "oas";        // 订阅状态 SP key
    private static final String KEY_OSV = "osv";        // 版本号 SP key
    private static final String[] LF_JSON_KEYS = {
        "st", "pu", "ex", "al", "ca", "v", "te"
    };

    // ===== 运行时定位的类与对象（不硬编码） =====
    private volatile Class<?> zccClass;   // 订阅管理类
    private volatile Class<?> llfClass;   // 订阅数据类
    private volatile Object fakeLlf;      // 假订阅数据实例
    private volatile boolean installed;   // Hook 是否已安装

    private final Object lock = new Object();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log("[Bypass] === Universal bypass module loaded ===");
        XposedBridge.log("[Bypass] Target package: " + TARGET_PKG);

        final ClassLoader cl = lpparam.classLoader;

        // 切入点：Hook ContextImpl.getSharedPreferences
        // "app_setting" 是订阅 SP 的文件名，是功能性字符串，不会被混淆器修改。
        // 当 App 首次读取订阅 SP 时，本 Hook 被触发，写入订阅状态并定位目标类。
        hookSharedPreferencesEntry(cl);
    }

    /**
     * Hook ContextImpl.getSharedPreferences 作为定位切入点。
     * 当 name == "app_setting" 时：
     * 1. 写入 "oas"=1, "osv"=0（让校验走路径1，直接显示内容）
     * 2. 从调用栈定位订阅管理类
     * 3. 反射分析并 Hook 订阅管理类的方法
     */
    private void hookSharedPreferencesEntry(final ClassLoader cl) {
        final String[] entryClassNames = {
            "android.app.ContextImpl",
            "android.content.ContextWrapper"
        };

        boolean hooked = false;
        for (String clsName : entryClassNames) {
            try {
                Class<?> cls = cl.loadClass(clsName);
                XposedHelpers.findAndHookMethod(cls, "getSharedPreferences",
                    String.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            handleSpAccess(cl, param);
                        }
                    });
                XposedBridge.log("[Bypass] Entry hooked: " + clsName + ".getSharedPreferences");
                hooked = true;
            } catch (Throwable t) {
                XposedBridge.log("[Bypass] Cannot hook " + clsName + ": " + t);
            }
        }

        if (!hooked) {
            XposedBridge.log("[Bypass] FATAL: All entry hooks failed");
        }
    }

    private void handleSpAccess(ClassLoader cl, MethodHookParam param) {
        try {
            String name = (String) param.args[0];
            if (!SP_NAME.equals(name)) return;

            // 写入订阅状态，让 of8.O00OO() 走路径1
            // "oas"=1 → 已订阅；"osv"=0 → oO0OOOo() 返回 false → 直接 O00OO0() 显示内容
            SharedPreferences sp = (SharedPreferences) param.getResult();
            if (sp != null) {
                sp.edit()
                    .putInt(KEY_OAS, 1)
                    .putInt(KEY_OSV, 0)
                    .commit();
                XposedBridge.log("[Bypass] SP '" + SP_NAME + "' patched: oas=1, osv=0");
            }

            if (!installed) {
                synchronized (lock) {
                    if (!installed) {
                        installHooks(cl);
                        installed = true;
                    }
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] SP access handler error: " + t);
        }
    }

    /**
     * 安装所有 Hook：定位类 → 创建假数据 → Hook 方法
     */
    private void installHooks(ClassLoader cl) {
        XposedBridge.log("[Bypass] --- Installing hooks ---");

        // 1. 从调用栈定位订阅管理类（getSharedPreferences 的直接调用者）
        zccClass = locateClassFromStack(cl);
        if (zccClass == null) {
            XposedBridge.log("[Bypass] FATAL: Cannot locate subscription manager from stack");
            return;
        }
        XposedBridge.log("[Bypass] Located subscription manager: " + zccClass.getName());

        // 2. 通过订阅管理类的静态方法返回类型定位订阅数据类
        llfClass = locateSubscriptionDataClass();
        if (llfClass == null) {
            XposedBridge.log("[Bypass] WARNING: Cannot locate subscription data class");
        } else {
            XposedBridge.log("[Bypass] Located subscription data class: " + llfClass.getName());

            // 3. 创建假订阅数据（通过注解 value 定位字段，完全通杀）
            fakeLlf = createFakeSubscriptionData();
            if (fakeLlf != null) {
                XposedBridge.log("[Bypass] Fake subscription data created");
            }
        }

        // 4. Hook 订阅管理类的方法（兜底，防止 SP 被覆盖或数据被重置）
        int count = hookSubscriptionManagerMethods();
        XposedBridge.log("[Bypass] Hooked " + count + " methods");

        XposedBridge.log("[Bypass] === All hooks installed ===");
    }

    /**
     * 从调用栈定位第一个非系统类（即订阅管理类）。
     * getSharedPreferences("app_setting") 的直接调用者就是订阅管理类。
     */
    private Class<?> locateClassFromStack(ClassLoader cl) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String cls = frame.getClassName();
            if (isSystemOrXposedClass(cls)) continue;
            try {
                return cl.loadClass(cls);
            } catch (Throwable t) {
                // 该类无法加载，继续检查下一个栈帧
            }
        }
        return null;
    }

    private boolean isSystemOrXposedClass(String cls) {
        return cls.startsWith("java.")
            || cls.startsWith("android.")
            || cls.startsWith("androidx.")
            || cls.startsWith("com.android.")
            || cls.startsWith("dalvik.")
            || cls.startsWith("sun.")
            || cls.startsWith("de.robv.")
            || cls.startsWith("com.bypass.")
            || cls.startsWith("kotlin.")
            || cls.startsWith("kotlinx.")
            || cls.startsWith("org.jetbrains.");
    }

    /**
     * 通过订阅管理类的静态方法返回类型定位订阅数据类。
     * 扫描所有无参静态方法，找到返回类型满足订阅数据类特征的类。
     */
    private Class<?> locateSubscriptionDataClass() {
        if (zccClass == null) return null;
        for (Method m : zccClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;
            if (m.getParameterCount() != 0) continue;
            Class<?> ret = m.getReturnType();
            if (ret == null || ret.isPrimitive() || ret.isArray()) continue;
            if (ret == String.class || ret == Object.class) continue;
            if (isSubscriptionDataClass(ret)) {
                return ret;
            }
        }
        return null;
    }

    /**
     * 验证类是否是订阅数据类。
     * 特征：
     * - 恰好 7 个声明的实例字段
     * - 字段类型组合：3×int + 2×boolean + 2×long
     * - 每个字段有注解，注解 value() 返回 JSON key 之一
     */
    private boolean isSubscriptionDataClass(Class<?> c) {
        Field[] fields = c.getDeclaredFields();
        if (fields.length != 7) return false;

        int intCount = 0, boolCount = 0, longCount = 0;
        Set<String> annoValues = new HashSet<>();

        for (Field f : fields) {
            Class<?> t = f.getType();
            if (t == int.class) {
                intCount++;
            } else if (t == boolean.class) {
                boolCount++;
            } else if (t == long.class) {
                longCount++;
            } else {
                return false; // 出现非 int/boolean/long 字段，不匹配
            }

            String val = getAnnotationValue(f);
            if (val != null) {
                annoValues.add(val);
            }
        }

        if (intCount != 3 || boolCount != 2 || longCount != 2) return false;

        // 验证注解 value 覆盖所有 JSON key
        for (String key : LF_JSON_KEYS) {
            if (!annoValues.contains(key)) return false;
        }
        return true;
    }

    /**
     * 读取字段的注解 value。
     * 注解类名会被混淆（如 Lgrc;），但 Java 注解的 value() 方法名是标准的，不会变。
     */
    private String getAnnotationValue(Field f) {
        for (Annotation a : f.getDeclaredAnnotations()) {
            try {
                Method vm = a.annotationType().getDeclaredMethod("value");
                Object v = vm.invoke(a);
                if (v instanceof String) {
                    return (String) v;
                }
            } catch (Throwable t) {
                // 该注解没有 value() 方法，跳过
            }
        }
        return null;
    }

    /**
     * 创建假订阅数据实例。
     * 通过注解 value（JSON key）确定每个字段的含义并设置有效值，不依赖字段名。
     */
    private Object createFakeSubscriptionData() {
        if (llfClass == null) return null;
        try {
            Object obj = llfClass.getDeclaredConstructor().newInstance();
            for (Field f : llfClass.getDeclaredFields()) {
                f.setAccessible(true);
                String val = getAnnotationValue(f);
                if (val == null) continue;
                switch (val) {
                    case "st": // status = 1 (有效)
                        f.setInt(obj, 1);
                        break;
                    case "al": // isActive = true
                        f.setBoolean(obj, true);
                        break;
                    case "pu": // isPremium = true
                        f.setBoolean(obj, true);
                        break;
                    case "ex": // expireTime = 永不过期
                        f.setLong(obj, Long.MAX_VALUE);
                        break;
                    case "ca": // createTime = 当前时间
                        f.setLong(obj, System.currentTimeMillis());
                        break;
                    case "v":  // version = 1
                        f.setInt(obj, 1);
                        break;
                    case "te": // type = 0 (标准)
                        f.setInt(obj, 0);
                        break;
                    default:
                        break;
                }
            }
            return obj;
        } catch (Throwable t) {
            XposedBridge.log("[Bypass] createFakeSubscriptionData error: " + t);
            return null;
        }
    }

    /**
     * Hook 订阅管理类的方法（兜底）。
     *
     * Hook 的方法（通过签名定位，不依赖方法名）：
     * - (String, int) → int：读 SP int 值，当 key=="oas" 返回 1，key=="osv" 返回 0
     * - () → lf：获取订阅数据，返回完整假数据
     *
     * 这些 Hook 是兜底措施：即使 App 其他代码覆盖了 SP 或重置了订阅数据缓存，
     * 也能确保校验逻辑读到正确的值。
     */
    private int hookSubscriptionManagerMethods() {
        if (zccClass == null) return 0;
        int count = 0;

        for (Method m : zccClass.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) continue;

            Class<?>[] params = m.getParameterTypes();
            Class<?> ret = m.getReturnType();

            // Hook (String, int) → int：读 SP int（兜底，防止 SP 被覆盖）
            if (params.length == 2
                && params[0] == String.class
                && params[1] == int.class
                && ret == int.class) {

                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        String key = (String) p.args[0];
                        if (KEY_OAS.equals(key)) {
                            p.setResult(1);
                        } else if (KEY_OSV.equals(key)) {
                            p.setResult(0);
                        }
                    }
                });
                XposedBridge.log("[Bypass] Hooked (String,int)->int: " + m.getName());
                count++;
            }

            // Hook () → lf：获取订阅数据（兜底，返回完整假数据）
            if (params.length == 0
                && llfClass != null
                && ret == llfClass
                && fakeLlf != null) {

                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam p) {
                        // 同步更新静态缓存字段，防止其他代码读到空值
                        try {
                            Field cache = findStaticFieldByType(zccClass, llfClass);
                            if (cache != null) {
                                cache.setAccessible(true);
                                cache.set(null, fakeLlf);
                            }
                        } catch (Throwable t) {
                            // 缓存更新失败不影响返回假数据
                        }
                        return fakeLlf;
                    }
                });
                XposedBridge.log("[Bypass] Hooked ()->lf: " + m.getName());
                count++;
            }
        }
        return count;
    }

    /**
     * 查找指定类型的静态字段。
     * 用于定位订阅管理类中缓存订阅数据的静态字段（字段名被混淆，通过类型定位）。
     */
    private Field findStaticFieldByType(Class<?> cls, Class<?> type) {
        for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                return f;
            }
        }
        return null;
    }
}
