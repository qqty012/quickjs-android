package com.quickjs.plugin

import android.util.Log
import android.webkit.JavascriptInterface

import com.quickjs.JSArray
import com.quickjs.JSContext
import com.quickjs.JSObject
import com.quickjs.Plugin


import java.util.HashMap

open class ConsolePlugin : Plugin() {
    private var count = 0
    private val timer = HashMap<String, Long>()

    override fun setup(context: JSContext) {
        val console = context.global.addJavascriptInterface(this, "console")
        console.registerJavaMethod("assert") { receiver, args ->
            if ((args[0] ?: false) == true) {
                error(args[1].toString())
            }
        }

//        console.set("prototype", context.getPrototype(console))
    }

    override fun close(context: JSContext) {

    }

    @JavascriptInterface
    fun log(vararg msg: Any?) {
        count++
        println(Log.DEBUG, *msg)
    }

    @JavascriptInterface
    fun info(vararg msg: Any?) {
        count++
        println(Log.INFO, *msg)
    }

    @JavascriptInterface
    fun error(vararg msg: Any?) {
        count++
        println(Log.ERROR, *msg)
    }

    @JavascriptInterface
    fun dir(vararg msg: Any?) {
        count++
        println(Log.INFO, *msg)
    }

    @JavascriptInterface
    fun warn(vararg msg: Any?) {
        count++
        println(Log.WARN, *msg)
    }

    open fun println(priority: Int, vararg msg: Any?) {
        Log.println(priority, "QuickJS-Console", join(msg))
    }

    @JavascriptInterface
    fun count(): Int {
        return count
    }


    @JavascriptInterface
    fun table(vararg msg: Any?) {
        val obj = msg[0] as? JSObject
        if (obj is JSArray) {
            log(obj.toJSONArray().toString())
        } else if (obj != null) {
            log(obj.toJSONObject().toString())
        }
    }


    @JavascriptInterface
    fun time(vararg msg: Any?) {
        val name = msg[0] as? String ?: ""
        if (timer.containsKey(name)) {
            warn(String.format("Timer '%s' already exists", name))
            return
        }
        timer.put(name, System.currentTimeMillis())
    }

    @JavascriptInterface
    fun timeEnd(vararg msg: Any?) {
        val name = msg[0] as? String ?: ""
        val startTime = timer.get(name)
        if (startTime != null) {
            val ms = (System.currentTimeMillis() - startTime)
            log(String.format("%s: %s ms", name, ms))
        }
        timer.remove(name)
    }

    @JavascriptInterface
    fun trace() {
        log("This 'console.trace' function is not supported");
    }

    @JavascriptInterface
    fun clear() {
        log("This 'console.clear' function is not supported");
    }

    @JavascriptInterface
    fun group(name: String) {
        log("This 'console.group' function is not supported");
    }

    @JavascriptInterface
    fun groupCollapsed(name: String) {
        log("This 'console.groupCollapsed' function is not supported");
    }

    @JavascriptInterface
    fun groupEnd(name: String) {
        log("This 'console.groupEnd' function is not supported");
    }

    private fun join(args: Array<out Any?>): String {
        val result = arrayListOf<String?>()
        for (it in args) {
            result.add(it?.toString())
        }
        return result.joinToString(" ")
    }
}
