package dev.arisu.shrine.feature.share

import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 卡面身份只从传入的当前社员取，两个账号各拿各的，不共用任何全局字段。 */
class FortuneCardIdentityTest {

    private fun user(
        no: String,
        nickname: String,
        fixedAvatar: String? = null,
        activeAvatar: String? = null,
        activeEnabled: Boolean = true,
    ) = User(
        id = no,
        nickname = nickname,
        role = Role.MEMBER,
        fixedAvatarUrl = fixedAvatar,
        activeAvatarUrl = activeAvatar,
        activeAvatarEnabled = activeEnabled,
    )

    @Test
    fun eachUserGetsOwnNameNoAndAvatar() {
        val first = user(
            no = "User_000001",
            nickname = "甲",
            fixedAvatar = "https://a/fixed-1.png",
            activeAvatar = "https://a/active-1.png",
        )
        val second = user(
            no = "User_000002",
            nickname = "乙",
            fixedAvatar = "https://a/fixed-2.png",
            activeEnabled = false,
        )

        val card1 = fortuneCardData(first, 59, "中吉", "2026.11.10")
        val card2 = fortuneCardData(second, 88, "大吉", "2026.11.10")

        assertEquals("User_000001", card1.userNo)
        assertEquals("甲", card1.userName)
        assertEquals("https://a/active-1.png", card1.avatarUrl)
        assertEquals("User_000002", card2.userNo)
        assertEquals("乙", card2.userName)
        assertEquals("https://a/fixed-2.png", card2.avatarUrl)
        assertEquals(59, card1.luck)
        assertEquals("大吉", card2.band)
        // 同一天两个账号分享，缓存文件名也不能撞，否则会把别人的卡发出去
        assertNotEquals(FortuneCard.fileId(card1), FortuneCard.fileId(card2))
    }

    @Test
    fun missingUserLeavesBlankIdentity() {
        val card = fortuneCardData(null, 30, "末吉", "2026.11.10")
        assertEquals("", card.userName)
        assertEquals("", card.userNo)
        assertNull(card.avatarUrl)
    }

    @Test
    fun streakAndOptionsReachTheCard() {
        val options = FortuneCardOptions(showQr = true)
        val card = fortuneCardData(
            user(no = "User_000001", nickname = "甲"),
            luck = 59,
            band = "中吉",
            date = "2026.11.10",
            streak = 7,
            options = options,
            style = FortuneCardStyle.PAPER,
        )
        assertEquals(7, card.streak)
        assertTrue(card.options.showQr)
        assertEquals(FortuneCardStyle.PAPER, card.style)
    }
}
