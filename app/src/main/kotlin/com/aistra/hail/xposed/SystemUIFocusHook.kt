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
            return
        }
        Log.i(TAG, "target class found: $TARGET_CLASS")
        hookReturnTrue(clazz, "canShowFocus", Context::class.java, String::class.java)
        hookReturnTrue(clazz, "canCustomFocus", String::class.java)
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
