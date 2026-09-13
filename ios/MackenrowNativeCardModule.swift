import ExpoModulesCore
import Foundation

public class MackenrowNativeCardModule: Module {
  public func definition() -> ModuleDefinition {
    Name("MackenrowNativeCard")

    // `AsyncFunction`, not `Function`: Android's twin now needs a Promise to
    // resolve once `ensure()` has actually run on `Queues.MAIN` (there is no
    // lock left in `CardSessionRegistry.kt` to make a plain fire-and-forget
    // dispatch safe there — see that object's own header). The shared TS
    // signature has to describe ONE contract for both platforms, so this
    // side returns a Promise too, even though nothing here needed one on its
    // own: `DispatchQueue.main.async` was already fire-and-forget-safe here,
    // because it never shared a lock with anything.
    AsyncFunction("ensureSession") { (sessionId: String, promise: Promise) in
      DispatchQueue.main.async {
        CardSessionRegistry.shared.ensure(sessionId)
        promise.resolve(nil)
      }
    }

    AsyncFunction("tokenize") { (sessionId: String, timeoutMs: Int, promise: Promise) in
      DispatchQueue.main.async {
        CardSessionRegistry.shared.tokenize(sessionId, timeoutMs: timeoutMs, promise: promise)
      }
    }

    AsyncFunction("reset") { (sessionId: String, promise: Promise) in
      DispatchQueue.main.async {
        CardSessionRegistry.shared.reset(sessionId, promise: promise)
      }
    }

    AsyncFunction("focus") { (sessionId: String, field: String, promise: Promise) in
      DispatchQueue.main.async {
        CardSessionRegistry.shared.focus(sessionId, fieldName: field, promise: promise)
      }
    }

    AsyncFunction("disposeSession") { (sessionId: String, promise: Promise) in
      DispatchQueue.main.async {
        CardSessionRegistry.shared.dispose(sessionId, promise: promise)
      }
    }

    View(MackenrowNativeCardView.self) {
      Events("onStateChange")

      Prop("sessionId") { (view: MackenrowNativeCardView, value: String) in view.setSessionId(value) }
      Prop("field") { (view: MackenrowNativeCardView, value: String) in view.setField(value) }
      Prop("disabled") { (view: MackenrowNativeCardView, value: Bool) in view.setDisabled(value) }
      Prop("placeholder") { (view: MackenrowNativeCardView, value: String) in view.setPlaceholder(value) }
      Prop("accessibilityLabel") { (view: MackenrowNativeCardView, value: String) in
        view.setAccessibilityLabel(value)
      }
      Prop("accessibilityHint") { (view: MackenrowNativeCardView, value: String) in
        view.setAccessibilityHint(value)
      }
      Prop("enteredAccessibilityValue") { (view: MackenrowNativeCardView, value: String) in
        view.setEnteredAccessibilityValue(value)
      }
      Prop("invalidAccessibilityValue") { (view: MackenrowNativeCardView, value: String) in
        view.setInvalidAccessibilityValue(value)
      }
      Prop("textColor") { (view: MackenrowNativeCardView, value: UIColor) in view.setTextColor(value) }
      Prop("placeholderColor") { (view: MackenrowNativeCardView, value: UIColor) in
        view.setPlaceholderColor(value)
      }
      Prop("cursorColor") { (view: MackenrowNativeCardView, value: UIColor) in view.setCursorColor(value) }
      Prop("fontSize") { (view: MackenrowNativeCardView, value: Double) in view.setFontSize(value) }
      Prop("fontFamily") { (view: MackenrowNativeCardView, value: String?) in view.setFontFamily(value) }
      Prop("fontWeight") { (view: MackenrowNativeCardView, value: String?) in view.setFontWeight(value) }
      Prop("fontStyle") { (view: MackenrowNativeCardView, value: String?) in view.setFontStyle(value) }
      Prop("textAlign") { (view: MackenrowNativeCardView, value: String?) in view.setTextAlign(value) }
      Prop("letterSpacing") { (view: MackenrowNativeCardView, value: Double?) in
        view.setLetterSpacing(value)
      }
    }
  }
}
