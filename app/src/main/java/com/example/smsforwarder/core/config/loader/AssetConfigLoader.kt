package com.example.smsforwarder.core.config.loader

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "AssetConfigLoader"
private const val ASSET_CONFIG_PATH = "config/config.yaml"
private const val USER_CONFIG_FILENAME = "config.yaml"

/**
 * 配置文件加载器
 * 优先加载用户目录中的 config.yaml，不存在时从 assets 复制并读取
 */
object AssetConfigLoader {

    /**
     * 读取配置文本
     * 优先级：filesDir/config.yaml > assets/config/config.yaml
     */
    fun readConfigText(context: Context): String {
        val userFile = getUserConfigFile(context)
        if (userFile.exists()) {
            return try {
                userFile.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "读取用户配置失败，回退到 assets: ${e.message}")
                readFromAssets(context)
            }
        }
        // 首次运行：从 assets 复制到 filesDir
        val defaultContent = readFromAssets(context)
        if (defaultContent.isNotEmpty()) {
            try {
                userFile.writeText(defaultContent, Charsets.UTF_8)
                Log.i(TAG, "已将默认配置复制到: ${userFile.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "复制默认配置失败: ${e.message}")
            }
        }
        return defaultContent
    }

    /**
     * 保存配置文本到用户目录
     */
    fun saveConfigText(context: Context, content: String): Boolean {
        return try {
            getUserConfigFile(context).writeText(content, Charsets.UTF_8)
            Log.i(TAG, "配置已保存")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存配置失败: ${e.message}", e)
            false
        }
    }

    /**
     * 读取 assets 中的默认配置
     */
    fun readFromAssets(context: Context): String {
        return try {
            context.assets.open(ASSET_CONFIG_PATH).bufferedReader(Charsets.UTF_8).readText()
        } catch (e: Exception) {
            Log.e(TAG, "读取 assets 配置失败: ${e.message}", e)
            ""
        }
    }

    /**
     * 获取用户配置文件路径
     */
    fun getUserConfigFile(context: Context): File {
        return File(context.filesDir, USER_CONFIG_FILENAME)
    }
}
