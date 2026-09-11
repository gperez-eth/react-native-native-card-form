import StripePayments

/// The status a sensitive field (PAN, expiry, or CVC) can present. Mirrors
/// `docs/VALIDATION.md`.
internal enum FieldStatus: String {
  case empty
  case incomplete
  case invalid
  case valid
}

/// Pure decision logic behind every sensitive field, extracted out of
/// `MackenrowNativeCardView` so it can be unit tested without a live
/// `UITextField` or a rendered view. Every branch here is Stripe's own
/// answer, composed but never second-guessed. See `docs/VALIDATION.md`.
internal enum CardValidation {
  static func numberStatus(_ value: String) -> FieldStatus {
    guard !value.isEmpty else { return .empty }
    switch STPCardValidator.validationState(forNumber: value, validatingCardBrand: true) {
    case .valid: return .valid
    case .invalid: return .invalid
    case .incomplete: return .incomplete
    }
  }

  static func expiryStatus(_ value: String) -> FieldStatus {
    guard !value.isEmpty else { return .empty }
    let month = value.count >= 2 ? String(value.prefix(2)) : value
    let year = value.count > 2 ? String(value.dropFirst(2)) : ""
    switch STPCardValidator.validationState(forExpirationYear: year, inMonth: month) {
    case .valid: return .valid
    case .invalid: return .invalid
    case .incomplete: return .incomplete
    }
  }

  static func cvcStatus(_ value: String, brand: String) -> FieldStatus {
    guard !value.isEmpty else { return .empty }
    let stripeBrand: STPCardBrand = brand == "amex" ? .amex : .unknown
    switch STPCardValidator.validationState(forCVC: value, cardBrand: stripeBrand) {
    case .valid: return .valid
    case .invalid: return .invalid
    case .incomplete: return .incomplete
    }
  }

  // Delegates entirely to Stripe's own classifier: no BIN-prefix guessing. A
  // brand Stripe recognizes but this package can't render an icon for
  // (dinersClub, cartesBancaires — see STPCardBrand's cases) falls to
  // "unknown", same as anything Stripe itself doesn't recognize. Note there is
  // no `.maestro` case in STPCardBrand at all: Stripe classifies those BINs as
  // `.mastercard` on purpose (STPBINController hardcodes "67 -> mastercard //
  // Maestro"), and `numberStatus` above already gets the number field's own
  // validity straight from Stripe — this function only decides which icon to
  // show.
  static func normalizedBrand(_ value: String) -> String {
    switch STPCardValidator.brand(forNumber: value) {
    case .visa: return "visa"
    case .mastercard: return "mastercard"
    case .amex: return "amex"
    case .discover: return "discover"
    case .JCB: return "jcb"
    case .unionPay: return "unionpay"
    default: return "unknown"
    }
  }
}
