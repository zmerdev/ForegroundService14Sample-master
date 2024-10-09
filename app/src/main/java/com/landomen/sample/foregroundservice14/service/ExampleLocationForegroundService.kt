package com.landomen.sample.foregroundservice14.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.landomen.sample.foregroundservice14.notification.NotificationsHelper
import de.proglove.sdk.PgManager
import de.proglove.sdk.scanner.BarcodeScanResults
import de.proglove.sdk.scanner.IScannerOutput
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ExampleLocationForegroundService : Service(), IScannerOutput {
    private val binder = LocalBinder()
    private var scannerConnected = false
    private val coroutineScope = CoroutineScope(Job())
    private var timerJob: Job? = null
    val pgManager: PgManager = PgManager()

    // WebSocket session management
    private val sessions = mutableListOf<DefaultWebSocketSession>()
    private val sessionMutex = Mutex()

    // Ktor WebSocket server
    private val webSocketServer = embeddedServer(Netty, port = 8080) {
        install(WebSockets)
        install(ContentNegotiation) {
            json()
        }
        routing {
            webSocket("/scan") {
                sessionMutex.withLock {
                    sessions.add(this) // Add session to the list when a client connects
                }
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            // Handle incoming frames if necessary
                        }
                    }
                } finally {
                    sessionMutex.withLock {
                        sessions.remove(this) // Remove session when the client disconnects
                    }
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ExampleLocationForegroundService = this@ExampleLocationForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        startAsForegroundService()
        startLocationUpdates()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // Start the WebSocket server
        webSocketServer.start()

        // Scanner subscriptions and connection
        val result = pgManager.ensureConnectionToService(this.applicationContext)
        if (!result) throw Exception("ahhhh")
        pgManager.subscribeToScans(this)
        pgManager.startPairing()

        Toast.makeText(this, "Foreground Service created", Toast.LENGTH_SHORT).show()

        startServiceRunningTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        timerJob?.cancel()
        coroutineScope.coroutineContext.cancelChildren()

        // Stop the WebSocket server
        webSocketServer.stop(1000, 1000)

        Toast.makeText(this, "Foreground Service destroyed", Toast.LENGTH_SHORT).show()
    }

    private fun startAsForegroundService() {
        // create the notification channel
        NotificationsHelper.createNotificationChannel(this)

        // promote service to foreground service
        ServiceCompat.startForeground(
            this,
            1,
            NotificationsHelper.buildNotification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    fun stopForegroundService() {
        stopSelf()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
    }

    private fun startServiceRunningTicker() {
        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            tickerFlow()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ExampleLocationForegroundService,
                            "Foreground Service still running!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun tickerFlow(
        period: Duration = TICKER_PERIOD_SECONDS,
        initialDelay: Duration = TICKER_PERIOD_SECONDS
    ) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    companion object {
        private const val TAG = "ExampleForegroundService"
        private val LOCATION_UPDATES_INTERVAL_MS = 1.seconds.inWholeMilliseconds
        private val TICKER_PERIOD_SECONDS = 10.seconds
    }

    override fun onBarcodeScanned(barcodeScanResults: BarcodeScanResults) {
        if (barcodeScanResults.symbology!!.isNotEmpty()) {
            Log.d("scanner", "Got barcode: ${barcodeScanResults.barcodeContent} with symbology: ${barcodeScanResults.symbology}")
            coroutineScope.launch {
                broadcastBarcode(barcodeScanResults.barcodeContent)
            }
        } else {
            Log.d("scanner", "Got barcode: ${barcodeScanResults.barcodeContent}")
            coroutineScope.launch {
                broadcastBarcode(barcodeScanResults.barcodeContent)
            }
        }
    }

    override fun onScannerConnected() {
        scannerConnected = true
        Log.d("scanner", "scanner connected")
        Log.d("scanner", "Is connected? : ${pgManager.isConnectedToScanner()}")
    }

    override fun onScannerDisconnected() {
        scannerConnected = false
        Log.d("scanner", "scanner disconnected")
    }

    private suspend fun broadcastBarcode(barcode: String) {
        sessionMutex.withLock {
            sessions.forEach { session ->
                try {
                    session.send("Barcode: $barcode")
                } catch (e: Exception) {
                    e.printStackTrace() // Handle exceptions like broken pipe
                }
            }
        }
    }
}
