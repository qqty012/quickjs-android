package com.quickjs.plugin

import android.os.Build
import android.webkit.JavascriptInterface
import com.quickjs.JSContext
import com.quickjs.Plugin
import java.net.InetAddress

class OsPlugin: Plugin() {

    @JavascriptInterface
    fun hostname(): String {
        return InetAddress.getLocalHost().hostName
    }

    @JavascriptInterface
    fun totalmem(): Double {
        return Runtime.getRuntime().totalMemory().toDouble()
    }

    @JavascriptInterface
    fun freemem(): Double {
        return Runtime.getRuntime().freeMemory().toDouble()
    }

    override fun setup(context: JSContext) {
        
        val os = context.global.addJavascriptInterface(this, "os")
        os.set("platform", Build.BRAND ?: "android")
        // 操作系统平台
        os.set("platform", Build.BRAND ?: "android")

        // 架构
        os.set("arch", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")

        // 系统版本
        os.set("release", Build.VERSION.RELEASE ?: "unknown")

        // 设备型号
        os.set("type", Build.MODEL ?: "unknown")

        // 临时目录
        os.set("tmpdir", "/data/local/tmp")

    }

    override fun close(context: JSContext) {

    }

}