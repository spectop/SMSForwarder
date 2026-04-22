package com.example.smsforwarder.core.config.loader

import android.util.Log
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.example.smsforwarder.core.config.model.AppConfig

private const val TAG = "YamlConfigLoader"

/**
 * 从 YAML 字符串解析 AppConfig
 */
object YamlConfigLoader {

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false
        )
    )

    /**
     * 从 YAML 字符串加载配置
     * @param content YAML 文本
     * @return 解析成功的 AppConfig，失败时返回 null
     */
    fun load(content: String): AppConfig? {
        return try {
            yaml.decodeFromString(AppConfig.serializer(), content)
        } catch (e: Exception) {
            Log.e(TAG, "YAML 解析失败: ${e.message}", e)
            null
        }
    }

    /**
     * 将 AppConfig 序列化为 YAML 文本
     */
    fun dump(config: AppConfig): String? {
        return try {
            yaml.encodeToString(AppConfig.serializer(), config)
        } catch (e: Exception) {
            Log.e(TAG, "YAML 序列化失败: ${e.message}", e)
            null
        }
    }
}
