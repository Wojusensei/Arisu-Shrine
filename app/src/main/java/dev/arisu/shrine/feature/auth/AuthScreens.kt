package dev.arisu.shrine.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.ToriiWatermark
import dev.arisu.shrine.core.components.ShrineIcons
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.VermilionButton
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.BuildConfig
import dev.arisu.shrine.data.remote.Api
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.launch

private const val MinPasswordLength = 8

private val EmailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

private fun isEmail(s: String) = EmailRegex.matches(s.trim())

@Composable
fun RegisterScreen(
    onRegister: () -> Unit,
    onGoLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var codeBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(countdown) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown -= 1
        }
    }

    val mismatch = confirm.isNotEmpty() && password != confirm
    val canSubmit = nickname.isNotBlank() && isEmail(email) && code.isNotBlank() &&
        password.length >= MinPasswordLength && password == confirm && !busy

    AuthScaffold(modifier = modifier) {
        AuthBrandHeader(subtitle = "建造于人间之里外的飘渺存在")

        Spacer(Modifier.height(30.dp))

        AuthFormCard {
            ServerAddressRow()

            AuthFieldLabel("昵称")
            ArisuTextField(
                value = nickname,
                onValueChange = { nickname = it.take(20) },
                placeholder = "大家在茶话里怎么称呼您",
                leadingIcon = Icons.Rounded.Person,
            )

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("邮箱")
            ArisuTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "outlook / qq / gmail / 163 等任意邮箱",
                leadingIcon = Icons.Rounded.Mail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("验证码")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArisuTextField(
                    value = code,
                    onValueChange = { code = it.take(6) },
                    placeholder = "6 位数字",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.width(10.dp))
                CodeSendButton(
                    enabled = isEmail(email) && countdown == 0 && !codeBusy,
                    countdown = countdown,
                    busy = codeBusy,
                    onClick = {
                        scope.launch {
                            codeBusy = true
                            error = null
                            notice = null
                            try {
                                val devCode = Shrine.sendCode(email.trim(), "register")
                                countdown = 60
                                notice = devCode?.let { "开发模式：验证码 $it（服务器未配置 SMTP，直接填入即可）" }
                                    ?: "验证码已寄出，请到邮箱查收（注意垃圾箱）"
                            } catch (e: Exception) {
                                error = e.toApiMessage()
                            }
                            codeBusy = false
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("密码")
            ArisuTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "至少 $MinPasswordLength 位",
                leadingIcon = Icons.Rounded.Lock,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    PasswordVisibilityToggle(
                        visible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
            )

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("确认密码")
            ArisuTextField(
                value = confirm,
                onValueChange = { confirm = it },
                placeholder = "再输入一次密码",
                leadingIcon = Icons.Rounded.Lock,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (confirmVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    PasswordVisibilityToggle(
                        visible = confirmVisible,
                        onToggle = { confirmVisible = !confirmVisible },
                    )
                },
            )

            if (mismatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "两次输入的密码不一致",
                    style = MaterialTheme.typography.labelSmall,
                    color = Vermilion,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }

        if (notice != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = notice.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AuthErrorText(error)

        Spacer(Modifier.height(16.dp))

        ArisuPrimaryButton(
            text = if (busy) "注册中…" else "注册",
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        Shrine.register(email.trim(), code.trim(), password, nickname.trim())
                        onRegister()
                    } catch (e: Exception) {
                        error = e.toApiMessage()
                    }
                    busy = false
                }
            },
            enabled = canSubmit,
        )

        Spacer(Modifier.height(20.dp))

        AuthTextLink(
            plain = "已有账号？",
            action = "去登录",
            onClick = onGoLogin,
        )

        Spacer(Modifier.height(24.dp))

        AuthFootnote("社员编号依入社先后发放，自 User_000001 起")
    }
}

@Composable
fun LoginScreen(
    onLogin: () -> Unit,
    onGoRegister: () -> Unit,
    onForgot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val vaultAccounts by Shrine.vaultAccounts.collectAsState()

    var switching by remember { mutableStateOf(false) }
    var removingAccount by remember { mutableStateOf<dev.arisu.shrine.data.VaultAccount?>(null) }

    AuthScaffold(modifier = modifier) {
        AuthBrandHeader(subtitle = "建造于人间之里外的飘渺存在")

        Spacer(Modifier.height(30.dp))

        if (vaultAccounts.isNotEmpty()) {
            Text(
                text = "最近登录",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),

                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            ) {
                vaultAccounts.take(5).forEach { account ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                enabled = !switching,
                                onClick = {
                                    scope.launch {
                                        switching = true
                                        val ok = Shrine.switchAccount(account)
                                        if (!ok) {

                                            email = account.email
                                            error = "该账号需要重新登录"
                                        }
                                        switching = false
                                    }
                                },

                                onLongClick = { removingAccount = account },
                            )
                            .padding(4.dp),
                    ) {
                        ArisuAvatar(
                            seed = account.no.removePrefix("User_").toIntOrNull() ?: 0,
                            name = account.nickname,
                            size = 54.dp,
                            url = account.avatarUrl.ifEmpty { null },
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = account.nickname,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 64.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        AuthFormCard {
            ServerAddressRow()

            AuthFieldLabel("邮箱")
            ArisuTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "注册时使用的邮箱",
                leadingIcon = Icons.Rounded.Mail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("密码")
            ArisuTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "请输入密码",
                leadingIcon = Icons.Rounded.Lock,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    PasswordVisibilityToggle(
                        visible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
            )

            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "忘记密码",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onForgot() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        AuthErrorText(error)

        Spacer(Modifier.height(16.dp))

        ArisuPrimaryButton(
            text = if (busy) "登录中…" else "登录",
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        Shrine.login(email.trim(), password)
                        onLogin()
                    } catch (e: Exception) {
                        error = e.toApiMessage()
                    }
                    busy = false
                }
            },
            enabled = isEmail(email) && password.isNotEmpty() && !busy,
        )

        Spacer(Modifier.height(20.dp))

        AuthTextLink(
            plain = "没有账号？",
            action = "去注册",
            onClick = onGoRegister,
        )

        Spacer(Modifier.height(24.dp))

        AuthFootnote("社员编号依入社先后发放，自 User_000001 起")
    }

    removingAccount?.let { target ->
        dev.arisu.shrine.core.components.ArisuConfirmDialog(
            title = "删除这个账号记录？",
            body = "删除后这台设备不再保存它，下次使用需要重新登录。",
            confirmText = "删除",
            onConfirm = {
                Shrine.removeVaultAccount(target)
                if (Shrine.me.value?.id == target.no) Shrine.logout()
                removingAccount = null
            },
            onDismiss = { removingAccount = null },
        )
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var codeBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(countdown) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown -= 1
        }
    }

    val canSubmit = isEmail(email) && code.isNotBlank() &&
        password.length >= MinPasswordLength && password == confirm && !busy

    AuthScaffold(modifier = modifier) {
        AuthBrandHeader(subtitle = "验证码寄到邮箱，重设即可")

        Spacer(Modifier.height(30.dp))

        AuthFormCard {
            AuthFieldLabel("邮箱")
            ArisuTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = "注册时使用的邮箱",
                leadingIcon = Icons.Rounded.Mail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("验证码")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArisuTextField(
                    value = code,
                    onValueChange = { code = it.take(6) },
                    placeholder = "6 位数字",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.width(10.dp))
                CodeSendButton(
                    enabled = isEmail(email) && countdown == 0 && !codeBusy,
                    countdown = countdown,
                    busy = codeBusy,
                    onClick = {
                        scope.launch {
                            codeBusy = true
                            error = null
                            try {
                                val devCode = Shrine.sendCode(email.trim(), "reset")
                                countdown = 60
                                if (devCode != null) notice = "开发模式：验证码 $devCode（直接填入即可）"
                            } catch (e: Exception) {
                                error = e.toApiMessage()
                            }
                            codeBusy = false
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("新密码")
            ArisuTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = "至少 $MinPasswordLength 位",
                leadingIcon = Icons.Rounded.Lock,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    PasswordVisibilityToggle(
                        visible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
            )

            Spacer(Modifier.height(14.dp))

            AuthFieldLabel("确认新密码")
            ArisuTextField(
                value = confirm,
                onValueChange = { confirm = it },
                placeholder = "再输入一次新密码",
                leadingIcon = Icons.Rounded.Lock,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (confirmVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    PasswordVisibilityToggle(
                        visible = confirmVisible,
                        onToggle = { confirmVisible = !confirmVisible },
                    )
                },
            )
        }

        if (notice != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = notice.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (done) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "密码已重置，用新密码登录吧",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        AuthErrorText(error)

        Spacer(Modifier.height(16.dp))

        ArisuPrimaryButton(
            text = if (busy) "提交中…" else if (done) "去登录" else "重置密码",
            onClick = {
                if (done) {
                    onBack()
                } else {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            Shrine.resetPassword(email.trim(), code.trim(), password)
                            done = true
                        } catch (e: Exception) {
                            error = e.toApiMessage()
                        }
                        busy = false
                    }
                }
            },
            enabled = canSubmit || done,
        )

        Spacer(Modifier.height(20.dp))

        AuthTextLink(
            plain = "想起来密码了？",
            action = "去登录",
            onClick = onBack,
        )
    }
}

@Composable
private fun ServerAddressRow() {
    if (!BuildConfig.DEBUG) return
    var expanded by remember { mutableStateOf(false) }

    val saved by Shrine.baseUrl.collectAsState()

    val effective = saved.ifEmpty { Api.DEFAULT_BASE_URL }
    var value by rememberSaveable { mutableStateOf(saved) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "服务器",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = effective.ifEmpty { "未设置（点击填写）" },
                style = MaterialTheme.typography.labelSmall,
                color = if (effective.isEmpty()) Vermilion else InkMuted,
                maxLines = 1,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                ArisuTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "http://192.168.1.100:8080",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "保存后即可注册登录。手机需与服务器在同一网络。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (saving) "保存中…" else "保存",
                        style = MaterialTheme.typography.labelMedium,
                        color = Vermilion,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                scope.launch {
                                    saving = true
                                    Shrine.saveBaseUrl(value)
                                    saving = false
                                    expanded = false
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CodeSendButton(
    enabled: Boolean,
    countdown: Int,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val label = when {
        busy -> "寄送中"
        countdown > 0 -> "${countdown}s"
        else -> "发送验证码"
    }
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .neuInset(shape = RoundedCornerShape(12.dp), color = dev.arisu.shrine.core.design.NeuInset)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            YinYangOrb(size = 16.dp, rotating = false)
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) Vermilion else InkMuted,
            )
        }
    }
}

@Composable
private fun AuthErrorText(error: String?) {
    if (error != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = Vermilion,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AuthScaffold(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {

        Icon(
            imageVector = ShrineIcons.Torii,
            contentDescription = null,
            tint = InkFaint.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(200.dp),
        )

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 27.dp, vertical = 26.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()

                    .verticalScroll(rememberScrollState())
                    .heightIn(min = this@BoxWithConstraints.maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
private fun AuthBrandHeader(subtitle: String) {
    val shape = RoundedCornerShape(18.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp)
                .accentRaised(shape = shape, elevation = 12.dp, color = VermilionButton),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ShrineIcons.Torii,
                contentDescription = "有栖神社",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "有栖神社",
            style = MaterialTheme.typography.headlineMedium,
            color = InkTitle,
            letterSpacing = 4.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthFormCard(content: @Composable ColumnScope.() -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        depth = 5.dp,
        blur = 10.dp,
    ) {
        ToriiWatermark(RoundedCornerShape(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(22.dp),
            content = content,
        )
    }
}

@Composable
private fun AuthFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = InkMuted,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, bottom = 6.dp),
    )
}

@Composable
private fun PasswordVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    Icon(
        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
        contentDescription = if (visible) "隐藏密码" else "显示密码",
        tint = InkMuted,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .clickable { onToggle() },
    )
}

@Composable
private fun AuthFootnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = InkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthTextLink(
    plain: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = plain,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
        )
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            color = Vermilion,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
