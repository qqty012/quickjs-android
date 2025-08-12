package com.quickjs

import java.util.HashMap

/**
 * 支持 require、exports
 */
abstract class CommonJSModule: Module {
    companion object {
        const val MODULE_SCRIPT_WRAPPER = "(function () {var module = { exports: {}, children: [] }; #CODE ; return module;})();"
        val modules = HashMap<String, JSObject>()
    }

    constructor(quickJS: QuickJS) : super(quickJS, quickJS.native.createContext(quickJS.runtimePtr)) {
        registerJavaMethod("require") { receiver, args ->
            var moduleBaseName: String? = null
            if (receiver?.isUndefined() == true) {
                val parentModule = receiver.getObject ("module")
                if (!parentModule.isUndefined()) {
                    if (parentModule.contains("filename")) {
                        moduleBaseName = parentModule.getString("filename")
                    }
                }
            }
            val path = args[0] as String
            val moduleName = convertModuleName(moduleBaseName, path)
            var module = modules[path]
            if (module == null) {
                module = executeModule(moduleName ?: "")
                if (module == null) {
                    throw RuntimeException("'moduleName' script is null")
                }
                modules[path] = module
            }
            return@registerJavaMethod module.get(JSValue.TYPE.UNKNOWN, "exports") as Unit
        }
    }

    override fun close() {
        modules.clear()
        super.close()
    }

    abstract override fun getModuleScript(moduleName: String): String?

    override fun executeModuleScript(source: String, fileName: String): JSObject? {
        val moduleName = convertModuleName(null, fileName) ?: ""
        val wrapper = MODULE_SCRIPT_WRAPPER.replace("#CODE", source)
        val module = super.executeScript(wrapper, moduleName) as JSObject?
        if (module != null) {
            module.set("id", moduleName)
            module.set("filename", moduleName)
            modules[moduleName] = module
        }
        return module
    }

    fun executeModule(moduleName: String): JSObject? {
        val script = getModuleScript(moduleName)
        if (script == null) {
            throw RuntimeException("'moduleName' script is null")
        }
        return executeModuleScript(script, moduleName)
    }
}
