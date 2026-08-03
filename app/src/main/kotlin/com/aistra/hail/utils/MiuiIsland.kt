package com.aistra.hail.utils

import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import com.aistra.hail.R
import org.json.JSONObject

/**
 * 小米 HyperOS 超级岛 / 焦点通知接入（官方开发指南：dev.mi.com 小米超级岛）。
 *
 * 通知以普通方式构建，在 extras 中写入 "miui.focus.param"（岛通知 JSON），
 * 支持岛通知的设备上会以超级岛形态展示，否则退化为普通通知。
 *
 * 注意：HyperOS 对焦点通知有白名单限制，应用需在系统侧获得"实时活动/焦点通知"
 * 权限（hasFocusPermission() 可检测），否则即使注入参数也只显示普通通知。
 */
object MiuiIsland {
    private const val KEY_PARAM = "miui.focus.param"
    private const val KEY_PICS = "miui.focus.pics"
    private const val PIC_SMALL = "miui.focus.pic_small"

    /**
     * 构建岛通知扩展参数。
     *
     * @param frontTitle 前置文案（如"专注模式中"）
     * @param remainingText 倒计时正文（如"25:00"）
     * @param contentText 通知内容文案（如"剩余 25:00"）
     */
    fun buildIslandExtras(
        context: Context, frontTitle: String, remainingText: String, contentText: String
    ): Bundle {
        val param = JSONObject().put(
            "param_v2", JSONObject()
                .put("protocol", 1)
                .put("business", "hail_focus")
                // 首次出现即展示展开态，之后缩为胶囊；更新时不重复展开
                .put("enableFloat", false)
                .put("updatable", true)
                .put("islandFirstFloat", true)
                // 状态栏 / 息屏展示文案
                .put("ticker", contentText)
                .put("aodTitle", contentText)
                .put(
                    "param_island", JSONObject()
                        .put("islandProperty", 1)
                        .put(
                            "bigIslandArea", JSONObject()
                                .put(
                                    "textInfo", JSONObject()
                                        .put("frontTitle", frontTitle)
                                        .put("title", remainingText)
                                        .put("content", context.getString(R.string.focus_island_content))
                                        .put("showHighlightColor", true)
                                )
                        )
                        .put(
                            "smallIslandArea", JSONObject()
                                .put(
                                    "picInfo", JSONObject()
                                        .put("type", 1)
                                        .put("pic", PIC_SMALL)
                                )
                        )
                )
                .put(
                    "baseInfo", JSONObject()
                        .put("type", 2)
                        .put("title", frontTitle)
                        .put("content", contentText)
                        .put("colorTitle", "#FF6B00")
                )
                .put(
                    "hintInfo", JSONObject()
                        .put("type", 1)
                        .put("title", contentText)
                )
        )
        val bundle = Bundle()
        bundle.putString(KEY_PARAM, param.toString())
        val pics = Bundle()
        pics.putParcelable(
            PIC_SMALL, Icon.createWithResource(context, R.mipmap.ic_launcher)
        )
        bundle.putBundle(KEY_PICS, pics)
        return bundle
    }

    /**
     * 查询当前应用是否开启了焦点通知/超级岛权限（官方查询接口，耗时操作）。
     * 非 HyperOS 或未授权时返回 false。
     */
    fun hasFocusPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = Bundle().apply { putString("package", context.packageName) }
            context.contentResolver.call(uri, "canShowFocus", null, extras)
                ?.getBoolean("canShowFocus", false) ?: false
        } catch (e: Exception) {
            HLog.e(e)
            false
        }
    }
}
