package com.mackenrow.nativecard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.core.exception.APIConnectionException
import com.stripe.android.exception.CardException
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import expo.modules.kotlin.Promise
import java.lang.ref.WeakReference
import java.util.UUID

internal enum class SensitiveField(val wireName: String) {
  NUMBER("number"),
  EXPIRY("expiry"),
  CVC("cvc");

  companion object {
    fun fromWireName(value: String) = entries.firstOrNull { it.wireName == value }
  }
}

/**
 * Implemented only by package-private native inputs. Values never leave this
 * interface or cross the React Native bridge.
 */
internal interface SensitiveFieldHandle {
  val sensitiveField: SensitiveField
  fun sensitiveDigits(): String
  fun isSensitiveValid(): Boolean
  fun clearSensitiveValue()
  fun focusSensitiveField()
  fun blurSensitiveField()
  fun revealValidationError()
}

internal object CardSessionRegistry {
  private const val SAFE_MESSAGE = "Native card form operation failed."
  private const val MIN_TIMEOUT_MS = 1_000L
  private const val MAX_TIMEOUT_MS = 120_000L

  private data class PendingOperation(
    val id: String,
    val promise: Promise,
    val timeout: Runnable,
    var settled: Boolean = false
  )

  private data class Session(
    val fields: MutableMap<SensitiveField, WeakReference<SensitiveFieldHandle>> = mutableMapOf(),
    var pending: PendingOperation? = null,
    var disposed: Boolean = false
  )

  private val mainHandler = Handler(Looper.getMainLooper())
  private val sessions = mutableMapOf<String, Session>()

  @Synchronized
  fun ensure(sessionId: String) {
    if (sessionId.isBlank()) return
    val current = sessions[sessionId]
    if (current == null || current.disposed) sessions[sessionId] = Session()
  }

  @Synchronized
  fun register(sessionId: String, field: SensitiveFieldHandle) {
    ensure(sessionId)
    sessions[sessionId]?.fields?.set(field.sensitiveField, WeakReference(field))
  }

  @Synchronized
  fun unregister(sessionId: String, field: SensitiveFieldHandle) {
    val session = sessions[sessionId] ?: return
    val registered = session.fields[field.sensitiveField]?.get()
    if (registered === field) session.fields.remove(field.sensitiveField)
  }

  @Synchronized
  fun focusNext(sessionId: String, field: SensitiveField) {
    val next = when (field) {
      SensitiveField.NUMBER -> SensitiveField.EXPIRY
      SensitiveField.EXPIRY -> SensitiveField.CVC
      SensitiveField.CVC -> null
    }
    if (next == null) {
      sessions[sessionId]?.fields?.get(SensitiveField.CVC)?.get()?.blurSensitiveField()
    } else {
      sessions[sessionId]?.fields?.get(next)?.get()?.focusSensitiveField()
    }
  }

  // Same brand this session's number field reports — CardValidation's single
  // classifier, taken straight from Stripe's own. Only "amex" changes
  // anything for a caller of this method (the CVC field's max length), so
  // anything else Stripe doesn't natively distinguish safely collapses to
  // "unknown".
  @Synchronized
  fun brand(sessionId: String?): String {
    val value = sessionId
      ?.let { sessions[it] }
      ?.fields
      ?.get(SensitiveField.NUMBER)
      ?.get()
      ?.sensitiveDigits()
      .orEmpty()
    return CardValidation.normalizedBrand(value)
  }

  // NOT `@Synchronized` any more — see `dispose`, right below, for why: this had
  // the identical deadlock shape, just reached from `selectMethod` instead of an
  // unmount.
  fun reset(sessionId: String, promise: Promise) {
    val snapshot: List<SensitiveFieldHandle>
    synchronized(this) {
      val session = activeSession(sessionId, promise) ?: return
      cancelPending(session, "cancelled")
      snapshot = liveFields(session)
    }
    mainHandler.post { snapshot.forEach { it.clearSensitiveValue() } }
    promise.resolve(null)
  }

  @Synchronized
  fun focus(sessionId: String, fieldName: String, promise: Promise) {
    val session = activeSession(sessionId, promise) ?: return
    val field = SensitiveField.fromWireName(fieldName)
    val target = field?.let { session.fields[it]?.get() }
    if (target == null) {
      reject(promise, "card_incomplete")
      return
    }
    target.focusSensitiveField()
    promise.resolve(null)
  }

  /**
   * NOT `@Synchronized` — this used to be, and it deadlocked the main thread
   * (reported as an ANR): `dispose` runs on Expo's background `AsyncFunction`
   * queue, called from `NativeCardForm.tsx`'s own unmount effect, in the SAME
   * commit React unmounts the view in. `@Synchronized` wrapped this whole
   * function, so it held the registry's single lock across
   * `clearSensitiveValue()` → `emitSanitizedState()` → a Fabric event emit that
   * needs the main thread's Choreographer to run. If Fabric detached the same
   * view on the main thread in that same commit — which it does — that thread
   * hit `unregister`'s OWN `@Synchronized` on the identical lock and blocked,
   * while this thread sat blocked waiting for the main thread to process the
   * event it had just emitted: two threads, one lock, each waiting on the
   * other. Neither `unregister` (a plain map removal) nor `dispose`'s own
   * bookkeeping needs more than a few instructions under the lock — only
   * `clearSensitiveValue` reaches back into Fabric, so it is the one thing
   * that must never run while the lock is held. It runs after, unconditionally
   * on the main thread via `mainHandler`, matching where `onDetachedFromWindow`
   * already calls the SAME method directly.
   */
  fun dispose(sessionId: String, promise: Promise) {
    val snapshot: List<SensitiveFieldHandle>
    synchronized(this) {
      val session = sessions.remove(sessionId)
      if (session == null) {
        promise.resolve(null)
        return
      }
      session.disposed = true
      cancelPending(session, "cancelled")
      snapshot = liveFields(session)
      session.fields.clear()
    }
    mainHandler.post { snapshot.forEach { it.clearSensitiveValue() } }
    promise.resolve(null)
  }

  @Synchronized
  fun tokenize(context: Context, sessionId: String, requestedTimeoutMs: Int, promise: Promise) {
    val session = activeSession(sessionId, promise) ?: return
    if (session.pending != null) {
      reject(promise, "busy")
      return
    }

    val fields = SensitiveField.entries.associateWith { session.fields[it]?.get() }
    if (fields.values.any { it == null }) {
      fields.values.filterNotNull().forEach { it.revealValidationError() }
      reject(promise, "card_incomplete")
      return
    }

    // This snapshot exists only in native memory for the duration of this call.
    val number = fields.getValue(SensitiveField.NUMBER)!!.sensitiveDigits()
    val expiry = fields.getValue(SensitiveField.EXPIRY)!!.sensitiveDigits()
    val cvc = fields.getValue(SensitiveField.CVC)!!.sensitiveDigits()
    if (fields.values.filterNotNull().any { !it.isSensitiveValid() }) {
      fields.values.filterNotNull().forEach { it.revealValidationError() }
      reject(promise, "card_incomplete")
      return
    }

    val month = expiry.substring(0, 2).toIntOrNull()
    val year = expiry.substring(2, 4).toIntOrNull()?.plus(2000)
    if (month == null || year == null) {
      reject(promise, "card_invalid")
      return
    }

    val publishableKey = try {
      PaymentConfiguration.getInstance(context).publishableKey
    } catch (_: IllegalStateException) {
      null
    }
    if (publishableKey.isNullOrBlank()) {
      reject(promise, "stripe_not_configured")
      return
    }

    val operationId = UUID.randomUUID().toString()
    val timeoutMs = requestedTimeoutMs.toLong().coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
    val timeout = Runnable { settleFailure(sessionId, operationId, "timeout") }
    session.pending = PendingOperation(operationId, promise, timeout)
    mainHandler.postDelayed(timeout, timeoutMs)

    val card = PaymentMethodCreateParams.Card.Builder()
      .setNumber(number)
      .setExpiryMonth(month)
      .setExpiryYear(year)
      .setCvc(cvc)
      .build()

    Stripe(context.applicationContext, publishableKey).createPaymentMethod(
      PaymentMethodCreateParams.create(card),
      null,
      null,
      object : ApiResultCallback<PaymentMethod> {
        override fun onSuccess(paymentMethod: PaymentMethod) {
          val identifier = paymentMethod.id
          if (identifier.isNullOrBlank()) {
            settleFailure(sessionId, operationId, "tokenization_failed")
          } else {
            settleSuccess(sessionId, operationId, identifier)
          }
        }

        override fun onError(e: Exception) {
          val code = when (e) {
            is APIConnectionException -> "network_error"
            is CardException -> "tokenization_failed"
            else -> "tokenization_failed"
          }
          settleFailure(sessionId, operationId, code)
        }
      }
    )
  }

  @Synchronized
  private fun settleSuccess(sessionId: String, operationId: String, paymentMethodId: String) {
    val pending = takePending(sessionId, operationId) ?: return
    pending.promise.resolve(mapOf("paymentMethodId" to paymentMethodId))
  }

  @Synchronized
  private fun settleFailure(sessionId: String, operationId: String, code: String) {
    val pending = takePending(sessionId, operationId) ?: return
    reject(pending.promise, code)
  }

  private fun takePending(sessionId: String, operationId: String): PendingOperation? {
    val session = sessions[sessionId] ?: return null
    if (session.disposed) return null
    val pending = session.pending ?: return null
    if (pending.id != operationId || pending.settled) return null
    pending.settled = true
    session.pending = null
    mainHandler.removeCallbacks(pending.timeout)
    return pending
  }

  private fun activeSession(sessionId: String, promise: Promise): Session? {
    val session = sessions[sessionId]
    if (session == null || session.disposed) reject(promise, "disposed")
    return session?.takeUnless { it.disposed }
  }

  private fun liveFields(session: Session) =
    session.fields.values.mapNotNull { it.get() }

  private fun cancelPending(session: Session, code: String) {
    val pending = session.pending ?: return
    if (pending.settled) return
    pending.settled = true
    session.pending = null
    mainHandler.removeCallbacks(pending.timeout)
    reject(pending.promise, code)
  }

  private fun reject(promise: Promise, code: String) {
    promise.reject(code, SAFE_MESSAGE, null)
  }
}
