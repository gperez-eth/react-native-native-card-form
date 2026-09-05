import ExpoModulesCore
import Foundation

public class MackenrowNativeCardModule: Module {
  public func definition() -> ModuleDefinition {
    Name("MackenrowNativeCard")

    Function("ensureSession") { (sessionId: String) in
      DispatchQueue.main.async {
        CardSessionRegistry.shared.ensure(sessionId)
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
