package com.recky.yijianxiping

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 放宽隐藏 API 限制，便于反射调用 IPowerManager
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }
}
