package com.example.smsforwarder.core.config.model

import kotlinx.serialization.Serializable

/**
 * 应用顶层配置，对应 config.yaml 根结构
 */
@Serializable
data class AppConfig(
    val matching_rules: List<MatchingRule> = emptyList(),
    val push_rules: List<PushRule> = emptyList(),
    val workflows: List<Workflow> = emptyList()
) {
    /** 根据 ID 查找匹配规则 */
    fun findMatchingRule(id: String): MatchingRule? = matching_rules.find { it.id == id }

    /** 根据 ID 查找推送规则 */
    fun findPushRule(id: String): PushRule? = push_rules.find { it.id == id }

    /** 返回所有已启用工作流 */
    fun enabledWorkflows(): List<Workflow> = workflows.filter { it.enabled }
}
