package com.example.smsforwarder

import com.example.smsforwarder.core.config.model.MatchingRule
import com.example.smsforwarder.core.config.model.MatchingRuleType
import com.example.smsforwarder.core.engine.RuleMatcher
import com.example.smsforwarder.core.sms.model.SmsMessage

class SmsMessageTest {

    @Test
    fun testSmsMessageCreation() {
        val sms = SmsMessage(
            sender = "13800138000",
            content = "您的验证码是：123456，请在5分钟内使用。",
            timestamp = 1640995200000L
        )

        assertEquals("13800138000", sms.sender)
        assertEquals("您的验证码是：123456，请在5分钟内使用。", sms.content)
        assertEquals(1640995200000L, sms.timestamp)
        assertFalse(sms.isRead)
        assertFalse(sms.isMms)
    }

    @Test
    fun testFromIntent() {
        val sms = SmsMessage.Companion.fromIntent("13800138000", "验证码：888888")
        assertNotNull(sms)
        assertEquals("13800138000", sms!!.sender)
        assertEquals("验证码：888888", sms.content)
    }

    @Test
    fun testFromIntentWithNull() {
        val sms1 = SmsMessage.Companion.fromIntent(null, "验证码：888888")
        assertNull(sms1)

        val sms2 = SmsMessage.Companion.fromIntent("13800138000", null)
        assertNull(sms2)
    }

    @Test
    fun testIsFrom() {
        val sms = SmsMessage.Companion.createTestMessage(sender = "13800138000")
        assertTrue(sms.isFrom("13800138000"))
        assertFalse(sms.isFrom("13900139000"))
    }

    @Test
    fun testContains() {
        val sms = SmsMessage.Companion.createTestMessage(content = "您的验证码是：123456")
        assertTrue(sms.contains("验证码"))
        assertTrue(sms.contains("验证码", ignoreCase = true))
        assertFalse(sms.contains("密码"))
    }

    @Test
    fun testGetSummary() {
        val longContent = "这是一条非常长的短信内容，需要被截断显示摘要信息。"
        val sms = SmsMessage.Companion.createTestMessage(content = longContent)

        val summary = sms.getSummary(10)
        assertEquals("这是一条非常长的短信内容，需要被截断显示摘要信息。".substring(0, 10) + "...", summary)

        val shortContent = "短内容"
        val sms2 = SmsMessage.Companion.createTestMessage(content = shortContent)
        assertEquals(shortContent, sms2.getSummary(50))
    }

    @Test
    fun testPhonePrefixMatching() {
        val rule = MatchingRule(
            id = "bank_sender",
            name = "银行号码匹配",
            type = MatchingRuleType.PHONE_PREFIX,
            phone = "955*",
            variables = emptyMap()
        )

        // 匹配银行号码
        val bankSms = SmsMessage.Companion.createTestMessage(sender = "95588")
        val result1 = RuleMatcher.match(bankSms, rule)
        assertTrue(result1.isMatched)
        assertEquals("号码前缀匹配成功", result1.message)
        assertEquals("95588", result1.variables["_phone"])

        // 不匹配其他号码
        val otherSms = SmsMessage.Companion.createTestMessage(sender = "13800138000")
        val result2 = RuleMatcher.match(otherSms, rule)
        assertFalse(result2.isMatched)
        assertEquals("号码前缀不匹配", result2.message)
    }

    @Test
    fun testPhoneRegexMatching() {
        val rule = MatchingRule(
            id = "china_mobile",
            name = "中国移动号码匹配",
            type = MatchingRuleType.PHONE_REGEX,
            phone = "^1(3[4-9]|4[7]|5[0-27-9]|7[28]|8[2-478]|9[8])\\d{8}$",
            variables = mapOf(
                "operator" to "中国移动"
            )
        )

        // 匹配中国移动号码
        val mobileSms = SmsMessage.Companion.createTestMessage(sender = "13900139000")
        val result1 = RuleMatcher.match(mobileSms, rule)
        assertTrue(result1.isMatched)
        assertEquals("中国移动", result1.variables["operator"])

        // 不匹配联通号码
        val unicomSms = SmsMessage.Companion.createTestMessage(sender = "13000130000")
        val result2 = RuleMatcher.match(unicomSms, rule)
        assertFalse(result2.isMatched)
    }

    @Test
    fun testContentRegexMatching() {
        val rule = MatchingRule(
            id = "verification_code",
            name = "验证码提取",
            type = MatchingRuleType.CONTENT_REGEX,
            content = "验证码[:：]\\s*(\\d{4,8})",
            variables = mapOf(
                "code" to "$1"
            )
        )

        // 匹配包含验证码的短信
        val sms1 = SmsMessage.Companion.createTestMessage(content = "验证码：123456")
        val result1 = RuleMatcher.match(sms1, rule)
        assertTrue(result1.isMatched)
        assertEquals("123456", result1.variables["code"])

        // 匹配中文冒号
        val sms2 = SmsMessage.Companion.createTestMessage(content = "验证码：888888")
        val result2 = RuleMatcher.match(sms2, rule)
        assertTrue(result2.isMatched)
        assertEquals("888888", result2.variables["code"])

        // 不匹配无验证码的短信
        val sms3 = SmsMessage.Companion.createTestMessage(content = "这是一条普通短信")
        val result3 = RuleMatcher.match(sms3, rule)
        assertFalse(result3.isMatched)
    }

    @Test
    fun testComplexContentRegexMatching() {
        val rule = MatchingRule(
            id = "verification_with_timeout",
            name = "带超时的验证码提取",
            type = MatchingRuleType.CONTENT_REGEX,
            content = "验证码[:：]\\s*(\\d{4,8}).*?(\\d{1,2})分钟",
            variables = mapOf(
                "code" to "$1",
                "timeout" to "$2"
            )
        )

        val sms = SmsMessage.Companion.createTestMessage(
            content = "您的验证码是：123456，请在5分钟内使用。"
        )

        val result = RuleMatcher.match(sms, rule)
        assertTrue(result.isMatched)
        assertEquals("123456", result.variables["code"])
        assertEquals("5", result.variables["timeout"])
    }

    @Test
    fun testMatchAll() {
        val rules = listOf(
            MatchingRule(
                id = "rule1",
                name = "规则1",
                type = MatchingRuleType.PHONE_PREFIX,
                phone = "955*"
            ),
            MatchingRule(
                id = "rule2",
                name = "规则2",
                type = MatchingRuleType.CONTENT_REGEX,
                content = "验证码.*"
            )
        )

        // 同时匹配两个规则
        val sms = SmsMessage.Companion.createTestMessage(
            sender = "95588",
            content = "验证码：123456"
        )

        val results = RuleMatcher.matchAll(sms, rules)
        assertEquals(2, results.size)

        // 只匹配内容规则
        val sms2 = SmsMessage.Companion.createTestMessage(
            sender = "13800138000",
            content = "验证码：123456"
        )

        val results2 = RuleMatcher.matchAll(sms2, rules)
        assertEquals(1, results2.size)
        assertEquals("rule2", results2[0].first.id)
    }

    @Test
    fun testFindFirstMatch() {
        val rules = listOf(
            MatchingRule(
                id = "rule1",
                name = "规则1",
                type = MatchingRuleType.PHONE_PREFIX,
                phone = "955*"
            ),
            MatchingRule(
                id = "rule2",
                name = "规则2",
                type = MatchingRuleType.CONTENT_REGEX,
                content = "验证码.*"
            )
        )

        val sms = SmsMessage.Companion.createTestMessage(
            sender = "95588",
            content = "普通短信"
        )

        val result = RuleMatcher.findFirstMatch(sms, rules)
        assertNotNull(result)
        assertEquals("rule1", result!!.first.id)
    }

    @Test
    fun testInvalidRule() {
        val invalidRule = MatchingRule(
            id = "invalid",
            name = "无效规则",
            type = MatchingRuleType.CONTENT_REGEX,
            content = ""  // 空内容，无效规则
        )

        val sms = SmsMessage.Companion.createTestMessage()
        val result = RuleMatcher.match(sms, invalidRule)
        assertFalse(result.isMatched)
        assertEquals("规则配置无效", result.message)
    }

    @Test
    fun testInvalidRegex() {
        val invalidRule = MatchingRule(
            id = "invalid_regex",
            name = "无效正则",
            type = MatchingRuleType.CONTENT_REGEX,
            content = "["  // 无效的正则表达式
        )

        val sms = SmsMessage.Companion.createTestMessage()
        val result = RuleMatcher.match(sms, invalidRule)
        assertFalse(result.isMatched)
        assertTrue(result.message.contains("正则表达式无效"))
    }
}