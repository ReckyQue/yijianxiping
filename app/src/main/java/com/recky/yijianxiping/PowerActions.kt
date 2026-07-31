package com.recky.yijianxiping

import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * 经 Shizuku 代理调用 IPowerManager（shell/adb 身份，免 Root）。
 * 手机整机重启后需重新启动 Shizuku。
 */
object PowerActions {

    private const val TAG = "PowerActions"
    private const val DESCRIPTOR = "android.os.IPowerManager"

    private fun powerBinder(): IBinder {
        val raw = SystemServiceHelper.getSystemService("power")
            ?: error("power service unavailable")
        return ShizukuBinderWrapper(raw)
    }

    private fun powerManager(): Any {
        val wrapped = powerBinder()
        val stubClass = Class.forName("android.os.IPowerManager\$Stub")
        val asInterface = HiddenApiBypass.getDeclaredMethod(stubClass, "asInterface", IBinder::class.java)
        return asInterface.invoke(null, wrapped) ?: error("IPowerManager null")
    }

    /** 息屏（不关机） */
    fun goToSleep() {
        val time = SystemClock.uptimeMillis()
        val errors = mutableListOf<String>()

        // 1) 反射：对 IPowerManager 接口调用（HiddenApiBypass）
        try {
            val pm = powerManager()
            val iface = Class.forName("android.os.IPowerManager")
            HiddenApiBypass.invoke(iface, pm, "goToSleep", time, 0, 0)
            Log.i(TAG, "goToSleep via reflection OK")
            return
        } catch (t: Throwable) {
            errors += "reflect3: ${t.javaClass.simpleName}: ${t.message}"
        }

        try {
            val pm = powerManager()
            val iface = Class.forName("android.os.IPowerManager")
            HiddenApiBypass.invoke(iface, pm, "goToSleepWithDisplayId", 0, time, 0, 0)
            Log.i(TAG, "goToSleepWithDisplayId OK")
            return
        } catch (t: Throwable) {
            errors += "reflectDisplay: ${t.javaClass.simpleName}: ${t.message}"
        }

        // 2) 直写 Binder transact（绕过 Proxy 方法查找）
        try {
            transactGoToSleep(time)
            Log.i(TAG, "goToSleep via transact OK")
            return
        } catch (t: Throwable) {
            errors += "transact: ${t.javaClass.simpleName}: ${t.message}"
        }

        // 3) shell 模拟电源键（最后兜底）
        try {
            shellPowerKey()
            Log.i(TAG, "goToSleep via input keyevent OK")
            return
        } catch (t: Throwable) {
            errors += "shell: ${t.javaClass.simpleName}: ${t.message}"
        }

        error(errors.joinToString(" | "))
    }

    private fun transactGoToSleep(time: Long) {
        val binder = powerBinder()
        val stub = Class.forName("android.os.IPowerManager\$Stub")
        val code = try {
            val f = stub.getDeclaredField("TRANSACTION_goToSleep")
            f.isAccessible = true
            f.getInt(null)
        } catch (_: Throwable) {
            // AOSP 常见值因版本而异，用 getTransactionName 扫描
            resolveTransactionCode(stub, "goToSleep")
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeLong(time)
            data.writeInt(0) // reason
            data.writeInt(0) // flags
            val ok = binder.transact(code, data, reply, 0)
            if (!ok) error("transact returned false code=$code")
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun resolveTransactionCode(stub: Class<*>, name: String): Int {
        // Android 10+ Stub 可能有 getDefaultTransactionName
        try {
            val m = HiddenApiBypass.getDeclaredMethod(stub, "getDefaultTransactionName", Int::class.javaPrimitiveType)
            for (code in 1..200) {
                val n = m.invoke(null, code) as? String
                if (n == name) return code
            }
        } catch (_: Throwable) {
        }
        // 回退：扫描 TRANSACTION_* 字段
        for (f in stub.declaredFields) {
            if (f.name == "TRANSACTION_$name") {
                f.isAccessible = true
                return f.getInt(null)
            }
        }
        error("TRANSACTION_$name not found")
    }

    private fun shellPowerKey() {
        // Shizuku 以 shell 身份执行 input keyevent 26
        val clz = Class.forName("rikka.shizuku.Shizuku")
        // public static native int newProcess(...) 在部分版本；改用反射调内部或使用 IActivityManager
        // 更稳妥：通过 Shizuku 执行 `sh -c 'input keyevent 26'`
        val newProcess = try {
            clz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
        } catch (_: NoSuchMethodException) {
            null
        }
        if (newProcess != null) {
            val proc = newProcess.invoke(null, arrayOf("input", "keyevent", "26"), null, null)
            // wait if Process-like
            try {
                proc?.javaClass?.getMethod("waitFor")?.invoke(proc)
            } catch (_: Throwable) {
            }
            return
        }
        // 无 newProcess：用 app_process 不可行；抛错交给上层
        error("Shizuku.newProcess unavailable")
    }

    fun reboot(reason: String = "yijianxiping") {
        val pm = powerManager()
        val iface = Class.forName("android.os.IPowerManager")
        try {
            HiddenApiBypass.invoke(iface, pm, "reboot", false, reason, false)
        } catch (_: NoSuchMethodException) {
            HiddenApiBypass.invoke(iface, pm, "reboot", reason)
        }
    }

    fun shutdown() {
        val pm = powerManager()
        val iface = Class.forName("android.os.IPowerManager")
        HiddenApiBypass.invoke(iface, pm, "shutdown", false, null, false)
    }
}
