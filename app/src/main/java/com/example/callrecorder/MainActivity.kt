package com.example.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }
}

private fun requiredPermissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
    return list.toTypedArray()
}

@Composable
private fun Screen() {
    val ctx = LocalContext.current

    fun allGranted() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var denied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(listFiles(ctx)) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        denied = !granted
    }

    DisposableEffect(Unit) { onDispose { player?.release() } }

    Column(
        Modifier.fillMaxSize().padding(16.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Call Recorder", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Call eka speakerphone eke damma witharai dedenage voice record wenne. " +
            "Anith aya ta kiyala record karanna.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!granted) {
            Text("Wada karanna permissions oni: Microphone, Phone state" +
                if (Build.VERSION.SDK_INT >= 33) ", Notifications" else "")
            Button(onClick = { launcher.launch(requiredPermissions()) }) {
                Text("Permissions allow karanna")
            }
            if (denied) {
                Text("Permission deny wela. Settings walin allow karanna.",
                    color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null))
                    )
                }) { Text("App settings open karanna") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (running) "Monitoring ON" else "Monitoring OFF", Modifier.weight(1f))
                Switch(checked = running, onCheckedChange = { on ->
                    running = on
                    val i = Intent(ctx, RecorderService::class.java)
                    if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
                })
            }
            OutlinedButton(onClick = { files = listFiles(ctx) }) { Text("List refresh") }

            if (files.isEmpty()) Text("Recordings nehe.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { f ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium)
                            Text("${f.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    player?.release()
                                    player = MediaPlayer().apply {
                                        setDataSource(f.absolutePath)
                                        prepare(); start()
                                    }
                                }) { Text("Play") }
                                TextButton(onClick = { player?.stop() }) { Text("Stop") }
                                TextButton(onClick = {
                                    f.delete(); files = listFiles(ctx)
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun listFiles(ctx: android.content.Context): List<File> =
    RecorderService.recordingsDir(ctx).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
