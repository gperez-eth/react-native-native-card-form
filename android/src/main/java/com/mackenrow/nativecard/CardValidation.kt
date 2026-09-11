package com.mackenrow.nativecard

import com.stripe.android.CardUtils
import com.stripe.android.model.CardBrand
import java.util.Calendar

/**
 * The status a sensitive field (PAN, expiry, or CVC) can present. Mirrors
 * `docs/VALIDATION.md`.
 */
internal enum class FieldStatus(val wireName: String) {
  EMPTY("empty"),
  INCOMPLETE("incomplete"),
  INVALID("invalid"),
  VALID("valid")
}

/**
 * Pure decision logic behind every sensitive field, extracted out of
 * [MackenrowNativeCardView] so it can be unit tested with plain JUnit — no
 * Android [android.content.Context], no live `EditText`, no Robolectric.
 * Every branch here is Stripe's own answer, composed but never
 * second-guessed. See `docs/VALIDATION.md`.
 */
internal object CardValidation {
  // Delegates entirely to Stripe's own CardBrand: no hand-maintained length
  // table and no BIN-prefix guessing. Stripe's `isValidCardNumberLength` /
  // `getMaxLengthForCardNumber` already encode brand-specific rules, and
  // CardBrand has no dedicated Maestro case — Stripe classifies those BINs as
  // MasterCard on purpose (see STPBINController's hardcoded
  // "67 -> MasterCard // Maestro" range on iOS, and verified here: a 16-digit
  // 67-prefixed number reports brand=MasterCard with isValidCardNumberLength
  // true). Whatever length Stripe accepts for the brand IT assigns is what
  // this field accepts too — verified against the real compiled SDK, not
  // assumed: on this SDK version a 13- or 19-digit Visa PAN is judged
  // INCOMPLETE/INVALID here, because Android's CardBrand.Visa only validates
  // 16, unlike iOS's hardcoded BIN table (which lists several 13-digit Visa
  // ranges) — the two platforms are not guaranteed to agree on every brand's
  // length set, and that's Stripe's call to make, not ours to paper over.
  //
  // CardBrand.Unknown gets special handling for a reason that IS ours to
  // document: `isValidCardNumberLength` on Unknown returns false for every
  // length (verified 1 through 18 digits) and `getMaxLengthForCardNumber`
  // never grows past 16, so without this branch an unrecognized-brand PAN of
  // 16 digits or fewer would sit in INCOMPLETE forever — never becoming
  // VALID, never becoming INVALID, leaving the field with no way to submit
  // and no error shown either. iOS never hits this: its own
  // `STPCardValidator.validationState(forNumber:validatingCardBrand: true)`
  // returns `.invalid` the moment the BIN range is unknown, by design (see
  // STPCardValidator.swift), even a single digit that matches no brand at
  // all. Rejecting CardBrand.Unknown outright mirrors that real, verified
  // iOS policy instead of inventing a third, Android-only behavior.
  fun numberStatus(value: String): FieldStatus {
    if (value.isEmpty()) return FieldStatus.EMPTY
    val stripeBrand = CardUtils.getPossibleCardBrand(value)
    if (stripeBrand == CardBrand.Unknown) return FieldStatus.INVALID
    return when {
      value.length > stripeBrand.getMaxLengthForCardNumber(value) -> FieldStatus.INVALID
      stripeBrand.isValidCardNumberLength(value) ->
        if (CardUtils.isValidLuhnNumber(value)) FieldStatus.VALID else FieldStatus.INVALID
      else -> FieldStatus.INCOMPLETE
    }
  }

  // `now` defaults to the real clock; tests pass a fixed one so date-boundary
  // cases (Dec this year vs Jan next year, this month vs last month) never
  // become time bombs that fail on their own as the calendar moves on.
  fun expiryStatus(value: String, now: Calendar = Calendar.getInstance()): FieldStatus {
    if (value.isEmpty()) return FieldStatus.EMPTY
    if (value.length < 2) return FieldStatus.INCOMPLETE
    val month = value.take(2).toIntOrNull() ?: return FieldStatus.INVALID
    if (month !in 1..12) return FieldStatus.INVALID
    if (value.length < 4) return FieldStatus.INCOMPLETE
    val year = value.drop(2).toIntOrNull() ?: return FieldStatus.INVALID
    val currentYear = now.get(Calendar.YEAR) % 100
    val currentMonth = now.get(Calendar.MONTH) + 1
    return if (year > currentYear || year == currentYear && month >= currentMonth) {
      FieldStatus.VALID
    } else {
      FieldStatus.INVALID
    }
  }

  fun cvcStatus(value: String, brand: String): FieldStatus {
    if (value.isEmpty()) return FieldStatus.EMPTY
    val expected = if (brand == "amex") 4 else 3
    return when {
      value.length < expected -> FieldStatus.INCOMPLETE
      value.length == expected -> FieldStatus.VALID
      else -> FieldStatus.INVALID
    }
  }

  // No BIN-prefix guessing here either: a brand this repo can't render an
  // icon for (DinersClub, CartesBancaires — see CardBrand.values()) falls to
  // "unknown", same as anything Stripe itself doesn't recognize.
  fun normalizedBrand(value: String): String = when (CardUtils.getPossibleCardBrand(value)) {
    CardBrand.Visa -> "visa"
    CardBrand.MasterCard -> "mastercard"
    CardBrand.AmericanExpress -> "amex"
    CardBrand.Discover -> "discover"
    CardBrand.JCB -> "jcb"
    CardBrand.UnionPay -> "unionpay"
    else -> "unknown"
  }
}
