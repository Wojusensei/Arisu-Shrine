package dev.arisu.shrine.data

import dev.arisu.shrine.data.remote.Api
import dev.arisu.shrine.data.remote.AuthResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginParseTest {

    @Test
    fun loginResponseParses() {
        val raw = """{"access_token":"abc","refresh_token":"def","me":{
            "id":14,"no":"User_000014","nickname":"解析验证","role":"member","avatar":"",
            "following":false,"signature":"","status_emoji":"","status_text":"","blog_url":"",
            "custom_markdown":"","custom_css":"","joined_at":1790588066,"online_minutes":0,
            "following_count":0,"follower_count":0,"post_count":0,"email":"dbg3@arisu.dev",
            "fixed_avatar_url":"","active_avatar_url":"","active_avatar_enabled":true,
            "show_fields":{},"notify_scope":{"follow_avatar":false,"follow_nickname":true},
            "muted_until":null,"deactivated_at":null}}"""
        val dto = Api.json.decodeFromString(AuthResultDto.serializer(), raw)
        assertEquals("abc", dto.accessToken)
        assertEquals("User_000014", dto.me?.no)
        assertNull(dto.me?.mutedUntil)
    }
}
