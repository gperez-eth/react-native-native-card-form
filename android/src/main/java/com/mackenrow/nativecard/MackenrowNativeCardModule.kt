package com.mackenrow.nativecard

import android.graphics.Color
import expo.modules.kotlin.Promise
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Every function below that reaches `CardSessionRegistry` is chained to
 * `.runOnQueue(Queues.MAIN)` — the same mechanism `expo-image`'s own module
 * uses for its View-touching calls, and the one `ViewDefinitionBuilder`
 * applies automatically to every prop/function declared inside a `View{}`
 * block. It is what lets `CardSessionRegistry` itself carry no locks at all:
 * see that object's own header for why. A new function added here needs
 * exactly this one thing to stay safe; `scripts/thread-confinement-check.js`
 * fails the build if it is missing.
 */
class MackenrowNativeCardModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("MackenrowNativeCard")

    AsyncFunction("ensureSession") { sessionId: String, promise: Promise ->
      CardSessionRegistry.ensure(sessionId)
      promise.resolve(null)
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("tokenize") { sessionId: String, timeoutMs: Int, promise: Promise ->
      val context = appContext.reactContext
      if (context == null) {
        promise.reject("disposed", "Native card form operation failed.", null)
      } else {
        CardSessionRegistry.tokenize(context, sessionId, timeoutMs, promise)
      }
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("reset") { sessionId: String, promise: Promise ->
      CardSessionRegistry.reset(sessionId, promise)
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("focus") { sessionId: String, field: String, promise: Promise ->
      CardSessionRegistry.focus(sessionId, field, promise)
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("disposeSession") { sessionId: String, promise: Promise ->
      CardSessionRegistry.dispose(sessionId, promise)
    }.runOnQueue(Queues.MAIN)

    View(MackenrowNativeCardView::class) {
      Events("onStateChange")

      Prop("sessionId") { view, value: String -> view.setSessionId(value) }
      Prop("field") { view, value: String -> view.setField(value) }
      Prop("disabled") { view, value: Boolean -> view.setDisabled(value) }
      Prop("placeholder") { view, value: String -> view.setPlaceholder(value) }
      Prop("accessibilityLabel") { view, value: String -> view.setAccessibilityLabel(value) }
      Prop("accessibilityHint") { view, value: String -> view.setAccessibilityHint(value) }
      Prop("enteredAccessibilityValue") { view, value: String -> view.setEnteredAccessibilityValue(value) }
      Prop("invalidAccessibilityValue") { view, value: String -> view.setInvalidAccessibilityValue(value) }
      Prop("textColor") { view, value: String -> view.setTextColor(parseColor(value, "textColor")) }
      Prop("placeholderColor") {
        view, value: String -> view.setPlaceholderColor(parseColor(value, "placeholderColor"))
      }
      Prop("cursorColor") { view, value: String -> view.setCursorColor(parseColor(value, "cursorColor")) }
      Prop("fontSize") { view, value: Double -> view.setFontSize(value.toFloat()) }
      Prop("fontFamily") { view, value: String? -> view.setFontFamily(value) }
      Prop("fontWeight") { view, value: String? -> view.setFontWeight(value) }
      Prop("fontStyle") { view, value: String? -> view.setFontStyle(value) }
      Prop("textAlign") { view, value: String? -> view.setTextAlign(value) }
      Prop("letterSpacing") { view, value: Double? -> view.setLetterSpacing(value?.toFloat()) }
    }
  }

  private fun parseColor(value: String, propertyName: String): Int {
    try {
      return Color.parseColor(value)
    } catch (_: IllegalArgumentException) {
      parseRgbFunction(value)?.let { return it }
    }

    throw IllegalArgumentException(
      "Invalid $propertyName value. Use a React Native color such as #RRGGBB, #AARRGGBB or rgba(...)."
    )
  }

  private fun parseRgbFunction(value: String): Int? {
    val match = Regex("""rgba?\(([^)]+)\)""").matchEntire(value.trim()) ?: return null
    val components = match.groupValues[1].split(',').map(String::trim)
    if (components.size !in 3..4) return null

    fun channel(component: String): Int? {
      val percentage = component.endsWith('%')
      val number = component.removeSuffix("%").toFloatOrNull() ?: return null
      val normalized = if (percentage) number * 2.55f else number
      return normalized.coerceIn(0f, 255f).toInt()
    }

    fun alpha(component: String): Int? {
      val percentage = component.endsWith('%')
      val number = component.removeSuffix("%").toFloatOrNull() ?: return null
      val normalized = if (percentage) number / 100f else number
      return (normalized.coerceIn(0f, 1f) * 255f).toInt()
    }

    val red = channel(components[0]) ?: return null
    val green = channel(components[1]) ?: return null
    val blue = channel(components[2]) ?: return null
    val opacity = if (components.size == 4) alpha(components[3]) ?: return null else 255
    return Color.argb(opacity, red, green, blue)
  }
}
