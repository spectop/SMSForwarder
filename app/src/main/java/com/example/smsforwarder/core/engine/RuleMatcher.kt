package com.example.smsforwarder.core.engine

import com.example.smsforwarder.core.config.model.MatchingRule
import com.example.smsforwarder.core.config.model.MatchingRuleType
import com.example.smsforwarder.core.sms.model.SmsMessage

/**
 * 规则匹配器
 * 负责根据匹配规则检查SMS消息是否匹配
 */
object RuleMatcher {
    
    /**
     * 检查短信是否匹配指定的规则
     * 
     * @param sms 短信消息
     * @param rule 匹配规则
     * @return 匹配结果，包含是否匹配和提取的变量
     */
    fun match(sms: SmsMessage, rule: MatchingRule): RuleMatchResult {
        if (!rule.isValid()) {
            return RuleMatchResult(false, emptyMap(), "规则配置无效")
        }
        
        return when (rule.type) {
            MatchingRuleType.PHONE_PREFIX -> matchPhonePrefix(sms, rule)
            MatchingRuleType.PHONE_REGEX -> matchPhoneRegex(sms, rule)
            MatchingRuleType.CONTENT_REGEX -> matchContentRegex(sms, rule)
        }
    }
    
    /**
     * 匹配电话号码前缀
     */
    private fun matchPhonePrefix(sms: SmsMessage, rule: MatchingRule): RuleMatchResult {
        val phonePattern = rule.phone.replace("*", ".*")
        val regex = Regex(phonePattern)
        
        return if (regex.matches(sms.sender)) {
            // 创建内置变量
            val variables = MatchingRule.createDefaultVariables(
                phone = sms.sender,
                content = sms.content,
                timestamp = sms.timestamp
            )
            RuleMatchResult(true, variables, "号码前缀匹配成功")
        } else {
            RuleMatchResult(false, emptyMap(), "号码前缀不匹配")
        }
    }
    
    /**
     * 匹配电话号码正则表达式
     */
    private fun matchPhoneRegex(sms: SmsMessage, rule: MatchingRule): RuleMatchResult {
        val regex = try {
            Regex(rule.phone)
        } catch (e: Exception) {
            return RuleMatchResult(false, emptyMap(), "电话号码正则表达式无效: ${e.message}")
        }
        
        val matchResult = regex.find(sms.sender)
        return if (matchResult != null) {
            // 创建内置变量
            val baseVariables = MatchingRule.createDefaultVariables(
                phone = sms.sender,
                content = sms.content,
                timestamp = sms.timestamp
            )
            
            // 提取正则捕获组
            val extractedVariables = extractVariablesFromRegex(matchResult, rule.variables)
            
            // 合并变量
            val allVariables = baseVariables + extractedVariables
            RuleMatchResult(true, allVariables, "电话号码正则匹配成功")
        } else {
            RuleMatchResult(false, emptyMap(), "电话号码正则不匹配")
        }
    }
    
    /**
     * 匹配内容正则表达式
     */
    private fun matchContentRegex(sms: SmsMessage, rule: MatchingRule): RuleMatchResult {
        val regex = try {
            Regex(rule.content)
        } catch (e: Exception) {
            return RuleMatchResult(false, emptyMap(), "内容正则表达式无效: ${e.message}")
        }
        
        val matchResult = regex.find(sms.content)
        return if (matchResult != null) {
            // 创建内置变量
            val baseVariables = MatchingRule.createDefaultVariables(
                phone = sms.sender,
                content = sms.content,
                timestamp = sms.timestamp
            )
            
            // 提取正则捕获组
            val extractedVariables = extractVariablesFromRegex(matchResult, rule.variables)
            
            // 合并变量
            val allVariables = baseVariables + extractedVariables
            RuleMatchResult(true, allVariables, "内容正则匹配成功")
        } else {
            RuleMatchResult(false, emptyMap(), "内容正则不匹配")
        }
    }
    
    /**
     * 从正则匹配结果中提取变量
     */
    private fun extractVariablesFromRegex(
        matchResult: kotlin.text.MatchResult,
        variableDefinitions: Map<String, String>
    ): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        
        for ((variableName, valueRef) in variableDefinitions) {
            when {
                // 固定值
                !valueRef.startsWith('$') -> {
                    variables[variableName] = valueRef
                }
                // 正则捕获组引用，如 $1, $2
                valueRef.matches(Regex("^\\$\\d+$")) -> {
                    val groupIndex = valueRef.substring(1).toIntOrNull()
                    if (groupIndex != null && groupIndex <= matchResult.groupValues.size) {
                        variables[variableName] = matchResult.groupValues[groupIndex]
                    }
                }
                // 内置变量引用，如 ${_phone}
                valueRef.startsWith("\${") && valueRef.endsWith("}") -> {
                    // 这里不处理，因为内置变量已经在baseVariables中
                }
                // 从phone中提取的变量，如 ${phone.1}
                valueRef.startsWith("\${phone.") && valueRef.endsWith("}") -> {
                    // 这里需要额外的phone正则匹配，暂时不支持
                }
            }
        }
        
        return variables
    }
    
    /**
     * 匹配结果数据类
     */
    data class RuleMatchResult(
        val isMatched: Boolean,
        val variables: Map<String, String>,
        val message: String
    )
    
    /**
     * 批量匹配多个规则
     * 
     * @param sms 短信消息
     * @param rules 匹配规则列表
     * @return 所有匹配的规则和对应的变量
     */
    fun matchAll(sms: SmsMessage, rules: List<MatchingRule>): List<Pair<MatchingRule, RuleMatchResult>> {
        return rules.mapNotNull { rule ->
            val result = match(sms, rule)
            if (result.isMatched) {
                rule to result
            } else {
                null
            }
        }
    }
    
    /**
     * 查找第一个匹配的规则
     */
    fun findFirstMatch(sms: SmsMessage, rules: List<MatchingRule>): Pair<MatchingRule, RuleMatchResult>? {
        return matchAll(sms, rules).firstOrNull()
    }
}