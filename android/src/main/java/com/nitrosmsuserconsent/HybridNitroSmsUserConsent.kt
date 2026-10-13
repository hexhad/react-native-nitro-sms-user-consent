package com.nitrosmsuserconsent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.common.LifecycleState
import com.facebook.proguard.annotations.DoNotStrip
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Tasks
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import com.margelo.nitro.nitrosmsuserconsent.HybridNitroSmsUserConsentSpec
import com.margelo.nitro.nitrosmsuserconsent.SmsConsentEvent
import com.margelo.nitro.nitrosmsuserconsent.SmsConsentStatus

// Nitro instantiates this class from C++ through JNI, which R8 cannot see. Without these
// annotations a minified release build strips the constructor, so no field initializer runs
// and the first `synchronized(lock)` throws a NullPointerException.
@DoNotStrip
@Keep
class HybridNitroSmsUserConsent : HybridNitroSmsUserConsentSpec() {

  private val lock = Any()
  private var handler: ((event: SmsConsentEvent) -> Unit)? = null
  private var receiver: BroadcastReceiver? = null
  // Consent intent received while the app was in the background
  private var pendingConsentIntent: Intent? = null

  override val memorySize: Long get() = 0L

  override val isSupported: Boolean
    get() = NitroModules.applicationContext?.let {
      GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(it) == ConnectionResult.SUCCESS
    } ?: false

  override fun startOtpListener(
    handler: (event: SmsConsentEvent) -> Unit,
    senderPhoneNumber: String?
  ): Promise<Unit> = Promise.async {
    val ctx = reactContext
    stopInternal()

    val rx = createReceiver()
    synchronized(lock) {
      this.handler = handler
      receiver = rx
    }
    // The broadcast is sent by Google Play services (another app), so the receiver
    // must be exported on Android 13+, and SEND_PERMISSION ensures only Play services
    // can deliver it.
    ContextCompat.registerReceiver(
      ctx,
      rx,
      IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
      SmsRetriever.SEND_PERMISSION,
      null,
      ContextCompat.RECEIVER_EXPORTED
    )
    ctx.addActivityEventListener(activityListener)
    ctx.addLifecycleEventListener(lifecycleListener)

    try {
      Tasks.await(SmsRetriever.getClient(ctx).startSmsUserConsent(senderPhoneNumber))
    } catch (e: Exception) {
      stopInternal()
      throw e
    }
  }

  override fun stopOtpListener(): Promise<Unit> = Promise.async {
    stopInternal()
  }

  // ── Broadcast handling ────────────────────────────────────────────────

  private fun createReceiver() = object : BroadcastReceiver() {
    override fun onReceive(c: Context?, intent: Intent?) {
      if (intent?.action != SmsRetriever.SMS_RETRIEVED_ACTION) return
      val extras = intent.extras ?: return
      val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
      } else {
        @Suppress("DEPRECATION")
        extras.get(SmsRetriever.EXTRA_STATUS) as? Status
      } ?: return

      when (status.statusCode) {
        CommonStatusCodes.SUCCESS -> {
          val consentIntent = IntentCompat.getParcelableExtra(
            intent,
            SmsRetriever.EXTRA_CONSENT_INTENT,
            Intent::class.java
          )
          when {
            consentIntent == null ->
              finish(SmsConsentStatus.ERROR, error = "Consent intent missing")
            !isTrustedConsentIntent(consentIntent) ->
              finish(SmsConsentStatus.ERROR, error = "Untrusted consent intent")
            else -> launchConsent(consentIntent)
          }
        }
        CommonStatusCodes.TIMEOUT -> finish(SmsConsentStatus.TIMEOUT)
      }
    }
  }

  // Guards against intent redirection: only launch Play services' own consent
  // activity, and never with URI permission grants.
  private fun isTrustedConsentIntent(intent: Intent): Boolean {
    val component = intent.resolveActivity(reactContext.packageManager) ?: return false
    val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return component.packageName == GMS_PACKAGE && (intent.flags and grantFlags) == 0
  }

  private fun launchConsent(intent: Intent) {
    UiThreadUtil.runOnUiThread {
      val activity = reactContext.currentActivity
      // currentActivity is kept while paused, but Android blocks background
      // activity starts (returning RESULT_CANCELED) — defer until resumed
      if (activity == null || reactContext.lifecycleState != LifecycleState.RESUMED) {
        synchronized(lock) { pendingConsentIntent = intent }
        return@runOnUiThread
      }
      try {
        // Must be started within 5 minutes, otherwise a TIMEOUT broadcast follows
        activity.startActivityForResult(intent, SMS_CONSENT_REQUEST)
      } catch (e: ActivityNotFoundException) {
        finish(SmsConsentStatus.ERROR, error = e.message ?: "Consent activity not found")
      }
    }
  }

  private val activityListener = object : ActivityEventListener {
    override fun onActivityResult(
      activity: Activity,
      requestCode: Int,
      resultCode: Int,
      data: Intent?
    ) {
      if (requestCode != SMS_CONSENT_REQUEST) return
      val message = data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
      if (resultCode == Activity.RESULT_OK && message != null) {
        finish(SmsConsentStatus.RECEIVED, message = message)
      } else {
        finish(SmsConsentStatus.DENIED)
      }
    }

    override fun onNewIntent(intent: Intent) = Unit
  }

  private val lifecycleListener = object : LifecycleEventListener {
    override fun onHostResume() {
      val intent = synchronized(lock) {
        pendingConsentIntent.also { pendingConsentIntent = null }
      } ?: return
      launchConsent(intent)
    }

    override fun onHostPause() = Unit

    override fun onHostDestroy() = Unit
  }

  // ── Session lifecycle ─────────────────────────────────────────────────

  /** Ends the session and delivers its single result to JS. */
  private fun finish(
    status: SmsConsentStatus,
    message: String? = null,
    error: String? = null
  ) {
    val callback = synchronized(lock) { handler }
    stopInternal()
    callback?.invoke(SmsConsentEvent(status, message, error))
  }

  // Idempotent — safe to call when nothing is registered.
  private fun stopInternal() {
    val rx = synchronized(lock) {
      handler = null
      pendingConsentIntent = null
      receiver.also { receiver = null }
    }
    val ctx = NitroModules.applicationContext ?: return
    rx?.let {
      try {
        ctx.unregisterReceiver(it)
      } catch (_: IllegalArgumentException) {
        // Already unregistered
      }
    }
    ctx.removeActivityEventListener(activityListener)
    ctx.removeLifecycleEventListener(lifecycleListener)
  }

  private val reactContext: ReactApplicationContext
    get() = NitroModules.applicationContext
      ?: error("React application context not available")

  companion object {
    private const val SMS_CONSENT_REQUEST = 0x534D // "SM"
    private const val GMS_PACKAGE = "com.google.android.gms"
  }
}
