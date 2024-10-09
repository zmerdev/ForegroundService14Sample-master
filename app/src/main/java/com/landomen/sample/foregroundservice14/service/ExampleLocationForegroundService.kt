package com.landomen.sample.foregroundservice14.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.landomen.sample.foregroundservice14.notification.NotificationsHelper
import de.proglove.sdk.PgManager
import de.proglove.sdk.scanner.BarcodeScanResults
import de.proglove.sdk.scanner.IScannerOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Simple foreground service that shows a notification to the user and provides location updates.
 */
class ExampleLocationForegroundService : Service(), IScannerOutput {
    private val binder = LocalBinder()
    private var scannerConnected = false
    private val coroutineScope = CoroutineScope(Job())
    private var timerJob: Job? = null
    val pgManager: PgManager = PgManager()

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

        //scanner subscriptions and connection
        val result = pgManager.ensureConnectionToService(this.applicationContext)
        if(!result) throw Exception("ahhhh")
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

        Toast.makeText(this, "Foreground Service destroyed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Promotes the service to a foreground service, showing a notification to the user.
     *
     * This needs to be called within 10 seconds of starting the service or the system will throw an exception.
     */
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

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }



    /**
     * Starts the location updates using the FusedLocationProviderClient.
     */
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
    }

    /**
     * Starts a ticker that shows a toast every [TICKER_PERIOD_SECONDS] seconds to indicate that the service is still running.
     */
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
        // the scanner input will come on background threads, make sure to execute this on the UI Thread
        if(barcodeScanResults.symbology!!.isNotEmpty()) {
            Log.d("scanner","Got barcode: ${barcodeScanResults.barcodeContent} with symbology: ${barcodeScanResults.symbology}")
            // do some custom logic here to react on received barcodes and symbology
        } else {
            Log.d("scanner", "Got barcode: ${barcodeScanResults.barcodeContent}")
            // not every scanner currently has the ability to send the barcode symbology
        }

    }

    override fun onScannerConnected() {
        scannerConnected = true
        Log.d("scanner", "scanner connected")
        Log.d("scanner", "Is connected? : ${pgManager.isConnectedToScanner()}")
        // let the user know that the scanner is connected
    }

    override fun onScannerDisconnected() {
        scannerConnected = false
        Log.d("scanner", "scanner disconnected")
        // Inform the user that the scanner has been disconnected
    }
}
