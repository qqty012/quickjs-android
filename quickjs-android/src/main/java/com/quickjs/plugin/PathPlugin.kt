package com.quickjs.plugin

import android.webkit.JavascriptInterface
import com.quickjs.JSContext
import com.quickjs.Plugin
import java.io.File

class PathPlugin: Plugin() {

    @JavascriptInterface
    fun join(vararg paths: String): String {
        return paths.joinToString(File.separator)
    }

    @JavascriptInterface
    fun dirname(path: String): String {
        return File(path).parent ?: ""
    }

    @JavascriptInterface
    fun basename(path: String): String {
        return File(path).name
    }

    @JavascriptInterface
    fun extname(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else ""
    }

    @JavascriptInterface
    fun isAbsolute(path: String): Boolean {
        return File(path).isAbsolute
    }

    @JavascriptInterface
    fun resolve(path: String): String {
        return File(path).absolutePath
    }

    @JavascriptInterface
    fun normalize(path: String): String {
        return File(path).canonicalPath
    }

    override fun setup(context: JSContext) {
        val path = context.global.addJavascriptInterface(this, "path")
        path.set("sep", File.separator)
        path.set("delimiter", File.pathSeparator)
    }

    override fun close(context: JSContext) {

    }
}