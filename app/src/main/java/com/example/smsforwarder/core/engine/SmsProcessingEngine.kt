package com.example.smsforwarder.core.engine

import android.util.Log
import com.example.smsforwarder.core.config.ConfigManager
import com.example.smsforwarder.core.config.model.AppConfig
import com.example.smsforwarder.core.sms.model.SmsMessage
import com.example.smsforwarder.pusher.base.PusherFactory
import com.example.smsforwarder.pusher.base.PusherResult
import com.example.smsforwarder.storage.EventLog
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "SmsProcessingEngine"
private const val WORKFLOW_TIMEOUT_MS = 30_000L

/**
 * SMS 处理引擎：短信 → 规则匹配 → 变量解析 → 推送
 */
class SmsProcessingEngine(private val configManager: ConfigManager) {

    /**
     * 处理一条短信，遍历所有已启用工作流
     */
    suspend fun process(sms: SmsMessage) {
        val config = configManager.getConfig()
        val enabledWorkflows = config.enabledWorkflows()

        if (enabledWorkflows.isEmpty()) {
            Log.d(TAG, "没有已启用的工作流，跳过处理")
            EventLog.add("收到短信 [${sms.sender.maskPhone()}]，无已启用工作流")
            return
        }

        EventLog.add("收到短信 [${sms.sender.maskPhone()}]，内容长度=${sms.content.length}")

        for (workflow in enabledWorkflows) {
            try {
                withTimeout(WORKFLOW_TIMEOUT_MS) {
                    runWorkflow(sms, workflow, config)
                }
            } catch (e: TimeoutCancellationException) {
                val msg = "工作流 [${workflow.id}] 执行超时"
                Log.w(TAG, msg)
                EventLog.add(msg)
            } catch (e: Exception) {
                val msg = "工作流 [${workflow.id}] 异常: ${e.message}"
                Log.e(TAG, msg, e)
                EventLog.add(msg)
            }
        }
    }

    private suspend fun runWorkflow(
        sms: SmsMessage,
        workflow: com.example.smsforwarder.core.config.model.Workflow,
        config: AppConfig
    ) {
        // 1. 找到匹配规则
        val matchingRule = config.findMatchingRule(workflow.matching)
        if (matchingRule == null) {
            Log.w(TAG, "工作流 [${workflow.id}] 找不到匹配规则: ${workflow.matching}")
            return
        }

        // 2. 执行规则匹配
        val matchResult = RuleMatcher.match(sms, matchingRule)
        if (!matchResult.isMatched) {
            Log.d(TAG, "工作流 [${workflow.id}] 未命中: ${matchResult.message}")
            return
        }

        Log.i(TAG, "工作流 [${workflow.id}] 命中: ${matchResult.message}")
        EventLog.add("工作流 [${workflow.name}] 命中，开始推送")

        // 3. 遍历推送器
        for (workflowPusher in workflow.pushers) {
            val pushRule = config.findPushRule(workflowPusher.id)
            if (pushRule == null) {
                Log.w(TAG, "推送规则不存在: ${workflowPusher.id}")
                continue
            }
            if (!pushRule.enabled) {
                Log.d(TAG, "推送规则 [${pushRule.id}] 已禁用，跳过")
                continue
            }

            // 4. 应用变量映射
            val varMappings = workflowPusher.varMap.mappings.map { it.source to it.target }
            val resolvedVars = VariableResolver.applyVarMap(varMappings, matchResult.variables)

            // 5. 创建并执行推送器
            val pusher = PusherFactory.create(pushRule)
            if (pusher == null) {
                Log.w(TAG, "无法创建推送器: ${pushRule.type}")
                continue
            }

            val result: PusherResult = pusher.push(resolvedVars)
            when (result) {
                is PusherResult.Success -> {
                    EventLog.add("  ✓ 推送 [${pushRule.name}] 成功: ${result.message}")
                }
                is PusherResult.Failure -> {
                    EventLog.add("  ✗ 推送 [${pushRule.name}] 失败: ${result.error}")
                }
            }
        }
    }

    /** 手机号脱敏：保留前3后2位 */
    private fun String.maskPhone(): String {
        return if (length > 5) "${take(3)}****${takeLast(2)}" else "****"
    }
}
