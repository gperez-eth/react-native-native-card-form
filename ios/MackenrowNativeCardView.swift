import ExpoModulesCore
import StripePayments
import UIKit

private final class NoExportTextField: UITextField {
  override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
    if action == #selector(copy(_:)) || action == #selector(cut(_:)) {
      return false
    }
    return super.canPerformAction(action, withSender: sender)
  }

  override func encodeRestorableState(with coder: NSCoder) {
    // Sensitive text is intentionally excluded from UIKit state restoration.
  }
}

/**
 * A single sensitive input. React Native owns layout, chrome, labels and errors.
 * The package's JS entry point never exports this native view.
 */
public final class MackenrowNativeCardView: ExpoView, UITextFieldDelegate, SensitiveFieldHandle {
  private let input = NoExportTextField()
  private var sessionId: String?
  private var configuredField = SensitiveField.number
  private var touched = false
  private var revealError = false
  private var autoAdvanced = false
  private var previousIntrinsicStatus = FieldStatus.empty
  private var lastEvent: [String: AnyHashable]?
  private var fontSize: CGFloat = 17
  private var fontFamily: String?
  private var fontWeight: String?
  private var fontStyle: String?
  private var textAlignment: String?
  private var letterSpacing: CGFloat?
  private var placeholderColor: UIColor = .placeholderText
  private var placeholderText = ""
  private var enteredAccessibilityValue = ""
  private var invalidAccessibilityValue = ""

  public let onStateChange = EventDispatcher()

  internal var sensitiveField: SensitiveField { configuredField }

  public required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    clipsToBounds = false
    restorationIdentifier = nil
    input.translatesAutoresizingMaskIntoConstraints = false
    input.delegate = self
    input.borderStyle = .none
    input.adjustsFontForContentSizeCategory = true
    input.clearButtonMode = .never
    input.autocorrectionType = .no
    input.spellCheckingType = .no
    input.smartDashesType = .no
    input.smartQuotesType = .no
    input.smartInsertDeleteType = .no
    addSubview(input)
    NSLayoutConstraint.activate([
      input.leadingAnchor.constraint(equalTo: leadingAnchor),
      input.trailingAnchor.constraint(equalTo: trailingAnchor),
      input.topAnchor.constraint(equalTo: topAnchor),
      input.bottomAnchor.constraint(equalTo: bottomAnchor),
      input.heightAnchor.constraint(greaterThanOrEqualToConstant: 44)
    ])
    applyFieldConfiguration()
    applyFont()
  }

  public func setSessionId(_ value: String) {
    guard sessionId != value else { return }
    if let sessionId { CardSessionRegistry.shared.unregister(self, sessionId: sessionId) }
    sessionId = value
    CardSessionRegistry.shared.register(self, sessionId: value)
  }

  public func setField(_ value: String) {
    guard let next = SensitiveField(rawValue: value), configuredField != next else { return }
    if let sessionId { CardSessionRegistry.shared.unregister(self, sessionId: sessionId) }
    clearSensitiveValue()
    configuredField = next
    applyFieldConfiguration()
    if let sessionId { CardSessionRegistry.shared.register(self, sessionId: sessionId) }
    emitSanitizedState(force: true)
  }

  public func setDisabled(_ value: Bool) {
    input.isEnabled = !value
  }

  public func setPlaceholder(_ value: String) {
    placeholderText = value
    input.attributedPlaceholder = nil
    input.placeholder = value
    applyPlaceholder()
  }

  public func setAccessibilityLabel(_ value: String) {
    input.accessibilityLabel = value
  }

  public func setAccessibilityHint(_ value: String) {
    input.accessibilityHint = value
  }

  public func setEnteredAccessibilityValue(_ value: String) {
    enteredAccessibilityValue = value
    updateProtectedAccessibilityValue()
  }

  public func setInvalidAccessibilityValue(_ value: String) {
    invalidAccessibilityValue = value
    updateProtectedAccessibilityValue()
  }

  public func setTextColor(_ value: UIColor) {
    input.textColor = value
  }

  public func setPlaceholderColor(_ value: UIColor) {
    placeholderColor = value
    applyPlaceholder()
  }

  public func setCursorColor(_ value: UIColor) {
    input.tintColor = value
  }

  public func setFontSize(_ value: Double) {
    fontSize = CGFloat(value)
    applyFont()
  }

  public func setFontFamily(_ value: String?) {
    fontFamily = value
    applyFont()
  }

  public func setFontWeight(_ value: String?) {
    fontWeight = value
    applyFont()
  }

  public func setFontStyle(_ value: String?) {
    fontStyle = value
    applyFont()
  }

  public func setTextAlign(_ value: String?) {
    textAlignment = value
    switch value {
    case "center":
      input.textAlignment = .center
    case "right":
      input.textAlignment = .right
    case "left":
      input.textAlignment = .left
    default:
      input.textAlignment = .natural
    }
  }

  public func setLetterSpacing(_ value: Double?) {
    letterSpacing = value.map { CGFloat($0) }
    input.defaultTextAttributes[.kern] = letterSpacing ?? 0
    input.typingAttributes = input.defaultTextAttributes
    applyPlaceholder()
  }

  public override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil {
      if let sessionId { CardSessionRegistry.shared.unregister(self, sessionId: sessionId) }
      clearSensitiveValue()
    } else if let sessionId {
      CardSessionRegistry.shared.register(self, sessionId: sessionId)
    }
  }

  internal func sensitiveDigits() -> String {
    STPCardValidator.sanitizedNumericString(for: input.text ?? "")
  }

  internal func isSensitiveValid() -> Bool {
    intrinsicStatus() == .valid
  }

  internal func clearSensitiveValue() {
    input.text = nil
    touched = false
    revealError = false
    autoAdvanced = false
    previousIntrinsicStatus = .empty
    updateProtectedAccessibilityValue()
    emitSanitizedState(force: true)
  }

  internal func focusSensitiveField() {
    if input.isEnabled { input.becomeFirstResponder() }
  }

  internal func blurSensitiveField() {
    input.resignFirstResponder()
  }

  internal func revealValidationError() {
    touched = true
    revealError = true
    emitSanitizedState(force: true)
  }

  public func textFieldDidBeginEditing(_ textField: UITextField) {
    emitSanitizedState()
  }

  public func textFieldDidEndEditing(_ textField: UITextField) {
    touched = true
    revealError = true
    emitSanitizedState()
  }

  public func textField(
    _ textField: UITextField,
    shouldChangeCharactersIn range: NSRange,
    replacementString string: String
  ) -> Bool {
    guard
      let current = textField.text,
      let swiftRange = Range(range, in: current)
    else { return false }

    let prospective = current.replacingCharacters(in: swiftRange, with: string)
    let digitsBeforeRange = current[..<swiftRange.lowerBound].filter(\.isNumber).count
    let insertedDigits = string.filter(\.isNumber).count
    let logicalCursor = digitsBeforeRange + insertedDigits
    let formatted = formattedValue(prospective)
    textField.text = formatted
    if let position = textField.position(
      from: textField.beginningOfDocument,
      offset: cursorOffset(in: formatted, logicalDigit: logicalCursor)
    ) {
      textField.selectedTextRange = textField.textRange(from: position, to: position)
    }

    let currentStatus = intrinsicStatus()
    let isPaste = string.count > 1
    if currentStatus != .valid { autoAdvanced = false }
    let shouldAdvance =
      previousIntrinsicStatus != .valid &&
      currentStatus == .valid &&
      !isPaste &&
      !autoAdvanced
    previousIntrinsicStatus = currentStatus
    if shouldAdvance {
      autoAdvanced = true
      if let sessionId { CardSessionRegistry.shared.focusNext(sessionId, after: configuredField) }
    } else if isPaste && currentStatus == .valid {
      autoAdvanced = true
    }
    updateProtectedAccessibilityValue()
    emitSanitizedState()
    return false
  }

  private func applyFieldConfiguration() {
    input.keyboardType = .numberPad
    input.isSecureTextEntry = configuredField == .cvc
    input.textContentType = configuredField == .number ? .creditCardNumber : .none
  }

  private func applyFont() {
    var base = fontFamily
      .flatMap { UIFont(name: $0, size: fontSize) }
      ?? UIFont.systemFont(ofSize: fontSize, weight: resolvedFontWeight())
    var traits = base.fontDescriptor.symbolicTraits
    if fontStyle == "italic" { traits.insert(.traitItalic) }
    if isBold { traits.insert(.traitBold) }
    if let descriptor = base.fontDescriptor.withSymbolicTraits(traits) {
      base = UIFont(descriptor: descriptor, size: fontSize)
    }
    input.font = UIFontMetrics(forTextStyle: .body).scaledFont(for: base)
    input.adjustsFontForContentSizeCategory = true
    input.defaultTextAttributes[.kern] = letterSpacing ?? 0
    input.typingAttributes = input.defaultTextAttributes
    applyPlaceholder()
  }

  private var isBold: Bool {
    guard let fontWeight else { return false }
    return fontWeight == "bold" || (Int(fontWeight) ?? 400) >= 600
  }

  private func resolvedFontWeight() -> UIFont.Weight {
    switch fontWeight {
    case "100": return .ultraLight
    case "200": return .thin
    case "300": return .light
    case "500": return .medium
    case "600": return .semibold
    case "700", "bold": return .bold
    case "800": return .heavy
    case "900": return .black
    default: return .regular
    }
  }

  private func applyPlaceholder() {
    let value = placeholderText
    guard !value.isEmpty else {
      input.attributedPlaceholder = nil
      return
    }
    var attributes: [NSAttributedString.Key: Any] = [.foregroundColor: placeholderColor]
    attributes[.kern] = letterSpacing ?? 0
    attributes[.font] = input.font ?? UIFont.systemFont(ofSize: fontSize)
    input.placeholder = nil
    input.attributedPlaceholder = NSAttributedString(string: value, attributes: attributes)
  }

  private func formattedValue(_ value: String) -> String {
    let raw = STPCardValidator.sanitizedNumericString(for: value)
    switch configuredField {
    case .number:
      let limited = String(raw.prefix(19))
      if normalizedBrand(limited) == "amex" {
        return limited.enumerated().reduce(into: "") { result, pair in
          if pair.offset == 4 || pair.offset == 10 { result.append(" ") }
          result.append(pair.element)
        }
      }
      return stride(from: 0, to: limited.count, by: 4).map { offset in
        let start = limited.index(limited.startIndex, offsetBy: offset)
        let end = limited.index(start, offsetBy: min(4, limited.distance(from: start, to: limited.endIndex)))
        return String(limited[start..<end])
      }.joined(separator: " ")
    case .expiry:
      let limited = String(raw.prefix(4))
      return limited.count > 2 ? "\(limited.prefix(2))/\(limited.dropFirst(2))" : limited
    case .cvc:
      return String(raw.prefix(4))
    }
  }

  private func cursorOffset(in value: String, logicalDigit: Int) -> Int {
    guard logicalDigit > 0 else { return 0 }
    var seen = 0
    for (offset, character) in value.enumerated() {
      if character.isNumber { seen += 1 }
      if seen == logicalDigit { return offset + 1 }
    }
    return value.count
  }

  // The switch below (and CardValidation itself) holds every branch of "is
  // this PAN/expiry/CVC complete, valid, or invalid" — deliberately pure and
  // free of `input`/`sessionId` so it's covered by plain XCTest instead of
  // only being exercised by hand on a device.
  private func intrinsicStatus() -> FieldStatus {
    let value = sensitiveDigits()
    switch configuredField {
    case .number:
      return CardValidation.numberStatus(value)
    case .expiry:
      return CardValidation.expiryStatus(value)
    case .cvc:
      return CardValidation.cvcStatus(value, brand: CardSessionRegistry.shared.brand(sessionId))
    }
  }

  private func presentedStatus() -> FieldStatus {
    let intrinsic = intrinsicStatus()
    return intrinsic == .invalid && input.isFirstResponder && !revealError ? .incomplete : intrinsic
  }

  private func normalizedBrand(_ value: String) -> String {
    CardValidation.normalizedBrand(value)
  }

  private func updateProtectedAccessibilityValue() {
    var parts: [String] = []
    if !sensitiveDigits().isEmpty { parts.append(enteredAccessibilityValue) }
    if presentedStatus() == .invalid { parts.append(invalidAccessibilityValue) }
    input.accessibilityValue = parts.filter { !$0.isEmpty }.joined(separator: ". ")
  }

  private func emitSanitizedState(force: Bool = false) {
    let payload: [String: AnyHashable] = [
      "field": configuredField.rawValue,
      "status": presentedStatus().rawValue,
      "brand": configuredField == .number ? normalizedBrand(sensitiveDigits()) : "unknown",
      "focused": input.isFirstResponder,
      "touched": touched
    ]
    if force || payload != lastEvent {
      lastEvent = payload
      updateProtectedAccessibilityValue()
      onStateChange(payload)
    }
  }
}
