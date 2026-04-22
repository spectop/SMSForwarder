package com.example.smsforwarder.core.config.model

import kotlinx.serialization.Serializable

/**
 * 推送器类型枚举
 */
@Serializable
enum class PusherType {
    SMS_AGENT,
    CURL
}

/**
 * 推送规则数据类
 * 
 * @property id 规则唯一标识符
 * @property name 规则名称
 * @property enabled 是否启用
 * @property type 推送器类型
 * @property config 推送配置，键值对形式
 */
@Serializable
data class PushRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val type: PusherType,
    val config: Map<String, String> = emptyMap()
) {
    /**
     * 验证推送规则配置是否有效
     */
    fun isValid(): Boolean {
        return when (type) {
            PusherType.SMS_AGENT -> {
                config.containsKey("tag") && 
                config.containsKey("endpoint") && 
                config.containsKey("token")
            }
            PusherType.CURL -> {
                config.containsKey("url")
            }
        }
    }
    
    /**
     * 获取配置中所有包含变量引用的键
     * @return 包含变量引用的键列表
     */
    fun getVariableReferences(): List<String> {
        return config.values.filter { it.contains("\${") }
    }
    
    /**
     * 从配置值中提取变量名
     * @param value 配置值
     * @return 变量名列表
     */
    fun extractVariablesFromValue(value: String): List<String> {
        val pattern = Regex("\\$\\{([^}]+)\\}")
        return pattern.findAll(value).map { it.groupValues[1] }.toList()
    }
    
    /**
     * 获取配置中引用的所有变量名
     * @return 变量名集合
     */
    fun getAllReferencedVariables(): Set<String> {
        return config.values.flatMap { extractVariablesFromValue(it) }.toSet()
    }
}

/**
 * 推送规则列表包装类，用于YAML序列化
 */
@Serializable
data class PushRules(
    val push_rules: List<PushRule> = emptyList()
)