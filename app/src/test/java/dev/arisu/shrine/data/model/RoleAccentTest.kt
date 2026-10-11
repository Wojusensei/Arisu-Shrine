package dev.arisu.shrine.data.model

import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.Indigo
import dev.arisu.shrine.core.design.Vermilion
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleAccentTest {

    @Test
    fun kannushiIsGold() {
        assertEquals(Gold, Role.KANNUSHI.accent)
    }

    @Test
    fun guujiIsVermilion() {
        assertEquals(Vermilion, Role.GUUJI.accent)
    }

    @Test
    fun houshiAndTempHoushiShareBlue() {
        assertEquals(Role.HOUSHI.accent, Role.TEMP_HOUSHI.accent)
        assertEquals(Indigo, Role.HOUSHI.accent)
    }

    @Test
    fun memberIsGrey() {
        assertEquals(InkFaint, Role.MEMBER.accent)
    }
}
