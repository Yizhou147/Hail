package com.aistra.hail.xposed

import com.aistra.hail.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedInterface : IXposedHookLoadPackage {
    @Throws(Throwable::class)
    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        if (!loadPackageParam.isFirstApplication) {
            return
        }

        if (loadPackageParam.packageName == BuildConfig.APPLICATION_ID) {
            return
        }

        // SystemUI：解锁 HyperOS 焦点通知白名单，让专注通知显示为超级岛
        if (loadPackageParam.packageName == "com.android.systemui") {
            SystemUIFocusHook(loadPackageParam.classLoader).startHook()
            return
        }

        LaunchAppHook(loadPackageParam.classLoader).startHook()
    }

    abstract class BaseHook(protected val classLoader: ClassLoader) {
        abstract fun startHook()
    }
}
