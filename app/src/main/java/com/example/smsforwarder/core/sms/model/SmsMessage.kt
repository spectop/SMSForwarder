package com.example.smsforwarder.core.sms.model

import android.os.Parcelable
import com.example.smsforwarder.core.config.model.MatchingRule
import com.example.smsforwarder.core.config.model.MatchingRuleType
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern
import java.util.regex.Matcher

/**
 * SMS消息实体类
 * 表示从系统接收到的短信消息
 */
@Parcelize
data class SmsMessage(
    // 短信ID
    val id: Long = 0L,
    // 发件人号码
    val sender: String = "",
    // 短信内容
    val content: String = "",
    // 接收时间戳（毫秒）
    val timestamp: Long = System.currentTimeMillis(),
    // 是否已读
    val isRead: Boolean = false,
    // 匹配到的规则ID（如果有）
    var matchedRuleId: String? = null,
    // 提取的变量
    val extractedVariables: MutableMap<String, String> = mutableMapOf()
) : Parcelable {

    companion object {
        // 默认变量名
        const val VAR_PHONE = "_phone"
        const val VAR_CONTENT = "_content"
        const val VAR_TIMESTAMP = "_timestamp"
    }

    init {
        // 初始化默认变量
        extractedVariables[VAR_PHONE] = sender
        extractedVariables[VAR_CONTENT] = content
        extractedVariables[VAR_TIMESTAMP] = timestamp.toString()
    }

    /**
     * 判断是否匹配给定的规则
     * @param rule 匹配规则
     * @return 是否匹配
     */
    fun matches(rule: MatchingRule): Boolean {
        return when (rule.type) {
            MatchingRuleType.PHONE_PREFIX -> matchesPhonePrefix(rule)
            MatchingRuleType.PHONE_REGEX -> matchesPhoneRegex(rule)
            MatchingRuleType.CONTENT_REGEX -> matchesContentRegex(rule)
        }
    }

    /**
     * 应用匹配规则并提取变量
     * @param rule 匹配规则
     * @return 是否匹配并成功提取变量
     */
    fun applyRule(rule: MatchingRule): Boolean {
        if (!matches(rule)) {
            return false
        }

        // 标记匹配到的规则ID
        matchedRuleId = rule.id

        // 提取变量
        extractVariables(rule)

        return true
    }

    /**
     * 根据规则提取变量
     * @param rule 匹配规则
     */
    private fun extractVariables(rule: MatchingRule) {
        // 提取电话号码相关的变量
        extractPhoneVariables(rule)

        // 提取内容相关的变量
        extractContentVariables(rule)

        // 添加规则中定义的固定变量
        rule.variables.forEach { (key, value) ->
            // 如果值不是正则引用格式（如$1），则直接作为固定值
            if (!value.startsWith("$")) {
                extractedVariables[key] = value
            }
        }
    }

    /**
     * 提取电话号码相关的变量
     * @param rule 匹配规则
     */
    private fun extractPhoneVariables(rule: MatchingRule) {
        when (rule.type) {
            MatchingRuleType.PHONE_REGEX -> {
                // 使用正则表达式提取电话号码中的变量
                rule.phone.takeIf { it.isNotEmpty() }?.let { phonePattern ->
                    val pattern = Pattern.compile(phonePattern)
                    val matcher = pattern.matcher(sender)
                    if (matcher.find()) {
                        extractRegexGroups(matcher, rule.variables)
                    }
                }
            }
            MatchingRuleType.PHONE_PREFIX -> {
                // 电话号码前缀匹配，无变量提取
            }
            else -> {
                // 其他类型不处理电话号码
            }
        }
    }

    /**
     * 提取内容相关的变量
     * @param rule 匹配规则
     */
    private fun extractContentVariables(rule: MatchingRule) {
        when (rule.type) {
            MatchingRuleType.CONTENT_REGEX -> {
                // 使用正则表达式提取内容中的变量
                rule.content.takeIf { it.isNotEmpty() }?.let { contentPattern ->
                    val pattern = Pattern.compile(contentPattern)
                    val matcher = pattern.matcher(content)
                    if (matcher.find()) {
                        extractRegexGroups(matcher, rule.variables)
                    }
                }
            }
            else -> {
                // 其他类型不处理内容
            }
        }
    }

    /**
     * 从正则匹配中提取分组变量
     * @param matcher 正则匹配器
     * @param variableDefinitions 变量定义
     */
    private fun extractRegexGroups(matcher: Matcher, variableDefinitions: Map<String, String>?) {
        variableDefinitions?.forEach { (variableName, groupRef) ->
            if (groupRef.startsWith("$")) {
                // 处理$1, $2等引用格式
                val groupIndex = groupRef.substring(1).toIntOrNull()
                if (groupIndex != null && groupIndex <= matcher.groupCount()) {
                    val groupValue = matcher.group(groupIndex)
                    if (groupValue != null) {
                        extractedVariables[variableName] = groupValue.toString()
                    }
                }
            }
        }
    }

    /**
     * 检查电话号码前缀匹配
     * @param rule 匹配规则
     * @return 是否匹配
     */
    private fun matchesPhonePrefix(rule: MatchingRule): Boolean {
        return rule.phone.takeIf { it.isNotEmpty() }?.let { prefixPattern ->
            if (prefixPattern.endsWith("*")) {
                // 通配符匹配
                val prefix = prefixPattern.substring(0, prefixPattern.length - 1)
                sender.startsWith(prefix)
            } else {
                // 精确匹配
                sender == prefixPattern
            }
        } ?: false
    }

    /**
     * 检查电话号码正则匹配
     * @param rule 匹配规则
     * @return 是否匹配
     */
    private fun matchesPhoneRegex(rule: MatchingRule): Boolean {
        return rule.phone.takeIf { it.isNotEmpty() }?.let { regexPattern ->
            try {
                val pattern = Pattern.compile(regexPattern)
                pattern.matcher(sender).find()
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    /**
     * 检查内容正则匹配
     * @param rule 匹配规则
     * @return 是否匹配
     */
    private fun matchesContentRegex(rule: MatchingRule): Boolean {
        return rule.content.takeIf { it.isNotEmpty() }?.let { regexPattern ->
            try {
                val pattern = Pattern.compile(regexPattern)
                pattern.matcher(content).find()
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    /**
     * 获取变量值
     * @param variableName 变量名
     * @return 变量值，如果不存在则返回null
     */
    fun getVariable(variableName: String): String? {
        return extractedVariables[variableName]
    }

    /**
     * 设置变量值
     * @param variableName 变量名
     * @param value 变量值
     */
    fun setVariable(variableName: String, value: String) {
        extractedVariables[variableName] = value
    }

    /**
     * 检查是否包含所有指定的变量
     * @param variableNames 变量名列表
     * @return 是否包含所有变量
     */
    fun containsAllVariables(variableNames: List<String>): Boolean {
        return variableNames.all { extractedVariables.containsKey(it) }
    }

    /**
     * 获取所有变量
     * @return 变量映射的副本
     */
    fun getAllVariables(): Map<String, String> {
        return extractedVariables.toMap()
    }
}