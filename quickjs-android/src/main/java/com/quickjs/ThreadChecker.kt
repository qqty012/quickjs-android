package com.quickjs;

/**
 * 线程锁
 */
class ThreadChecker(private val runtime: QuickJS) {
    private var thread:Thread? = null
    private var released:Boolean = false

    init {
        acquire()
    }

    @Synchronized
    fun acquire() {
        if (this.thread != null && this.thread != Thread.currentThread()) {
            throw Error("All QuickJS methods must be called on the same thread. Invalid QuickJS thread access: current thread is ${Thread.currentThread()} while the locker has thread ${this.thread}")
        } else if (this.thread != Thread.currentThread()) {
            this.thread = Thread.currentThread()
            this.released = false
        }
    }

    fun checkThread() {
        if (this.released && this.thread == null) {
            throw Error("Invalid QuickJS thread access: the locker has been released!")
        } else if (this.thread != Thread.currentThread()) {
            throw  Error("All QuickJS methods must be called on the same thread. Invalid QuickJS thread access: current thread is ${Thread.currentThread()} while the locker has thread ${this.thread}")
        }
    }
}
