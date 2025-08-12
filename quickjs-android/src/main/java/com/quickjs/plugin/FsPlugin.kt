package com.quickjs.plugin

import android.webkit.JavascriptInterface
import com.quickjs.JSContext
import com.quickjs.Plugin
import java.io.File
import java.nio.charset.Charset
import kotlin.toString

class FsPlugin : Plugin() {

    var base = ""

    override fun setup(context: JSContext) {
        base = context.quickJS.home.absolutePath
        val fs = context.global.addJavascriptInterface(this, "fs")
    }

    override fun close(context: JSContext) {

    }

    @JavascriptInterface
    fun writeFileSync(vararg args: Any) {
        if (args.size < 2) throw IllegalArgumentException("writeFileSync requires at least two arguments")
        val path = args[0] as? String ?: throw NullPointerException("path is null")
        val content = args[1] as? String ?: throw NullPointerException("content is null")
        val encoding = if (args.size > 2) args[2].toString() else "UTF-8"
        writeFile(path, content, encoding)
    }

    @JavascriptInterface
    fun readFileSync(vararg args: Any): String {
        if (args.isEmpty()) throw IllegalArgumentException("readFileSync requires at least one argument")
        val path = args[0] as? String ?: throw NullPointerException("path is null")
        val encoding = if (args.size > 1) args[1].toString() else "UTF-8"

        return readFile(path, encoding)
    }


    private fun readFile(path: String, encoding: String = "UTF-8"): String {
        // 实现读取文件的逻辑
        return File(base,path).takeIf { it.exists() }?.readText(Charset.forName(encoding))
            ?: throw IllegalArgumentException("File not found: $path")
    }

    private fun writeFile(path: String, content: String, encoding: String = "UTF-8") {
        // 实现写入文件的逻辑
        val file = File(base,path)
        file.parentFile?.mkdirs() // 确保目录存在
        file.writeText(content, Charset.forName(encoding))
    }
}