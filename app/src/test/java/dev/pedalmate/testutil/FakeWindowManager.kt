package dev.pedalmate.testutil

import android.view.View
import android.view.WindowManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/** Records addView/removeView/updateViewLayout instead of touching a real window; failures can be injected. */
class FakeWindowManager {
    class Added(val view: View, val params: WindowManager.LayoutParams)
    val added = mutableListOf<Added>()
    val removed = mutableListOf<View>()
    val updates = mutableListOf<Pair<View, WindowManager.LayoutParams>>()
    var addFailure: RuntimeException? = null
    var removeFailure: RuntimeException? = null

    val manager: WindowManager = Proxy.newProxyInstance(
        WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "addView" -> { addFailure?.let { throw it }; added += Added(args[0] as View, args[1] as WindowManager.LayoutParams); null }
                "removeView", "removeViewImmediate" -> { removeFailure?.let { throw it }; removed += args[0] as View; null }
                "updateViewLayout" -> { updates += (args[0] as View) to (args[1] as WindowManager.LayoutParams); null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                "toString" -> "FakeWindowManager"
                else -> when (method.returnType) {
                    java.lang.Integer.TYPE -> 0
                    java.lang.Boolean.TYPE -> false
                    java.lang.Float.TYPE -> 0f
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        },
    ) as WindowManager
}
