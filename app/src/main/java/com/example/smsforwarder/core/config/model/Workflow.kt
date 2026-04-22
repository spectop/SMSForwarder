package com.example.smsforwarder.core.config.model

import kotlinx.serialization.Serializable

/**
 * 工作流中的推送器配置
 * 
 * @property id 推送规则ID
 * @property varMap 变量映射配置
 */
@Serializable
data class WorkflowPusher(
    val id: String,
    val varMap: VariableMapping = VariableMapping()
) {
    /**
     * 验证推送器配置是否有效
     */
    fun isValid(): Boolean {
        return id.isNotEmpty() && varMap.isValid()
    }
}

/**
 * 工作流数据类
 * 
 * @property id 工作流唯一标识符
 * @property name 工作流名称
 * @property enabled 是否启用
 * @property matching 匹配规则ID
 * @property pushers 推送器配置列表
 */
@Serializable
data class Workflow(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matching: String = "",
    val matchings: List<String> = emptyList(),
    val pushers: List<WorkflowPusher> = emptyList()
) {
    /**
     * 验证工作流配置是否有效
     */
    fun isValid(): Boolean {
        return id.isNotEmpty() && 
               name.isNotEmpty() && 
               matchingIds().isNotEmpty() && 
               pushers.all { it.isValid() }
    }

    /**
     * 获取工作流关联的匹配规则 ID 列表。
     * 兼容旧字段 matching 与新字段 matchings。
     */
    fun matchingIds(): List<String> {
        if (matchings.isNotEmpty()) {
            return matchings.filter { it.isNotBlank() }.distinct()
        }
        return if (matching.isNotBlank()) listOf(matching) else emptyList()
    }
    
    /**
     * 获取工作流中引用的所有推送器ID
     */
    fun getPusherIds(): Set<String> {
        return pushers.map { it.id }.toSet()
    }
    
    /**
     * 根据推送器ID查找推送器配置
     */
    fun findPusherById(pusherId: String): WorkflowPusher? {
        return pushers.find { it.id == pusherId }
    }
    
    /**
     * 获取工作流中所有变量映射的源变量
     */
    fun getAllSourceVariables(): Set<String> {
        return pushers.flatMap { it.varMap.getAllSourceVariables() }.toSet()
    }
    
    /**
     * 获取工作流中所有变量映射的目标变量
     */
    fun getAllTargetVariables(): Set<String> {
        return pushers.flatMap { it.varMap.getAllTargetVariables() }.toSet()
    }
}

/**
 * 工作流列表包装类，用于YAML序列化
 */
@Serializable
data class Workflows(
    val workflows: List<Workflow> = emptyList()
)