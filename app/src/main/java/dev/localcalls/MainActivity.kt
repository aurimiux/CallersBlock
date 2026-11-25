package dev.localcalls

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so role/permission status is re-read after returning from Settings. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen(resumeTick) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Recomposes whenever the prefs file changes (tile toggle, new blocked call, etc.). */
@Composable
private fun rememberPrefsVersion(ctx: Context): Int {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(ctx) {
        val sp = Prefs.get(ctx)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return version
}

@Composable
private fun MainScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val roleManager = remember { ctx.getSystemService(RoleManager::class.java) }
    var refresh by remember { mutableIntStateOf(0) }
    val prefsVersion = rememberPrefsVersion(ctx)

    val roleAvailable = remember(resumeTick) {
        roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
    }
    val roleHeld = remember(resumeTick, refresh) {
        roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }
    val contactsGranted = remember(resumeTick, refresh) {
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    val enabled = remember(prefsVersion) { Prefs.isEnabled(ctx) }
    val blocked = remember(prefsVersion) { Prefs.blockedLog(ctx) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    val prefixes = remember(prefsVersion) { Prefs.allowedPrefixes(ctx) }
    var newPrefix by rememberSaveable { mutableStateOf("") }
    var prefixError by rememberSaveable { mutableStateOf<String?>(null) }
    var trunk by rememberSaveable { mutableStateOf(Prefs.trunkPrefixesRaw(ctx)) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Local Calls Only", style = MaterialTheme.typography.headlineMedium)

        // 1. Call screening role
        SectionCard {
            Text("Call screening", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !roleAvailable -> "This device doesn't offer the call screening role."
                    roleHeld -> "✓ This app is your call screening app."
                    else -> "✗ Not set as call screening app — calls can't be blocked yet."
                },
                color = if (roleHeld) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (roleAvailable && !roleHeld) {
                Button(onClick = {
                    roleLauncher.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }) { Text("Set as call screening app") }
            }
        }

        // 2. Contacts permission
        SectionCard {
            Text("Contacts", style = MaterialTheme.typography.titleMedium)
            Text(
                "Contacts are only read on this phone, to let your saved foreign contacts ring. " +
                    "Nothing ever leaves the device — this app has no internet permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (contactsGranted) {
                Text("✓ Contacts access granted", color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                    Text("Allow contacts access")
                }
            }
        }

        // 3. Master switch
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Block foreign calls", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "On — same as the Quick Settings tile" else "Off — all calls ring",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { Prefs.setEnabled(ctx, it) })
            }
        }

        // 4. Allowed prefixes
        SectionCard {
            Text("Allowed prefixes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only numbers starting with one of these ring (plus contacts, short and national numbers).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (prefixes.isEmpty()) {
                Text(
                    "No prefixes — every +number outside your contacts will be blocked.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            prefixes.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(p, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Prefs.removeAllowedPrefix(ctx, p) }) { Text("Remove") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPrefix,
                    onValueChange = { newPrefix = it; prefixError = null },
                    label = { Text("Add prefix, e.g. +371") },
                    isError = prefixError != null,
                    supportingText = prefixError?.let { err -> { Text(err) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val p = PhoneRules.normalizeCountryCode(newPrefix)
                    prefixError = when {
                        p.length !in 2..PhoneRules.MAX_PREFIX_LENGTH -> "Use + and 1–7 digits"
                        p in prefixes -> "$p is already allowed"
                        !Prefs.addAllowedPrefix(ctx, p) -> "Couldn't add $p"
                        else -> null
                    }
                    if (prefixError == null) newPrefix = ""
                }) { Text("Add") }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = trunk,
                onValueChange = {
                    trunk = it
                    Prefs.setTrunkPrefixesRaw(ctx, it)
                },
                label = { Text("Local trunk prefixes") },
                supportingText = {
                    Text(
                        "Comma separated, e.g. 0, 8 (national-format numbers without +). " +
                            "Short numbers up to ${PhoneRules.MAX_SHORT_NUMBER_LENGTH} digits always ring."
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 5. Recently blocked
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recently blocked", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (blocked.isNotEmpty()) {
                    TextButton(onClick = { Prefs.clearBlocked(ctx) }) { Text("Clear") }
                }
            }
            if (blocked.isEmpty()) {
                Text("No blocked calls yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                blocked.forEachIndexed { i, call ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(call.number, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text(fmt.format(Date(call.timestamp)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.padding(8.dp))
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
