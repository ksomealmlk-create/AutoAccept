package com.example.autoaccept

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "autoaccept/native")
            .setMethodCallHandler { call, result ->
                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                when (call.method) {
                    "openSettings" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(null)
                    }
                    "isEnabled" -> {
                        val e = Settings.Secure.getString(
                            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                        ) ?: ""
                        result.success(e.contains("$packageName/.AutoAcceptService") ||
                                e.contains("$packageName/$packageName.AutoAcceptService"))
                    }
                    "getAll" -> result.success(prefs.all.mapValues { it.value.toString() })
                    "set" -> {
                        val k = call.argument<String>("key")!!
                        val v = call.argument<String>("value")!!
                        prefs.edit().putString(k, v).apply()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
