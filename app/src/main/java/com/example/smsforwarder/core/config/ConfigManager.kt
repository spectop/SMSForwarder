package com.example.smsforwarder.core.config

import android.content.Context
import android.util.Log
import com.example.smsforwarder.core.config.loader.AssetConfigLoader
import com.example.smsforwarder.core.config.loader.YamlConfigLoader
import com.example.smsforwarder.core.config.model.AppConfig

private const val TAG = "ConfigManager"

/**
 * 配置管理器，负责加载、缓存和重载 AppConfig
 */
class ConfigManager(private val context: Context) {

    @Volatile
    private var cachedConfig: AppConfig? = null

    /**
     * 获取当前配置（缓存优先）
     */
    fun getConfig(): AppConfig {
        return cachedConfig ?: reload()
    }

    /**
     * 从文件重新加载配置
     */
    fun reload(): AppConfig {
        val text = AssetConfigLoader.readConfigText(context)
        val config = YamlConfigLoader.load(text)
        if (config != null) {
            cachedConfig = config
            Log.i(TAG, "配置加载成功：${config.workflows.size} 个工作流，${config.matching_rules.size} 个匹配规则，${config.push_rules.size} 个推送规则")
        } else {
            Log.e(TAG, "配置解析失败，使用空配置")
        }
        return cachedConfig ?: AppConfig()
    }

    /**
     * 保存新配置文本并刷新缓存
     * @return 保存是否成功
     */
    fun saveAndReload(content: String): Boolean {
        val saved = AssetConfigLoader.saveConfigText(context, content)
        if (saved) {
            cachedConfig = null
            reload()
        }
        return saved
    }

    /**
     * 读取当前配置的原始 YAML 文本
     */
    fun readRawConfig(): String {
        return AssetConfigLoader.readConfigText(context)
    }

    /**
     * 清除缓存，下次 getConfig() 时重新加载
     */
    fun invalidate() {
        cachedConfig = null
    }
}
