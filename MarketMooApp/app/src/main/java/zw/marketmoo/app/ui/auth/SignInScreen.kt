package zw.marketmoo.app.ui.auth

import zw.marketmoo.app.ui.components.HeroScene
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.net.ApiResult
import zw.marketmoo.app.data.sync.SyncWorker

/**
 * Phone number plus either a one-time SMS code or a password set by a manager. No national ID. Browsing, records and listings work
 * without signing in; sign-in is what lets the app sync them to the server.
 */
@Composable
fun SignInScreen(sl: ServiceLocator, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) } // 0 phone, 1 code
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var devCode by remember { mutableStateOf<String?>(null) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState())) {
    HeroScene(Modifier.fillMaxWidth(), height = 190.dp) {
        Column(Modifier.padding(start = 22.dp, bottom = 40.dp)) {
            Text("Welcome back", color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
            Text("Sign in with your phone number", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f), fontSize = 15.sp)
        }
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Use your phone number. We send a 6-digit code by SMS, or use the Password tab if a manager gave you one. No ID number is needed. " +
                "You can use MarketMoo without signing in; signing in lets your records and listings sync.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!usePassword) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Text code") } else OutlinedButton(onClick = { usePassword = false; message = null }, modifier = Modifier.weight(1f)) { Text("Text code") }
            if (usePassword) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Password") } else OutlinedButton(onClick = { usePassword = true; step = 0; message = null }, modifier = Modifier.weight(1f)) { Text("Password") }
        }
        if (usePassword) {
            Text("Your manager can create an account for you and give you a password. Enter it here.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone number (for example 0771234567)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(enabled = !busy && phone.filter { it.isDigit() }.length >= 9 && password.length >= 8, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; message = null
                scope.launch {
                    when (val r = sl.api.passwordLogin(phone, password)) {
                        is ApiResult.Ok -> { sl.session.signIn(r.value.token, r.value.profile); SyncWorker.enqueue(ctx); onDone() }
                        is ApiResult.Failure -> message = if (r.offline) "No connection to the server. Check your signal or the server address in Account." else r.message
                    }
                    busy = false
                }
            }) { Text(if (busy) "Checking..." else "Sign in") }
        } else if (step == 0) {
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone number (for example 0771234567)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(consent, { consent = it })
                Text("MarketMoo may store my phone number and district to run the service. I can delete my data at any time.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(enabled = !busy && phone.filter { it.isDigit() }.length >= 9 && consent, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; message = null
                scope.launch {
                    when (val r = sl.api.otpRequest(phone)) {
                        is ApiResult.Ok -> { devCode = r.value.devCode; step = 1 }
                        is ApiResult.Failure -> message = if (r.offline) "No connection to the server. Check your signal or the server address in Account." else r.message
                    }
                    busy = false
                }
            }) { Text(if (busy) "Sending..." else "Send code") }
        } else {
            Text("Enter the code sent to $phone.", style = MaterialTheme.typography.bodyMedium)
            devCode?.let { Text("Test server: your code is $it (shown only when the server runs in development mode).", color = MaterialTheme.colorScheme.secondary) }
            OutlinedTextField(code, { if (it.length <= 6) code = it.filter(Char::isDigit) }, label = { Text("6-digit code") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; message = null
                scope.launch {
                    when (val r = sl.api.otpVerify(phone, code, consent)) {
                        is ApiResult.Ok -> { sl.session.signIn(r.value.token, r.value.profile); SyncWorker.enqueue(ctx); onDone() }
                        is ApiResult.Failure -> message = if (r.offline) "No connection to the server." else r.message
                    }
                    busy = false
                }
            }) { Text(if (busy) "Checking..." else "Sign in") }
            TextButton(onClick = { step = 0; code = ""; message = null }) { Text("Use a different number") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
    }
    }
}
