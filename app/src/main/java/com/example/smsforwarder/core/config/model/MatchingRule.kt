package com.example.smsforwarder.core.config.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 匹配规则类型枚举
 */
@Serializable
enum class MatchingRuleType {
    @SerialName("phone-prefix")
    PHONE_PREFIX,
    
    @SerialName("phone-regex")
    PHONE_REGEX,
    
    @SerialName("content-regex")
    CONTENT_REGEX
}

/**
 * 匹配规则数据类
 * 
 * @property id 规则唯一标识符
 * @property name 规则名称
 * @property type 匹配规则类型
 * @property phone 用于 phone-prefix 和 phone-regex 类型的号码匹配
 * @property content 用于 content-regex 类型的内容匹配和内容萃取
 * @property variables 从匹配中提取的变量，键为变量名，值为正则表达式的捕获组引用或固定值
 */
@Serializable
data class MatchingRule(
    val id: String,
    val name: String,
    val type: MatchingRuleType,
    val phone: String = "",
    val content: String = "",
    val variables: Map<String, String> = emptyMap()
) {
    /**
     * 验证规则配置是否有效
     */
    fun isValid(): Boolean {
        return when (type) {
            MatchingRuleType.PHONE_PREFIX -> phone.isNotEmpty()
            MatchingRuleType.PHONE_REGEX -> phone.isNotEmpty()
            MatchingRuleType.CONTENT_REGEX -> content.isNotEmpty()
        }
    }
    
    /**
     * 获取所有可用的变量名（包括内置变量）
     */
    fun getAllVariableNames(): Set<String> {
        return variables.keys + setOf("_phone", "_content", "_timestamp")
    }
    
    /**
     * 检查变量引用是否有效
     * @param variableRef 变量引用，格式如 "${variable_name}" 或 "$1"
     * @return 如果变量引用有效返回true
     */
    fun isValidVariableReference(variableRef: String): Boolean {
        if (variableRef.isEmpty()) return false
        
        // 处理 $1, $2 等正则捕获组引用
        if (variableRef.matches(Regex("^\\$\\d+$"))) {
            return true
        }
        
        // 处理 ${variable_name} 格式
        if (variableRef.matches(Regex("^\\$\\{[^}]+\\}$"))) {
            val varName = variableRef.removePrefix("\${").removeSuffix("}")
            // 检查是否是内置变量
            if (varName in setOf("_phone", "_content", "_timestamp")) {
                return true
            }
            // 检查是否是自定义变量
            if (variables.containsKey(varName)) {
                return true
            }
            // 检查是否是 phone.1 格式
            if (varName.matches(Regex("^phone\\.\\d+$"))) {
                return true
            }
        }
        
        return false
    }
    
    companion object {
        /**
         * 创建默认的内置变量映射
         */
        fun createDefaultVariables(
            phone: String = "",
            content: String = "",
            timestamp: Long = System.currentTimeMillis()
        ): Map<String, String> {
            return mapOf(
                "_phone" to phone,
                "_content" to content,
                "_timestamp" to timestamp.toString()
            )
        }
    }
}

/**
 * 匹配规则列表包装类，用于YAML序列化
 */
@Serializable
data class MatchingRules(
    val matching_rules: List<MatchingRule> = emptyList()
)