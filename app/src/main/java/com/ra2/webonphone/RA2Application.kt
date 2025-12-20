package com.ra2.webonphone

import android.app.Application
import android.os.Build
import android.webkit.WebView

class RA2Application : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化WebView进程（不创建实例，只预热进程）
        initWebViewProcess()
    }

    private fun initWebViewProcess() {
        try {
            // 设置WebView数据目录（避免多进程冲突）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = getProcessName()
                if (processName != packageName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
        } catch (e: Exception) {
            // 忽略初始化错误
        }
    }
}
