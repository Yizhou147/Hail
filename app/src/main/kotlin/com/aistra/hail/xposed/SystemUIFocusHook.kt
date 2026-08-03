package com.aistra.hail.xposed

import android.content.Context
import android.util.Log
import com.aistra.hail.xposed.XposedInterface.BaseHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers

/**
 * HyperOS 超级岛白名单解锁。
 *
 * HyperOS 对"焦点通知/超级岛"有应用白名单限制，普通应用即使按官方规范在
 * 通知中注入 miui.focus.param 也不会被放行。本 Hook 在 SystemUI 进程中
 * 拦截 [NotificationSettingsManager.canShowFocus]/[canCustomFocus] 并强制
 * 返回 true，使雹的专注通知能以超级岛形态展示。
 *
 * 实现参考 HyperIsland（github.com/Yizhou147/HyperIsland）的
 * UnlockAllFocusHook，改用雹使用的传统 Xposed API。
 *
 * 前提：LSPosed 中启用雹，并将作用域勾选 "系统界面 (com.android.systemui)"。
 * 所有 hook 均被 try-catch 包裹，目标方法不存在时静默跳过，不影响 SystemUI。
 */
class SystemUIFocusHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        Log.i(TAG, "startHook: attempting to load $TARGET_CLASS")
        val clazz = runCatching { classLoader.loadClass(TARGET_CLASS) }.getOrNull()
        if (clazz == null) {
            Log.w(TAG, "target class not found: $TARGET_CLASS")
            scanCandidates()
            return
        }
        Log.i(TAG, "target class found: $TARGET_CLASS")
        hookReturnTrue(clazz, "canShowFocus", Context::class.java, String::class.java)
        hookReturnTrue(clazz, "canCustomFocus", String::class.java)
    }

    /**
     * 枚举 SystemUI classLoader 中所有 dex 的类名，找出与焦点通知/超级岛
     * 白名单相关的候选类（不同 HyperOS 版本类名可能不同），供后续适配。
     */
    private fun scanCandidates() {
        Thread {
            try {
                val pathListField =
                    Class.forName("dalvik.system.BaseDexClassLoader").getDeclaredField("pathList")
                pathListField.isAccessible = true
                val pathList = pathListField.get(classLoader)
                val elementsField = pathList.javaClass.getDeclaredField("dexElements")
                elementsField.isAccessible = true
                val elements = elementsField.get(pathList) as Array<*>
                var scanned = 0
                for (el in elements) {
                    val dexFileField = el.javaClass.getDeclaredField("dexFile")
                    dexFileField.isAccessible = true
                    val dexFile = dexFileField.get(el) ?: continue
                    val names = dexFile.javaClass.getMethod("getClassNameList").invoke(dexFile) as Array<*>
                    for (n in names) {
                        scanned++
                        val name = n.toString()
                        if (name.startsWith("miui.systemui.") &&
                            (name.contains("NotificationSettings") || name.contains("Focus")
                                || name.contains("Island"))
                        ) {
                            Log.i(TAG, "candidate: $name")
                        }
                    }
                }
                Log.i(TAG, "scan done, $scanned classes scanned")
            } catch (e: Throwable) {
                Log.e(TAG, "scan failed: $e")
            }
        }.start()
    }

    private fun hookReturnTrue(clazz: Class<*>, methodName: String, vararg params: Class<*>) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                clazz, methodName, *params,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any = true
                }
            )
            Log.i(TAG, "hooked $methodName")
        }.onFailure { Log.e(TAG, "hook $methodName failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "HailXposed"
        const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    }
}
