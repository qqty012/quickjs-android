package com.quickjs


class PromiseHelper(private val ctx: JSContext) {
    /**
     * 创建一个 Promise 并在block内暴露 resolve/reject
     */
    fun create(block: (resolve: JSFunction, reject: JSFunction) -> Unit): JSObject {
        // 1. 创建一个外部数组用于保存 resolve/reject
        val resolveRejectArray = ctx.executeArrayScript("(()=>{let arr=[]; function executor(resolve, reject){ arr=[resolve, reject]; } let p=new Promise(executor); return [p, arr]; })()", "") as JSArray
        val promise = resolveRejectArray.get(0) as JSObject
        val resolveReject = resolveRejectArray.get(1) as JSArray
        val resolve = resolveReject.get(0) as JSFunction
        val reject = resolveReject.get(1) as JSFunction
        val handler = ctx.quickJS.native.handler

        // 2. 交给你的异步逻辑
//        block(resolve, reject)
        block(
            JSFunction(ctx) { _, args ->
                handler.post {
                    // resolve
                    resolve.call(null, *args)
                }
            },
            JSFunction(ctx) { _, args ->
                handler.post {
                    // reject
                    reject.call(null, *args)
                }
            }
        )

        // 3. 返回 Promise
        return promise
    }
}