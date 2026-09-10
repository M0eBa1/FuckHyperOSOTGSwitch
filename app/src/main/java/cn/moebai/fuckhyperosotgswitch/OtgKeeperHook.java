package cn.moebai.fuckhyperosotgswitch;

import android.content.ContentResolver;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class OtgKeeperHook extends XposedModule {

    private static final String TAG = "FuckHyperOSOTGSwitch";

    public OtgKeeperHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
    }

    private void logMsg(String msg) {
        log(Log.INFO, TAG, msg);
    }

    private void logErr(String msg, Throwable t) {
        log(Log.ERROR, TAG, msg, t);
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();

        if ("com.android.settings".equals(packageName)) {
            hookSettings(param);
        } else if ("android".equals(packageName)) {
            hookSystemServer(param);
        } else if ("com.miui.powerkeeper".equals(packageName)) {
            hookPowerKeeper(param);
        }
    }

    private void hookSettings(PackageLoadedParam param) {
        ClassLoader cl = param.getClassLoader();
        String[] possibleClasses = new String[]{
                "com.android.settings.connecteddevice.OtgPreferenceController",
                "com.android.settings.hardware.OtgPreferenceController",
                "com.android.settings.OtgModePreferenceController",
                "com.android.settings.connecteddevice.OtgController"
        };

        for (String className : possibleClasses) {
            try {
                Class<?> clazz = cl.loadClass(className);
                hookOtgController(clazz);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                logErr("Error finding class: " + className, t);
            }
        }

        // 拦截广播发送（阻止超时关闭广播）
        try {
            Class<?> contextWrapperClass = cl.loadClass("android.content.ContextWrapper");
            Method sendBroadcastMethod = contextWrapperClass.getMethod("sendBroadcast", Intent.class);
            hook(sendBroadcastMethod).intercept(chain -> {
                Object[] args = new Object[]{};
                try {
                    Method getArgsMethod = chain.getClass().getMethod("getArgs");
                    args = (Object[]) getArgsMethod.invoke(chain);
                } catch (Throwable ignored) {
                }
                if (args != null && args.length > 0 && args[0] instanceof Intent) {
                    Intent intent = (Intent) args[0];
                    if (intent.getAction() != null) {
                        String action = intent.getAction().toLowerCase();
                        if (action.contains("otg") && (action.contains("timeout") || action.contains("stop") || action.contains("disable"))) {
                            logMsg("Blocked settings broadcast: " + intent.getAction());
                            return null;
                        }
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            logErr("Hook sendBroadcast failed", t);
        }
    }

    private void hookOtgController(Class<?> clazz) {
        String[] stopMethods = new String[]{"stopOtg", "closeOtg", "disableOtg", "onOtgTimeout"};
        for (Method method : clazz.getDeclaredMethods()) {
            for (String stopName : stopMethods) {
                if (method.getName().equals(stopName)) {
                    try {
                        hook(method).intercept(chain -> {
                            logMsg("Blocked controller method: " + clazz.getName() + "#" + method.getName());
                            return null;
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private void hookPowerKeeper(PackageLoadedParam param) {
        try {
            Class<?> contextWrapperClass = param.getClassLoader().loadClass("android.content.ContextWrapper");
            Method sendBroadcastMethod = contextWrapperClass.getMethod("sendBroadcast", Intent.class);
            hook(sendBroadcastMethod).intercept(chain -> {
                Object[] args = new Object[]{};
                try {
                    Method getArgsMethod = chain.getClass().getMethod("getArgs");
                    args = (Object[]) getArgsMethod.invoke(chain);
                } catch (Throwable ignored) {
                }
                if (args != null && args.length > 0 && args[0] instanceof Intent) {
                    Intent intent = (Intent) args[0];
                    if (intent.getAction() != null) {
                        String action = intent.getAction().toLowerCase();
                        if (action.contains("otg") && (action.contains("timeout") || action.contains("stop") || action.contains("disable"))) {
                            logMsg("Blocked powerkeeper broadcast: " + intent.getAction());
                            return null;
                        }
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable ignored) {
        }
    }

    private void hookSystemServer(PackageLoadedParam param) {
        // 拦截 Settings.Global.putInt 将 OTG 写入 0 (关闭)
        try {
            Method putIntGlobal = Settings.Global.class.getMethod("putInt", ContentResolver.class, String.class, int.class);
            hook(putIntGlobal).intercept(chain -> {
                try {
                    Method getArgsMethod = chain.getClass().getMethod("getArgs");
                    Object[] args = (Object[]) getArgsMethod.invoke(chain);
                    if (args != null && args.length >= 3) {
                        String name = (String) args[1];
                        int value = (int) args[2];
                        if (isOtgKey(name) && value == 0) {
                            logMsg("Prevented Global " + name + " from setting to 0, forcing to 1");
                            args[2] = 1;
                            return chain.proceed(args);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            logErr("Hook Settings.Global.putInt failed", t);
        }

        // 拦截 Settings.System.putInt 将 OTG 写入 0 (关闭)
        try {
            Method putIntSystem = Settings.System.class.getMethod("putInt", ContentResolver.class, String.class, int.class);
            hook(putIntSystem).intercept(chain -> {
                try {
                    Method getArgsMethod = chain.getClass().getMethod("getArgs");
                    Object[] args = (Object[]) getArgsMethod.invoke(chain);
                    if (args != null && args.length >= 3) {
                        String name = (String) args[1];
                        int value = (int) args[2];
                        if (isOtgKey(name) && value == 0) {
                            logMsg("Prevented System " + name + " from setting to 0, forcing to 1");
                            args[2] = 1;
                            return chain.proceed(args);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            logErr("Hook Settings.System.putInt failed", t);
        }
    }

    private static boolean isOtgKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.equals("otg_mode")
                || lower.equals("miui_otg")
                || lower.equals("persist.sys.otg_mode")
                || lower.contains("otg_switch");
    }
}
