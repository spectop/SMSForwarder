package com.example.smsforwarder.core.config.validator

import com.example.smsforwarder.core.config.model.AppConfig

/**
 * 配置校验结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun ok(warnings: List<String> = emptyList()) = ValidationResult(true, emptyList(), warnings)
        fun fail(errors: List<String>) = ValidationResult(false, errors)
    }
}

/**
 * 配置校验器
 */
object ConfigValidator {

    fun validate(config: AppConfig): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 校验匹配规则
        config.matching_rules.forEach { rule ->
            if (rule.id.isEmpty()) errors.add("存在 id 为空的匹配规则")
            if (!rule.isValid()) errors.add("匹配规则 [${rule.id}] 配置无效")
            rule.content.takeIf { it.isNotEmpty() }?.let {
                try { Regex(it) } catch (e: Exception) {
                    errors.add("匹配规则 [${rule.id}] content 正则无效: ${e.message}")
                }
            }
            rule.phone.takeIf { it.isNotEmpty() }?.let {
                try { Regex(it) } catch (e: Exception) {
                    errors.add("匹配规则 [${rule.id}] phone 正则无效: ${e.message}")
                }
            }
        }

        // 校验推送规则
        config.push_rules.forEach { rule ->
            if (rule.id.isEmpty()) errors.add("存在 id 为空的推送规则")
            if (!rule.isValid()) errors.add("推送规则 [${rule.id}] 配置无效（缺少必要字段）")
        }

        // 校验工作流
        val matchingIds = config.matching_rules.map { it.id }.toSet()
        val pushRuleIds = config.push_rules.map { it.id }.toSet()
        config.workflows.forEach { wf ->
            if (wf.id.isEmpty()) errors.add("存在 id 为空的工作流")
            if (wf.matching !in matchingIds) {
                errors.add("工作流 [${wf.id}] 引用了不存在的匹配规则: ${wf.matching}")
            }
            wf.pushers.forEach { pusher ->
                if (pusher.id !in pushRuleIds) {
                    errors.add("工作流 [${wf.id}] 引用了不存在的推送规则: ${pusher.id}")
                }
            }
            if (wf.pushers.isEmpty() && wf.enabled) {
                warnings.add("工作流 [${wf.id}] 已启用但没有配置任何推送器")
            }
        }

        return if (errors.isEmpty()) ValidationResult.ok(warnings) else ValidationResult.fail(errors)
    }
}
