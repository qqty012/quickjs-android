package com.quickjs

import java.util.ArrayList

abstract class Module(quickJS: QuickJS, contextPtr: Long): JSContext(quickJS, contextPtr) {


    abstract fun getModuleScript(moduleName: String): String?

    fun convertModuleName(moduleBaseName: String?, moduleName: String?): String? {
        if (moduleName == null || moduleName.isEmpty()) {
            return moduleName
        }
        var moduleName = moduleName.replace("//", "/")
        if (moduleName.startsWith("./")) {
            moduleName = moduleName.substring(2)
        }
        if (moduleName[0] == '/') {
            return moduleName
        }
        if (moduleBaseName.isNullOrEmpty()) {
            return moduleName
        }
        var moduleBaseName = moduleBaseName.replace("//", "/")
        if (moduleBaseName.startsWith("./")) {
            moduleBaseName = moduleBaseName.substring(2)
        }
        if (moduleBaseName == "/") {
            return "/$moduleName"
        }
        if (moduleBaseName.endsWith("/")) {
            return moduleBaseName + moduleName
        }
        val parentSplit = moduleBaseName.split("/")
        val pathSplit = moduleName.split("/")
        val parentStack = ArrayList<String>()
        val pathStack = ArrayList<String>()
        parentStack.addAll(parentSplit)
        pathStack.addAll(pathSplit)
        while (!pathStack.isEmpty()) {
            val tmp = pathStack[0]
            if (tmp == "..") {
                pathStack.removeAt(0)
                parentStack.removeAt(parentStack.size - 1)
            } else {
                break
            }
        }
        if (!parentStack.isEmpty()) {
            parentStack.removeAt(parentStack.size - 1)
        }
        val builder = StringBuilder()
        if (moduleBaseName.startsWith("/")) {
            builder.append("/")
        }
        for (it in parentStack) {
            builder.append(it).append("/")
        }
        for (it in pathStack) {
            builder.append(it).append("/")
        }
        builder.deleteCharAt(builder.length - 1)
        return builder.toString()
    }
}
