package com.recky.yijianxiping

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

/**
 * 桌面点击图标：
 * - Shizuku 已运行且已授权 → IPowerManager.goToSleep 息屏并退出
 * - 否则 → 引导启动 Shizuku / 授权（整机重启后需重新启动 Shizuku）
 */
class MainActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var acted = false

    private val binderListener = Shizuku.OnBinderReceivedListener {
        mainHandler.post { tryAct("binder") }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        mainHandler.post {
            acted = false
            showSetup(getString(R.string.setup_shizuku_dead))
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            mainHandler.post {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    screenOffAndExit()
                } else {
                    showSetup(getString(R.string.setup_need_grant))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        // 给 binder 一点时间，避免刚启动 Shizuku 时竞态
        mainHandler.postDelayed({ tryAct("delayed") }, 200)
        tryAct("create")
    }

    override fun onResume() {
        super.onResume()
        if (!acted) {
            tryAct("resume")
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun isReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            Log.w(TAG, "isReady failed", t)
            false
        }
    }

    private fun tryAct(from: String) {
        if (acted || isFinishing) return
        Log.i(TAG, "tryAct from=$from ping=${runCatching { Shizuku.pingBinder() }.getOrNull()}")
        if (!Shizuku.pingBinder()) {
            showSetup(getString(R.string.setup_start_shizuku))
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            showSetup(getString(R.string.setup_need_grant))
            return
        }
        screenOffAndExit()
    }

    private fun screenOffAndExit() {
        if (acted) return
        acted = true
        try {
            PowerActions.goToSleep()
        } catch (t: Throwable) {
            acted = false
            Log.e(TAG, "goToSleep failed", t)
            Toast.makeText(
                this,
                getString(R.string.lock_failed, t.message ?: t.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
            showSetup(getString(R.string.setup_need_grant))
            return
        }
        finishAndRemoveTask()
    }

    private fun showSetup(hint: String) {
        setContentView(R.layout.activity_setup)
        findViewById<TextView>(R.id.tvHint).text = hint
        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            acted = false
            if (!Shizuku.pingBinder()) {
                openShizukuManager()
                Toast.makeText(this, R.string.toast_start_shizuku_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            } else {
                screenOffAndExit()
            }
        }
        findViewById<Button>(R.id.btnOpenShizuku).setOnClickListener { openShizukuManager() }
    }

    private fun openShizukuManager() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.toast_shizuku_missing, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "Yijianxiping"
        private const val REQUEST_CODE_SHIZUKU = 1001
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
