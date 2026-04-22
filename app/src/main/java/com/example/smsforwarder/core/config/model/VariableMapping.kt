package com.example.smsforwarder.core.config.model

import kotlinx.serialization.Serializable

/**
 * 变量映射项
 * 
 * @property source 源变量名或表达式
 * @property target 目标变量名
 */
@Serializable
data class VariableMappingItem(
    val source: String,
    val target: String
) {
    /**
     * 验证映射项是否有效
     */
    fun isValid(): Boolean {
        return source.isNotEmpty() && target.isNotEmpty()
    }
    
    /**
     * 检查源变量是否是变量引用格式
     */
    fun isVariableReference(): Boolean {
        return source.startsWith("\${") && source.endsWith("}")
    }
    
    /**
     * 从源变量引用中提取变量名
     */
    fun extractVariableName(): String? {
        if (!isVariableReference()) return null
        return source.removePrefix("\${").removeSuffix("}")
    }
}

/**
 * 变量映射集合
 * 
 * @property mappings 变量映射项列表
 */
@Serializable
data class VariableMapping(
    val mappings: List<VariableMappingItem> = emptyList()
) {
    /**
     * 验证所有映射项是否有效
     */
    fun isValid(): Boolean {
        return mappings.all { it.isValid() }
    }
    
    /**
     * 获取所有源变量名
     */
    fun getAllSourceVariables(): Set<String> {
        return mappings.mapNotNull { it.extractVariableName() }.toSet()
    }
    
    /**
     * 获取所有目标变量名
     */
    fun getAllTargetVariables(): Set<String> {
        return mappings.map { it.target }.toSet()
    }
    
    /**
     * 根据目标变量名查找源变量
     */
    fun findSourceByTarget(target: String): String? {
        return mappings.find { it.target == target }?.source
    }
    
    /**
     * 根据源变量名查找目标变量
     */
    fun findTargetBySource(source: String): String? {
        return mappings.find { it.source == source }?.target
    }
    
    /**
     * 应用变量映射，将源变量值映射到目标变量
     * 
     * @param sourceVariables 源变量映射
     * @return 目标变量映射
     */
    fun applyMapping(sourceVariables: Map<String, String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        for (mapping in mappings) {
            if (mapping.isVariableReference()) {
                val sourceVarName = mapping.extractVariableName() ?: continue
                val sourceValue = sourceVariables[sourceVarName] ?: continue
                result[mapping.target] = sourceValue
            } else {
                // 如果是固定值，直接使用
                result[mapping.target] = mapping.source
            }
        }
        
        return result
    }
}