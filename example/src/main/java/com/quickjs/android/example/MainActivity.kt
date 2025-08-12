package com.quickjs.android.example

import android.os.Bundle
import android.os.Environment
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.quickjs.ES6Module
import com.quickjs.JSContext
import com.quickjs.QuickJS

class MainActivity : AppCompatActivity() {

    private lateinit var quickJS: QuickJS
    private lateinit var jsContext: JSContext


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        quickJS = QuickJS.Companion.createRuntimeWithEventQueue()



        jsContext = object : ES6Module(quickJS) {
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            override fun getModuleScript(moduleName: String): String? {
                return "${baseDir.absolutePath}/$moduleName"
            }
        }


        jsContext.executeIntegerScript("var count = 0;count;", null)
        findViewById<Button>(R.id.create).setOnClickListener {
            Thread {
                jsContext.executeVoidScript(
                    "setTimeout(function(){console.log(count++)},1000)",
                    null
                )
            }.start()
        }


        findViewById<Button>(R.id.run).setOnClickListener {
            val script = assets.open("test.js").bufferedReader().use { it.readText() }
            jsContext.executeVoidScript(script, null)

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        quickJS.close()
    }
}