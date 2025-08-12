package com.quickjs

import com.quickjs.classes.Url
import com.quickjs.plugin.*

/**
 * 支持 import、export
 */
abstract class ES6Module(quickJS: QuickJS): Module(quickJS, quickJS.native.createContext(quickJS.runtimePtr)) {

    init {
        this.addPlugin(ConsolePlugin())
        this.addPlugin(TimerPlugin())
        this.addPlugin(BufferPlugin())
        this.addPlugin(HttpPlugin())
        addPlugin(PathPlugin())
        addPlugin(OsPlugin())
        addPlugin(FsPlugin())
        addPlugin(URLPlugin())
    }

    abstract override fun getModuleScript(moduleName: String): String?

    override fun executeModuleScript(source: String, fileName: String): Any? {
        checkReleased()
        return native.executeScript(contextPtr, JSValue.TYPE.NULL.value, source, fileName, QuickJS.JS_EVAL_TYPE_MODULE)
    }

    fun executeModule(moduleName: String) {
        val script = getModuleScript(moduleName)
        if (script == null) {
            throw RuntimeException("'moduleName' script is null")
        }
        executeModuleScript(script, moduleName)
    }
}
