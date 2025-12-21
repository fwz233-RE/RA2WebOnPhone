package com.ra2.webonphone.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.ra2.webonphone.data.AppLanguage
import java.util.Locale

object LocaleHelper {

    fun setLocale(context: Context, language: AppLanguage): Context {
        val locale = when (language) {
            AppLanguage.SYSTEM -> getSystemLocale()
            AppLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.TRADITIONAL_CHINESE -> Locale.TRADITIONAL_CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.JAPANESE -> Locale.JAPANESE
        }
        return updateResources(context, locale)
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.os.LocaleList.getDefault().get(0)
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault()
        }
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    fun getLocaleDisplayName(language: AppLanguage): String {
        return when (language) {
            AppLanguage.SYSTEM -> "跟随系统 / Follow System"
            AppLanguage.SIMPLIFIED_CHINESE -> "简体中文"
            AppLanguage.TRADITIONAL_CHINESE -> "繁體中文"
            AppLanguage.ENGLISH -> "English"
            AppLanguage.JAPANESE -> "日本語"
        }
    }
}
