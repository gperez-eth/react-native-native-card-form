import XCTest
@testable import CardValidationCore

/// Every expected value here was checked by actually running these against
/// the real, compiled Stripe iOS SDK (24.25.0, the exact version pinned in
/// this repo's `ios/Pods`) on a booted simulator — not assumed from reading
/// `STPCardValidator.swift`/`STPBINController.swift`, even though that
/// reading is what shaped which cases were worth asserting. See
/// `docs/VALIDATION.md` for the platform-divergence notes a few of these
/// cases exist to pin down.
final class CardValidationTests: XCTestCase {

  // ---------------------------------------------------------------------
  // numberStatus — PAN
  // ---------------------------------------------------------------------

  func testEmptyPANIsEmpty() {
    XCTAssertEqual(CardValidation.numberStatus(""), .empty)
  }

  func testSingleLeadingDigitOfARealBrandIsIncompleteNotInvalid() {
    XCTAssertEqual(CardValidation.numberStatus("4"), .incomplete)
  }

  func testPartialVisaPrefixIsIncomplete() {
    XCTAssertEqual(CardValidation.numberStatus("42424242"), .incomplete)
  }

  func testValid16DigitVisaIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("4242424242424242"), .valid)
  }

  func test16DigitVisaWithBrokenLuhnDigitIsInvalid() {
    XCTAssertEqual(CardValidation.numberStatus("4242424242424241"), .invalid)
  }

  func test13DigitVisaFromADocumentedHardcodedBINIsValid() {
    // STPBINController's hardcoded seed table lists several 13-digit Visa
    // ranges, "413600" among them — unlike Android's CardBrand.Visa, which
    // this package's own JUnit suite shows only validates 16 digits on
    // stripe-android 21.29.2. This is the iOS half of that documented
    // platform divergence, not a guess: 4136000000008 is Luhn-valid and its
    // prefix is one of the exact ranges in STPBINController.swift.
    XCTAssertEqual(CardValidation.numberStatus("4136000000008"), .valid)
  }

  func test19DigitVisaIsInvalid() {
    // No 19-digit Visa range exists in the hardcoded table for this prefix,
    // so the catch-all Unknown range's 19-digit length would apply instead —
    // except Unknown is always invalid regardless of length (see below).
    XCTAssertEqual(CardValidation.numberStatus("4000000000000000006"), .invalid)
  }

  func testValid16DigitMastercardIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("5555555555554444"), .valid)
  }

  func testValidMastercard2SeriesIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("2223003122003222"), .valid)
  }

  func testValid15DigitAmexIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("378282246310005"), .valid)
  }

  func test15DigitAmexWithBrokenLuhnDigitIsInvalid() {
    XCTAssertEqual(CardValidation.numberStatus("378282246310004"), .invalid)
  }

  func test14DigitAmexPrefixIsIncomplete() {
    XCTAssertEqual(CardValidation.numberStatus("37828224631000"), .incomplete)
  }

  func test16DigitAmexPrefixedNumberIsInvalid() {
    // Amex never has 16 digits.
    XCTAssertEqual(CardValidation.numberStatus("3782822463100050"), .invalid)
  }

  func testValid16DigitDiscover6011IsValid() {
    XCTAssertEqual(CardValidation.numberStatus("6011111111111117"), .valid)
  }

  func testValid16DigitDiscover65PrefixIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("6500000000000002"), .valid)
  }

  func testValid16DigitJCBIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("3530111333300000"), .valid)
  }

  func testValid16DigitUnionPayIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("6200000000000005"), .valid)
  }

  func test67Prefixed16DigitPANClassifiesAsMastercardAndIsValid() {
    // The number that started the whole investigation: "Maestro" by BIN
    // convention, but Stripe's own hardcoded table
    // (`"67" -> mastercard // Maestro`) files it as MasterCard — so at the
    // right length it validates like any other MasterCard.
    XCTAssertEqual(CardValidation.numberStatus("6759649826438453"), .valid)
  }

  func test67Prefixed18DigitPANIsInvalidTheExactRegressionThisFixMustNotReintroduce() {
    // Same BIN family as above, padded to 18 digits. Stripe still calls it
    // MasterCard, and MasterCard's length is fixed at 16 in the hardcoded
    // table — this number is genuinely invalid per Stripe, not a Maestro
    // case we're failing to widen. If a future change reintroduces a
    // Maestro-specific length allowance, THIS is the test that should catch
    // it.
    XCTAssertEqual(CardValidation.numberStatus("676341724291545093"), .invalid)
  }

  func testADiscoverShaped622PrefixAt17DigitsIsInvalid() {
    XCTAssertEqual(CardValidation.numberStatus("62292323072416244"), .invalid)
  }

  func testADiscoverShaped622PrefixAt18DigitsIsInvalid() {
    XCTAssertEqual(CardValidation.numberStatus("622147453553343498"), .invalid)
  }

  func testAnUnrecognizedBrandIsInvalidEvenWhenLuhnValid() {
    // STPCardValidator.swift: "if binRange.brand == .unknown &&
    // validatingCardBrand { return .invalid }" — this fires before length or
    // Luhn are even considered.
    XCTAssertEqual(CardValidation.numberStatus("1234567890123452"), .invalid)
  }

  func testASingleDigitMatchingNoKnownBrandIsInvalidImmediatelyNotIncomplete() {
    // The surprising case worth pinning down explicitly: Stripe rejects this
    // from the very first character, not after enough digits to be sure.
    XCTAssertEqual(CardValidation.numberStatus("1"), .invalid)
  }

  func testALongUnrecognizedBrandDigitStringIsInvalid() {
    XCTAssertEqual(CardValidation.numberStatus("123456789012345678"), .invalid)
  }

  // ---------------------------------------------------------------------
  // normalizedBrand — the icon/CVC-length classifier
  // ---------------------------------------------------------------------

  func testEmptyPANReportsUnknownBrand() {
    XCTAssertEqual(CardValidation.normalizedBrand(""), "unknown")
  }

  func testASingle4AlreadyReportsVisa() {
    XCTAssertEqual(CardValidation.normalizedBrand("4"), "visa")
  }

  func testBrandMappingForEveryRecognizedNetwork() {
    let cases: [(String, String)] = [
      ("4242424242424242", "visa"),
      ("5555555555554444", "mastercard"),
      ("2223003122003222", "mastercard"),
      ("378282246310005", "amex"),
      ("6011111111111117", "discover"),
      ("6500000000000002", "discover"),
      ("3530111333300000", "jcb"),
      ("6200000000000005", "unionpay"),
      ("1234567890123452", "unknown")
    ]
    for (pan, expectedBrand) in cases {
      XCTAssertEqual(CardValidation.normalizedBrand(pan), expectedBrand, "brand for \(pan)")
    }
  }

  func testA67PrefixedPANReportsMastercardNeverMaestro() {
    // STPCardBrand has no Maestro case at all — Stripe classifies Maestro's
    // own network prefix as MasterCard by design. The `maestro` string still
    // exists in this package's CardBrand type and has an icon, but nothing
    // in this classifier can ever return it.
    XCTAssertEqual(CardValidation.normalizedBrand("6759649826438453"), "mastercard")
    XCTAssertEqual(CardValidation.normalizedBrand("676341724291545093"), "mastercard")
  }

  func testA622PrefixedPANReportsUnionPayNotDiscover() {
    // Worth pinning explicitly: the hardcoded Discover ranges are only "60"
    // and "64"/"65" (2-digit prefixes) — 622126-622925 is not in this table
    // at all, so a 62-prefixed PAN in that range falls to the "62" UnionPay
    // range instead, exactly like Android's own real classifier does.
    XCTAssertEqual(CardValidation.normalizedBrand("622147453553343498"), "unionpay")
    XCTAssertEqual(CardValidation.normalizedBrand("62292323072416244"), "unionpay")
  }

  // ---------------------------------------------------------------------
  // cvcStatus
  // ---------------------------------------------------------------------

  func testEmptyCVCIsEmptyRegardlessOfBrand() {
    XCTAssertEqual(CardValidation.cvcStatus("", brand: "visa"), .empty)
    XCTAssertEqual(CardValidation.cvcStatus("", brand: "amex"), .empty)
  }

  func testCVCAcceptsThreeOrFourDigitsRegardlessOfBrandOnIOS() {
    // Genuinely surprising, verified by running this against the real SDK —
    // not the behavior this test originally assumed. Two facts compound:
    // `STPCardValidator.minCVCLength()` is hardcoded to 3 for every brand
    // (STPCardValidator.swift), and this package's own `cvcStatus` maps ANY
    // brand string other than "amex" to `STPCardBrand.unknown` — whose
    // `maxCVCLength` is ALSO 4, same as `.amex`
    // (`case .amex, .unknown: return 4`). The net effect: on iOS, EVERY
    // brand string this function receives accepts 3 OR 4 digits as valid,
    // and the brand it's given makes no observable difference at all. This
    // is a pre-existing characteristic of the original code (this refactor
    // moved it verbatim, it did not change it) and is out of scope for this
    // ticket to fix — documented here so nobody has to rediscover it by
    // hand. Android's own CVC rule (verified in this package's Android
    // CardValidationTest.kt) genuinely does distinguish "amex" from
    // everything else, so the two platforms disagree here too.
    for brand in ["amex", "visa", "mastercard", "discover", "jcb", "unionpay", "unknown", ""] {
      XCTAssertEqual(CardValidation.cvcStatus("1", brand: brand), .incomplete, "1-digit CVC for brand=\(brand)")
      XCTAssertEqual(CardValidation.cvcStatus("12", brand: brand), .incomplete, "2-digit CVC for brand=\(brand)")
      XCTAssertEqual(CardValidation.cvcStatus("123", brand: brand), .valid, "3-digit CVC for brand=\(brand)")
      XCTAssertEqual(CardValidation.cvcStatus("1234", brand: brand), .valid, "4-digit CVC for brand=\(brand)")
      XCTAssertEqual(CardValidation.cvcStatus("12345", brand: brand), .invalid, "5-digit CVC for brand=\(brand)")
    }
  }

  // ---------------------------------------------------------------------
  // expiryStatus — real STPCardValidator behavior against the REAL wall
  // clock. Unlike Android's expiryStatus, iOS's has no injectable `now`
  // (STPCardValidator.currentYear()/currentMonth() read Date() internally,
  // with no override hook), so every case below is phrased RELATIVE to the
  // real current date so this suite never becomes a time bomb.
  // ---------------------------------------------------------------------

  private static let calendar = Calendar(identifier: .gregorian)

  private func monthYear(offsetMonths: Int) -> (month: Int, year: Int) {
    let now = Date()
    let shifted = Self.calendar.date(byAdding: .month, value: offsetMonths, to: now)!
    let month = Self.calendar.component(.month, from: shifted)
    let year = Self.calendar.component(.year, from: shifted) % 100
    return (month, year)
  }

  private func expiryDigits(offsetMonths: Int) -> String {
    let (month, year) = monthYear(offsetMonths: offsetMonths)
    return String(format: "%02d%02d", month, year)
  }

  func testEmptyExpiryIsEmpty() {
    XCTAssertEqual(CardValidation.expiryStatus(""), .empty)
  }

  func testASingleDigitOfTheMonthIsIncomplete() {
    XCTAssertEqual(CardValidation.expiryStatus("0"), .incomplete)
    XCTAssertEqual(CardValidation.expiryStatus("1"), .incomplete)
  }

  func testMonth00WithNoYearDigitsYetIsIncompleteNotInvalid() {
    // Genuinely surprising, verified by running this: Android's own
    // expiryStatus checks the MONTH first (`if (month !in 1..12) return
    // INVALID`), so "00" there is invalid before any year digit exists. iOS
    // delegates to the real `STPCardValidator.validationState(forExpirationYear:
    // inMonth:)`, whose very first branch switches on `sanitizedYear.count`
    // — with zero year digits typed, it returns `.incomplete` immediately,
    // never even looking at whether the month is 00. The month only gets
    // judged once two year digits exist (see
    // testTwoYearDigitsWithAnInvalidMonthIsInvalid below).
    XCTAssertEqual(CardValidation.expiryStatus("00"), .incomplete)
  }

  func testMonth13WithNoYearDigitsYetIsIncompleteNotInvalid() {
    XCTAssertEqual(CardValidation.expiryStatus("13"), .incomplete)
  }

  func testTwoYearDigitsWithAnInvalidMonthIsInvalid() {
    // Once two year digits exist, the month IS checked — and an invalid one
    // fails the `self.validationState(forExpirationMonth:) != .invalid`
    // guard inside STPCardValidator's year-validation branch, whatever the
    // year says. The year here is a placeholder ("00") precisely so this
    // test keeps asserting "invalid because of the month" and never starts
    // asserting "invalid because the year is in the past" instead.
    XCTAssertEqual(CardValidation.expiryStatus("0000"), .invalid)
    XCTAssertEqual(CardValidation.expiryStatus("1300"), .invalid)
  }

  func testAValidMonthAloneWithNoYearDigitsYetIsIncomplete() {
    XCTAssertEqual(CardValidation.expiryStatus("09"), .incomplete)
    XCTAssertEqual(CardValidation.expiryStatus("12"), .incomplete)
  }

  func testTheCurrentMonthAndYearIsValid() {
    XCTAssertEqual(CardValidation.numberStatus("4242424242424242"), .valid) // sanity: engine responsive
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(offsetMonths: 0)), .valid)
  }

  func testLastMonthOfTheCurrentPeriodIsInvalid() {
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(offsetMonths: -1)), .invalid)
  }

  func testNextMonthIsValid() {
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(offsetMonths: 1)), .valid)
  }

  func testThirteenMonthsFromNowIsValid() {
    // Crosses a year boundary in every calendar month this suite could run
    // in, unlike a fixed "next January" case.
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(offsetMonths: 13)), .valid)
  }

  func testThirteenMonthsAgoIsInvalid() {
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(offsetMonths: -13)), .invalid)
  }

  private func expiryDigits(yearsFromNow years: Int) -> String {
    let now = Date()
    let currentMonth = Self.calendar.component(.month, from: now)
    let shiftedYear = Self.calendar.date(byAdding: .year, value: years, to: now)!
    let year = Self.calendar.component(.year, from: shiftedYear) % 100
    return String(format: "%02d%02d", currentMonth, year)
  }

  func testUpTo50YearsInTheFutureIsValid() {
    // STPCardValidator.swift: "(yearInt > moddedYear) && (yearInt - moddedYear
    // <= 50)" — there IS an upper bound on iOS, verified by running exactly
    // this boundary. Android's own hand-written comparison
    // (`year > currentYear || …`, see this package's Android
    // CardValidationTest.kt) has no upper bound at all — a real, verified
    // platform divergence, not a guess from reading the source alone.
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(yearsFromNow: 50)), .valid)
  }

  func testMoreThan50YearsInTheFutureIsInvalid() {
    XCTAssertEqual(CardValidation.expiryStatus(expiryDigits(yearsFromNow: 51)), .invalid)
  }
}
