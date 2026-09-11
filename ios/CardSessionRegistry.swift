import ExpoModulesCore
import StripePayments
import UIKit

internal enum SensitiveField: String, CaseIterable {
  case number
  case expiry
  case cvc
}

internal protocol SensitiveFieldHandle: AnyObject {
  var sensitiveField: SensitiveField { get }
  func sensitiveDigits() -> String
  func isSensitiveValid() -> Bool
  func clearSensitiveValue()
  func focusSensitiveField()
  func blurSensitiveField()
  func revealValidationError()
}

private final class WeakSensitiveField {
  weak var value: SensitiveFieldHandle?
  init(_ value: SensitiveFieldHandle) { self.value = value }
}

internal final class CardSessionRegistry {
  static let shared = CardSessionRegistry()
  private static let safeMessage = "Native card form operation failed."

  private final class PendingOperation {
    let id = UUID()
    let promise: Promise
    var timeout: DispatchWorkItem?
    var settled = false

    init(promise: Promise) {
      self.promise = promise
    }
  }

  private final class Session {
    var fields: [SensitiveField: WeakSensitiveField] = [:]
    var pending: PendingOperation?
    var disposed = false
  }

  private var sessions: [String: Session] = [:]

  private init() {}

  func ensure(_ sessionId: String) {
    guard !sessionId.isEmpty else { return }
    if sessions[sessionId] == nil || sessions[sessionId]?.disposed == true {
      sessions[sessionId] = Session()
    }
  }

  func register(_ field: SensitiveFieldHandle, sessionId: String) {
    ensure(sessionId)
    sessions[sessionId]?.fields[field.sensitiveField] = WeakSensitiveField(field)
  }

  func unregister(_ field: SensitiveFieldHandle, sessionId: String) {
    guard sessions[sessionId]?.fields[field.sensitiveField]?.value === field else { return }
    sessions[sessionId]?.fields.removeValue(forKey: field.sensitiveField)
  }

  func focusNext(_ sessionId: String, after field: SensitiveField) {
    let next: SensitiveField?
    switch field {
    case .number: next = .expiry
    case .expiry: next = .cvc
    case .cvc: next = nil
    }
    if let next {
      sessions[sessionId]?.fields[next]?.value?.focusSensitiveField()
    } else {
      sessions[sessionId]?.fields[.cvc]?.value?.blurSensitiveField()
    }
  }

  // Same brand this session's number field reports — CardValidation's single
  // classifier, taken straight from Stripe's own. Only "amex" changes
  // anything for a caller of this method (the CVC field's max length), so
  // anything else Stripe doesn't natively distinguish safely collapses to
  // "unknown".
  func brand(_ sessionId: String?) -> String {
    guard
      let sessionId,
      let value = sessions[sessionId]?.fields[.number]?.value?.sensitiveDigits()
    else { return "unknown" }
    return CardValidation.normalizedBrand(value)
  }

  func reset(_ sessionId: String, promise: Promise) {
    guard let session = activeSession(sessionId, promise: promise) else { return }
    cancel(session, code: "cancelled")
    liveFields(session).forEach { $0.clearSensitiveValue() }
    promise.resolve(nil)
  }

  func focus(_ sessionId: String, fieldName: String, promise: Promise) {
    guard
      let session = activeSession(sessionId, promise: promise),
      let field = SensitiveField(rawValue: fieldName),
      let target = session.fields[field]?.value
    else {
      reject(promise, code: "card_incomplete")
      return
    }
    target.focusSensitiveField()
    promise.resolve(nil)
  }

  func dispose(_ sessionId: String, promise: Promise) {
    guard let session = sessions.removeValue(forKey: sessionId) else {
      promise.resolve(nil)
      return
    }
    session.disposed = true
    cancel(session, code: "cancelled")
    liveFields(session).forEach { $0.clearSensitiveValue() }
    session.fields.removeAll()
    promise.resolve(nil)
  }

  func tokenize(_ sessionId: String, timeoutMs: Int, promise: Promise) {
    guard let session = activeSession(sessionId, promise: promise) else { return }
    guard session.pending == nil else {
      reject(promise, code: "busy")
      return
    }
    let fields = Dictionary(uniqueKeysWithValues: SensitiveField.allCases.map {
      ($0, session.fields[$0]?.value)
    })
    guard fields.values.allSatisfy({ $0 != nil }) else {
      fields.values.compactMap { $0 }.forEach { $0.revealValidationError() }
      reject(promise, code: "card_incomplete")
      return
    }

    let number = fields[.number]!!.sensitiveDigits()
    let expiry = fields[.expiry]!!.sensitiveDigits()
    let cvc = fields[.cvc]!!.sensitiveDigits()
    guard
      fields.values.compactMap({ $0 }).allSatisfy({ $0.isSensitiveValid() }),
      let month = UInt(expiry.prefix(2)),
      let year = UInt(expiry.suffix(2))
    else {
      fields.values.compactMap { $0 }.forEach { $0.revealValidationError() }
      reject(promise, code: "card_incomplete")
      return
    }
    guard !(STPAPIClient.shared.publishableKey ?? "").isEmpty else {
      reject(promise, code: "stripe_not_configured")
      return
    }

    let operation = PendingOperation(promise: promise)
    session.pending = operation
    let boundedTimeout = max(1_000, min(timeoutMs, 120_000))
    let timeout = DispatchWorkItem { [weak self] in
      self?.settleFailure(sessionId, operationId: operation.id, code: "timeout")
    }
    operation.timeout = timeout
    DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(boundedTimeout), execute: timeout)

    let card = STPPaymentMethodCardParams()
    card.number = number
    card.expMonth = NSNumber(value: month)
    card.expYear = NSNumber(value: year + 2000)
    card.cvc = cvc
    let params = STPPaymentMethodParams(card: card, billingDetails: nil, metadata: nil)

    STPAPIClient.shared.createPaymentMethod(with: params) { [weak self] paymentMethod, error in
      DispatchQueue.main.async {
        guard let identifier = paymentMethod?.stripeId, !identifier.isEmpty else {
          let code = (error as NSError?)?.code == NSURLErrorNotConnectedToInternet
            ? "network_error"
            : "tokenization_failed"
          self?.settleFailure(sessionId, operationId: operation.id, code: code)
          return
        }
        self?.settleSuccess(sessionId, operationId: operation.id, paymentMethodId: identifier)
      }
    }
  }

  private func activeSession(_ sessionId: String, promise: Promise) -> Session? {
    guard let session = sessions[sessionId], !session.disposed else {
      reject(promise, code: "disposed")
      return nil
    }
    return session
  }

  private func liveFields(_ session: Session) -> [SensitiveFieldHandle] {
    session.fields.values.compactMap(\.value)
  }

  private func takePending(_ sessionId: String, operationId: UUID) -> PendingOperation? {
    guard
      let session = sessions[sessionId],
      !session.disposed,
      let pending = session.pending,
      pending.id == operationId,
      !pending.settled
    else { return nil }
    pending.settled = true
    pending.timeout?.cancel()
    session.pending = nil
    return pending
  }

  private func settleSuccess(_ sessionId: String, operationId: UUID, paymentMethodId: String) {
    guard let pending = takePending(sessionId, operationId: operationId) else { return }
    pending.promise.resolve(["paymentMethodId": paymentMethodId])
  }

  private func settleFailure(_ sessionId: String, operationId: UUID, code: String) {
    guard let pending = takePending(sessionId, operationId: operationId) else { return }
    reject(pending.promise, code: code)
  }

  private func cancel(_ session: Session, code: String) {
    guard let pending = session.pending, !pending.settled else { return }
    pending.settled = true
    pending.timeout?.cancel()
    session.pending = nil
    reject(pending.promise, code: code)
  }

  private func reject(_ promise: Promise, code: String) {
    promise.reject(code, CardSessionRegistry.safeMessage)
  }
}
