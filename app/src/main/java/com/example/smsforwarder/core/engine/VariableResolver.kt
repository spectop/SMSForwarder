package com.example.smsforwarder.core.engine

/**
 * 变量解析器：将 ${varName} 占位符替换为实际值
 */
object VariableResolver {

    private val PLACEHOLDER_REGEX = Regex("\\$\\{([^}]+)\\}")

    /**
     * 在模板字符串中替换所有变量占位符
     * @param template 包含 ${varName} 占位符的模板
     * @param variables 变量名到值的映射
     * @return 替换后的字符串，未找到的变量保持原样
     */
    fun resolve(template: String, variables: Map<String, String>): String {
        return PLACEHOLDER_REGEX.replace(template) { matchResult ->
            val varName = matchResult.groupValues[1]
            variables[varName] ?: matchResult.value
        }
    }

    /**
     * 对 Map 中所有值进行变量替换
     */
    fun resolveMap(
        templateMap: Map<String, String>,
        variables: Map<String, String>
    ): Map<String, String> {
        return templateMap.mapValues { (_, v) -> resolve(v, variables) }
    }

    /**
     * 应用 varMap 映射：将 source 变量值（经解析后）赋给 target 键
     * @param varMappings 工作流中定义的变量映射列表 (source -> target)
     * @param variables 当前已有变量
     * @return 合并后的变量 Map
     */
    fun applyVarMap(
        varMappings: List<Pair<String, String>>,
        variables: Map<String, String>
    ): Map<String, String> {
        val result = variables.toMutableMap()
        varMappings.forEach { (source, target) ->
            val resolved = resolve(source, variables)
            result[target] = resolved
        }
        return result
    }
}
