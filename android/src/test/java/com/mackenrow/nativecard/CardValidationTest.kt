package com.mackenrow.nativecard

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every expected value here was checked against the real, compiled
 * `com.stripe:stripe-android:21.29.2` classes before being written down — not
 * assumed from documentation, not carried over from the custom BIN-prefix
 * heuristic this package used to have. See `docs/VALIDATION.md` for the
 * platform-divergence notes a few of these cases exist to pin down.
 *
 * No Robolectric, no Android [android.content.Context], no emulator:
 * [CardValidation] touches only `com.stripe.android.CardUtils` /
 * `com.stripe.android.model.CardBrand`, which are plain JVM classes, so these
 * run as ordinary local JVM unit tests (`./gradlew testDebugUnitTest`).
 */
class CardValidationTest {

  // ---------------------------------------------------------------------
  // numberStatus — PAN
  // ---------------------------------------------------------------------

  @Test
  fun `empty PAN is empty`() {
    assertEquals(FieldStatus.EMPTY, CardValidation.numberStatus(""))
  }

  @Test
  fun `single leading digit of a real brand is incomplete, not invalid`() {
    // "4" alone is already enough for Stripe to say Visa, but nowhere near a
    // real Visa's length.
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.numberStatus("4"))
  }

  @Test
  fun `partial visa prefix is incomplete`() {
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.numberStatus("42424242"))
  }

  @Test
  fun `valid 16-digit visa is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("4242424242424242"))
  }

  @Test
  fun `16-digit visa with a broken luhn digit is invalid`() {
    // Same PAN as above with the last digit changed — right length, wrong
    // checksum.
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("4242424242424241"))
  }

  @Test
  fun `13-digit visa is judged incomplete on this SDK, not valid`() {
    // Verified against the compiled SDK: CardBrand.Visa.isValidCardNumberLength
    // returns false for this 13-digit, Luhn-valid PAN, and
    // getMaxLengthForCardNumber stays 16 — so it's short of a length Android's
    // classifier will accept, not a second valid Visa length. iOS's own
    // hardcoded BIN table lists 13-digit Visa ranges; this package doesn't
    // paper over that divergence (see docs/VALIDATION.md).
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.numberStatus("4222222222222"))
  }

  @Test
  fun `19-digit visa is invalid, not a wider valid length`() {
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("4000000000000000006"))
  }

  @Test
  fun `valid 16-digit mastercard is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("5555555555554444"))
  }

  @Test
  fun `valid mastercard 2-series (22-27) is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("2223003122003222"))
  }

  @Test
  fun `valid 15-digit amex is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("378282246310005"))
  }

  @Test
  fun `15-digit amex with a broken luhn digit is invalid`() {
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("378282246310004"))
  }

  @Test
  fun `14-digit amex prefix is incomplete`() {
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.numberStatus("37828224631000"))
  }

  @Test
  fun `16-digit amex-prefixed number is invalid, amex never has 16`() {
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("3782822463100050"))
  }

  @Test
  fun `valid 16-digit discover (6011) is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("6011111111111117"))
  }

  @Test
  fun `valid 16-digit discover (65-prefix) is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("6500000000000002"))
  }

  @Test
  fun `valid 16-digit jcb is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("3530111333300000"))
  }

  @Test
  fun `valid 16-digit unionpay is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("6200000000000005"))
  }

  @Test
  fun `67-prefixed 16-digit PAN classifies as mastercard and is valid`() {
    // This is the number that started the whole investigation: "Maestro"
    // by BIN convention, but Stripe's own classifier files it as MasterCard
    // (verified: brand=MasterCard, isValidCardNumberLength=true) — so at the
    // right length it validates like any other MasterCard.
    assertEquals(FieldStatus.VALID, CardValidation.numberStatus("6759649826438453"))
  }

  @Test
  fun `67-prefixed 18-digit PAN is invalid — the exact regression this fix must not reintroduce`() {
    // Same BIN family as above, padded to 18 digits. Stripe still calls it
    // MasterCard, and MasterCard's length is fixed at 16 on this SDK — this
    // number is genuinely invalid per Stripe, not a Maestro case we're
    // failing to widen. If a future change reintroduces a Maestro-specific
    // length allowance, THIS is the test that should catch it.
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("676341724291545093"))
  }

  @Test
  fun `a discover-shaped 622-prefix at 17 digits is invalid`() {
    // Verified against the compiled SDK: this PAN's brand is actually
    // UnionPay here, not Discover (see the brand test below) — but either
    // way, 17 digits exceeds the 16-digit max this SDK reports for it.
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("62292323072416244"))
  }

  @Test
  fun `a discover-shaped 622-prefix at 18 digits is invalid`() {
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("622147453553343498"))
  }

  @Test
  fun `an unrecognized brand is invalid even when Luhn-valid`() {
    // Mirrors STPCardValidator.swift on iOS, which returns .invalid the
    // moment the BIN is unrecognized — before it ever looks at Luhn. Without
    // this rule, CardBrand.Unknown's isValidCardNumberLength (always false on
    // this SDK) leaves the field stuck in "incomplete" forever: never valid,
    // never invalid, no error shown, and no way to submit.
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("1234567890123452"))
  }

  @Test
  fun `a single digit matching no known brand is invalid immediately, not incomplete`() {
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("1"))
  }

  @Test
  fun `a long unrecognized-brand digit string is invalid`() {
    assertEquals(FieldStatus.INVALID, CardValidation.numberStatus("123456789012345678"))
  }

  // ---------------------------------------------------------------------
  // normalizedBrand — the icon/CVC-length classifier
  // ---------------------------------------------------------------------

  @Test
  fun `empty PAN reports unknown brand`() {
    assertEquals("unknown", CardValidation.normalizedBrand(""))
  }

  @Test
  fun `a single 4 already reports visa`() {
    assertEquals("visa", CardValidation.normalizedBrand("4"))
  }

  @Test
  fun `brand mapping for every recognized network`() {
    val cases = mapOf(
      "4242424242424242" to "visa",
      "5555555555554444" to "mastercard",
      "2223003122003222" to "mastercard",
      "378282246310005" to "amex",
      "6011111111111117" to "discover",
      "6500000000000002" to "discover",
      "3530111333300000" to "jcb",
      "6200000000000005" to "unionpay",
      "1234567890123452" to "unknown"
    )
    cases.forEach { (pan, expectedBrand) ->
      assertEquals("brand for $pan", expectedBrand, CardValidation.normalizedBrand(pan))
    }
  }

  @Test
  fun `a 67-prefixed PAN reports mastercard, never maestro`() {
    // CardBrand has no Maestro case at all on this SDK — Stripe classifies
    // Maestro's own network prefix as MasterCard by design. The `maestro`
    // string still exists in this package's CardBrand type and has an icon,
    // but nothing in this classifier can ever return it.
    assertEquals("mastercard", CardValidation.normalizedBrand("6759649826438453"))
    assertEquals("mastercard", CardValidation.normalizedBrand("676341724291545093"))
  }

  @Test
  fun `a 622-prefixed PAN reports unionpay, not discover`() {
    // Worth pinning explicitly: it would be easy to assume Discover's
    // documented 622126-622925 range applies here the way it does on iOS's
    // hardcoded BIN table, but Android's real classifier disagrees — verified
    // by running the compiled SDK, not inferred from iOS's source.
    assertEquals("unionpay", CardValidation.normalizedBrand("622147453553343498"))
    assertEquals("unionpay", CardValidation.normalizedBrand("62292323072416244"))
  }

  // ---------------------------------------------------------------------
  // cvcStatus
  // ---------------------------------------------------------------------

  @Test
  fun `empty CVC is empty regardless of brand`() {
    assertEquals(FieldStatus.EMPTY, CardValidation.cvcStatus("", "visa"))
    assertEquals(FieldStatus.EMPTY, CardValidation.cvcStatus("", "amex"))
  }

  @Test
  fun `non-amex CVC is 3 digits — shorter is incomplete, longer is invalid`() {
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.cvcStatus("1", "visa"))
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.cvcStatus("12", "visa"))
    assertEquals(FieldStatus.VALID, CardValidation.cvcStatus("123", "visa"))
    assertEquals(FieldStatus.INVALID, CardValidation.cvcStatus("1234", "visa"))
  }

  @Test
  fun `amex CVC is 4 digits — shorter is incomplete, longer is invalid`() {
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.cvcStatus("1", "amex"))
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.cvcStatus("123", "amex"))
    assertEquals(FieldStatus.VALID, CardValidation.cvcStatus("1234", "amex"))
    assertEquals(FieldStatus.INVALID, CardValidation.cvcStatus("12345", "amex"))
  }

  @Test
  fun `every non-amex brand string takes the same 3-digit rule, including unknown`() {
    listOf("mastercard", "discover", "jcb", "unionpay", "unknown", "").forEach { brand ->
      assertEquals("3-digit CVC for brand=$brand", FieldStatus.VALID, CardValidation.cvcStatus("123", brand))
      assertEquals("4-digit CVC for brand=$brand", FieldStatus.INVALID, CardValidation.cvcStatus("1234", brand))
    }
  }

  // ---------------------------------------------------------------------
  // expiryStatus — always against a FIXED `now`, never the real wall clock,
  // so these never become time bombs that fail on their own months later.
  // ---------------------------------------------------------------------

  private val fixedNow: Calendar = Calendar.getInstance().apply {
    clear()
    set(Calendar.YEAR, 2026)
    set(Calendar.MONTH, Calendar.SEPTEMBER) // expiryStatus reads MONTH + 1 == 9
  }

  @Test
  fun `empty expiry is empty`() {
    assertEquals(FieldStatus.EMPTY, CardValidation.expiryStatus("", fixedNow))
  }

  @Test
  fun `a single digit of the month is incomplete`() {
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.expiryStatus("0", fixedNow))
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.expiryStatus("1", fixedNow))
  }

  @Test
  fun `month 00 is invalid before a year is even typed`() {
    assertEquals(FieldStatus.INVALID, CardValidation.expiryStatus("00", fixedNow))
  }

  @Test
  fun `month 13 is invalid before a year is even typed`() {
    assertEquals(FieldStatus.INVALID, CardValidation.expiryStatus("13", fixedNow))
  }

  @Test
  fun `a valid month alone, with no year digits yet, is incomplete`() {
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.expiryStatus("09", fixedNow))
    assertEquals(FieldStatus.INCOMPLETE, CardValidation.expiryStatus("12", fixedNow))
  }

  @Test
  fun `the current month and year is valid — the boundary the whole comparison hinges on`() {
    assertEquals(FieldStatus.VALID, CardValidation.expiryStatus("0926", fixedNow))
  }

  @Test
  fun `last month of the same year is invalid`() {
    assertEquals(FieldStatus.INVALID, CardValidation.expiryStatus("0826", fixedNow))
  }

  @Test
  fun `next month of the same year is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.expiryStatus("1026", fixedNow))
  }

  @Test
  fun `january of next year is valid`() {
    assertEquals(FieldStatus.VALID, CardValidation.expiryStatus("0127", fixedNow))
  }

  @Test
  fun `december of last year is invalid`() {
    assertEquals(FieldStatus.INVALID, CardValidation.expiryStatus("1225", fixedNow))
  }

  @Test
  fun `a year decades in the future is valid — there is no upper bound`() {
    assertEquals(FieldStatus.VALID, CardValidation.expiryStatus("1299", fixedNow))
  }

  @Test
  fun `year 00 compares as the year 2000, not 2100 — a documented quirk, not a fix`() {
    // The two-digit year is compared with a plain mod-100 subtraction, so
    // "00" always reads as smaller than any current year from 01 to 99. That
    // is correct today (2000 is long past) and would misjudge a genuine
    // "expires in 2100" card once the current year itself wraps past 99 mod
    // 100 — a scenario decades away and out of scope for this ticket. This
    // test exists to make that latent edge case visible, not to change it.
    assertEquals(FieldStatus.INVALID, CardValidation.expiryStatus("0100", fixedNow))
  }

  @Test
  fun `expiryStatus defaults to the real clock when now is not supplied`() {
    // The production code path: MackenrowNativeCardView never passes `now`,
    // so this has to keep working against Calendar.getInstance() by default.
    // A date far in the past is invalid under any real-world "now".
    assertEquals(FieldStatus.INVALID, CardValidation.expiryStatus("0120"))
  }
}
