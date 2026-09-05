package com.mackenrow.nativecard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.stripe.android.CardUtils
import com.stripe.android.model.CardBrand
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.util.Calendar

private enum class FieldStatus(val wireName: String) {
  EMPTY("empty"),
  INCOMPLETE("incomplete"),
  INVALID("invalid"),
  VALID("valid")
}

/**
 * One sensitive input only. React Native owns all layout/chrome/labels/errors.
 * This class is package-private in practice: the JS entry point never exports it.
 */
internal class MackenrowNativeCardView(
  context: Context,
  appContext: AppContext
) : ExpoView(context, appContext), SensitiveFieldHandle {
  private val input = NoExportEditText(context)
  private var sessionId: String? = null
  private var configuredField = SensitiveField.NUMBER
  private var changingText = false
  private var pastedEdit = false
  private var touched = false
  private var revealError = false
  private var autoAdvanced = false
  private var previousIntrinsicStatus = FieldStatus.EMPTY
  private var lastEvent: Map<String, Any>? = null
  private var accessibilityLabelText = ""
  private var accessibilityHintText = ""
  private var enteredAccessibilityValue = ""
  private var invalidAccessibilityValue = ""
  private var placeholderColor = Color.GRAY
  private var fontSizeSp = 16f
  private var fontFamilyName: String? = null
  private var fontWeightName: String? = null
  private var fontStyleName: String? = null
  private var letterSpacingPx: Float? = null

  val onStateChange by EventDispatcher<Map<String, Any>>()

  override val sensitiveField: SensitiveField
    get() = configuredField

  init {
    addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    minimumHeight = dp(48)
    input.minHeight = dp(48)
    input.setSingleLine(true)
    input.setSaveEnabled(false)
    input.isSaveFromParentEnabled = false
    input.setSelectAllOnFocus(false)
    input.customSelectionActionModeCallback = BlockCopyCutActionMode()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      input.customInsertionActionModeCallback = BlockCopyCutActionMode()
    }
    input.setOnFocusChangeListener { _, focused ->
      if (!focused) {
        touched = true
        revealError = true
      }
      emitSanitizedState()
    }
    input.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit

      override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
        if (!changingText) pastedEdit = count > 1
      }

      override fun afterTextChanged(value: Editable?) {
        if (changingText) return
        normalizeTextPreservingCursor()
        val current = intrinsicStatus()
        if (current != FieldStatus.VALID) autoAdvanced = false
        val shouldAdvance =
          previousIntrinsicStatus != FieldStatus.VALID &&
            current == FieldStatus.VALID &&
            !pastedEdit &&
            !autoAdvanced
        previousIntrinsicStatus = current
        if (shouldAdvance) {
          autoAdvanced = true
          sessionId?.let { CardSessionRegistry.focusNext(it, configuredField) }
        } else if (pastedEdit && current == FieldStatus.VALID) {
          autoAdvanced = true
        }
        pastedEdit = false
        emitSanitizedState()
      }
    })
    configureForField()
  }

  fun setSessionId(value: String) {
    if (sessionId == value) return
    sessionId?.let { CardSessionRegistry.unregister(it, this) }
    sessionId = value
    CardSessionRegistry.register(value, this)
  }

  fun setField(value: String) {
    val next = SensitiveField.fromWireName(value) ?: return
    if (configuredField == next) return
    sessionId?.let { CardSessionRegistry.unregister(it, this) }
    clearSensitiveValue()
    configuredField = next
    configureForField()
    sessionId?.let { CardSessionRegistry.register(it, this) }
    emitSanitizedState(force = true)
  }

  fun setDisabled(value: Boolean) {
    input.isEnabled = !value
  }

  fun setPlaceholder(value: String) {
    input.hint = value
    input.setHintTextColor(placeholderColor)
  }

  fun setAccessibilityLabel(value: String) {
    accessibilityLabelText = value
    updateAccessibilityDescription()
  }

  fun setAccessibilityHint(value: String) {
    accessibilityHintText = value
    updateAccessibilityDescription()
  }

  fun setEnteredAccessibilityValue(value: String) {
    enteredAccessibilityValue = value
    updateAccessibilityDescription()
  }

  fun setInvalidAccessibilityValue(value: String) {
    invalidAccessibilityValue = value
    updateAccessibilityDescription()
  }

  fun setTextColor(value: Int) {
    input.setTextColor(value)
  }

  fun setPlaceholderColor(value: Int) {
    placeholderColor = value
    input.setHintTextColor(value)
  }

  fun setCursorColor(value: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) input.textCursorDrawable?.setTint(value)
  }

  fun setFontSize(value: Float) {
    fontSizeSp = value
    input.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
    applyLetterSpacing()
  }

  fun setFontFamily(value: String?) {
    fontFamilyName = value
    applyTypeface()
  }

  fun setFontWeight(value: String?) {
    fontWeightName = value
    applyTypeface()
  }

  fun setFontStyle(value: String?) {
    fontStyleName = value
    applyTypeface()
  }

  fun setTextAlign(value: String?) {
    input.gravity = Gravity.CENTER_VERTICAL or when (value) {
      "center" -> Gravity.CENTER_HORIZONTAL
      "right" -> Gravity.END
      "left" -> Gravity.START
      else -> Gravity.START
    }
  }

  fun setLetterSpacing(value: Float?) {
    letterSpacingPx = value
    applyLetterSpacing()
  }

  private fun applyTypeface() {
    val weight = fontWeightName?.toIntOrNull()
    val style = (if (weight != null && weight >= 600 || fontWeightName == "bold") {
      Typeface.BOLD
    } else {
      Typeface.NORMAL
    }) or if (fontStyleName == "italic") Typeface.ITALIC else Typeface.NORMAL
    val family = fontFamilyName
    input.typeface = if (family.isNullOrBlank()) {
      Typeface.create(Typeface.DEFAULT, style)
    } else {
      Typeface.create(family, style)
    }
  }

  private fun applyLetterSpacing() {
    input.letterSpacing = letterSpacingPx?.div(fontSizeSp)?.takeIf { it.isFinite() } ?: 0f
  }

  override fun onDetachedFromWindow() {
    sessionId?.let { CardSessionRegistry.unregister(it, this) }
    clearSensitiveValue()
    super.onDetachedFromWindow()
  }

  override fun sensitiveDigits(): String = digits(input.text?.toString().orEmpty())

  override fun isSensitiveValid(): Boolean = intrinsicStatus() == FieldStatus.VALID

  override fun clearSensitiveValue() {
    changingText = true
    input.text?.clear()
    changingText = false
    touched = false
    revealError = false
    autoAdvanced = false
    previousIntrinsicStatus = FieldStatus.EMPTY
    emitSanitizedState(force = true)
  }

  override fun focusSensitiveField() {
    if (input.isEnabled) input.requestFocus()
  }

  override fun blurSensitiveField() {
    input.clearFocus()
  }

  override fun revealValidationError() {
    touched = true
    revealError = true
    emitSanitizedState(force = true)
  }

  private fun configureForField() {
    input.inputType = InputType.TYPE_CLASS_NUMBER or when (configuredField) {
      SensitiveField.CVC -> InputType.TYPE_NUMBER_VARIATION_PASSWORD
      else -> InputType.TYPE_NUMBER_VARIATION_NORMAL
    }
    input.importantForAutofill = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      IMPORTANT_FOR_AUTOFILL_AUTO
    } else if (configuredField == SensitiveField.NUMBER) {
      input.setAutofillHints("creditCardNumber")
      IMPORTANT_FOR_AUTOFILL_YES
    } else {
      input.setAutofillHints()
      IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
  }

  private fun normalizeTextPreservingCursor() {
    val currentText = input.text?.toString().orEmpty()
    val oldSelection = input.selectionStart.coerceAtLeast(0)
    val logicalCursor = currentText.take(oldSelection).count(Char::isDigit)
    val normalized = when (configuredField) {
      SensitiveField.NUMBER -> formatNumber(digits(currentText).take(19))
      SensitiveField.EXPIRY -> formatExpiry(digits(currentText).take(4))
      SensitiveField.CVC -> digits(currentText).take(4)
    }
    if (normalized == currentText) return
    changingText = true
    input.setText(normalized)
    input.setSelection(cursorOffsetForDigit(normalized, logicalCursor))
    changingText = false
  }

  private fun formatNumber(value: String): String {
    if (brand(value) == "amex") {
      return buildString {
        value.forEachIndexed { index, char ->
          if (index == 4 || index == 10) append(' ')
          append(char)
        }
      }
    }
    return value.chunked(4).joinToString(" ")
  }

  private fun formatExpiry(value: String) =
    if (value.length > 2) "${value.take(2)}/${value.drop(2)}" else value

  private fun cursorOffsetForDigit(value: String, logicalDigit: Int): Int {
    if (logicalDigit <= 0) return 0
    var seen = 0
    value.forEachIndexed { index, char ->
      if (char.isDigit()) seen += 1
      if (seen == logicalDigit) return index + 1
    }
    return value.length
  }

  private fun intrinsicStatus(): FieldStatus {
    val value = sensitiveDigits()
    if (value.isEmpty()) return FieldStatus.EMPTY
    return when (configuredField) {
      SensitiveField.NUMBER -> numberStatus(value)
      SensitiveField.EXPIRY -> expiryStatus(value)
      SensitiveField.CVC -> cvcStatus(value)
    }
  }

  private fun presentedStatus(): FieldStatus {
    val intrinsic = intrinsicStatus()
    return if (intrinsic == FieldStatus.INVALID && input.hasFocus() && !revealError) {
      FieldStatus.INCOMPLETE
    } else {
      intrinsic
    }
  }

  private fun numberStatus(value: String): FieldStatus {
    val stripeBrand = CardUtils.getPossibleCardBrand(value)
    val expectedLengths = when (normalizedBrand(stripeBrand, value)) {
      "amex" -> setOf(15)
      "visa" -> setOf(13, 16, 19)
      "jcb", "unionpay" -> setOf(16, 17, 18, 19)
      "maestro" -> (12..19).toSet()
      else -> setOf(16)
    }
    return when {
      value.length < expectedLengths.min() -> FieldStatus.INCOMPLETE
      value.length > expectedLengths.max() -> FieldStatus.INVALID
      value.length !in expectedLengths -> FieldStatus.INCOMPLETE
      (
        stripeBrand.isValidCardNumberLength(value) ||
          normalizedBrand(stripeBrand, value) == "jcb" ||
          normalizedBrand(stripeBrand, value) == "unionpay" ||
          normalizedBrand(stripeBrand, value) == "maestro"
        ) && CardUtils.isValidLuhnNumber(value) -> FieldStatus.VALID
      normalizedBrand(stripeBrand, value) == "unknown" && CardUtils.isValidLuhnNumber(value) -> FieldStatus.VALID
      else -> FieldStatus.INVALID
    }
  }

  private fun expiryStatus(value: String): FieldStatus {
    if (value.length < 2) return FieldStatus.INCOMPLETE
    val month = value.take(2).toIntOrNull() ?: return FieldStatus.INVALID
    if (month !in 1..12) return FieldStatus.INVALID
    if (value.length < 4) return FieldStatus.INCOMPLETE
    val year = value.drop(2).toIntOrNull() ?: return FieldStatus.INVALID
    val now = Calendar.getInstance()
    val currentYear = now.get(Calendar.YEAR) % 100
    val currentMonth = now.get(Calendar.MONTH) + 1
    return if (year > currentYear || year == currentYear && month >= currentMonth) {
      FieldStatus.VALID
    } else {
      FieldStatus.INVALID
    }
  }

  private fun cvcStatus(value: String): FieldStatus {
    val expected = if (CardSessionRegistry.brand(sessionId) == "amex") 4 else 3
    return when {
      value.length < expected -> FieldStatus.INCOMPLETE
      value.length == expected -> FieldStatus.VALID
      else -> FieldStatus.INVALID
    }
  }

  private fun brand(value: String) = normalizedBrand(CardUtils.getPossibleCardBrand(value), value)

  private fun normalizedBrand(stripeBrand: CardBrand, value: String): String = when (stripeBrand) {
    CardBrand.Visa -> "visa"
    CardBrand.MasterCard -> "mastercard"
    CardBrand.AmericanExpress -> "amex"
    CardBrand.Discover -> "discover"
    else -> when {
      isDiscoverPrefix(value) -> "discover"
      isJcbPrefix(value) -> "jcb"
      isUnionPayPrefix(value) -> "unionpay"
      isMaestroPrefix(value) -> "maestro"
      else -> "unknown"
    }
  }

  private fun isDiscoverPrefix(value: String): Boolean {
    val prefix3 = value.take(3).toIntOrNull()
    val prefix6 = value.take(6).toIntOrNull()
    return value.startsWith("6011") ||
      value.startsWith("65") ||
      prefix3 in 644..649 ||
      prefix6 in 622126..622925
  }

  private fun isJcbPrefix(value: String): Boolean {
    val prefix4 = value.take(4).toIntOrNull() ?: return false
    return prefix4 in 3528..3589
  }

  private fun isUnionPayPrefix(value: String): Boolean =
    value.startsWith("62")

  private fun isMaestroPrefix(value: String): Boolean {
    val prefix2 = value.take(2).toIntOrNull() ?: return false
    return value.startsWith("50") || prefix2 in 56..59 || prefix2 in 60..61 || prefix2 in 63..69
  }

  private fun emitSanitizedState(force: Boolean = false) {
    val payload = mapOf(
      "field" to configuredField.wireName,
      "status" to presentedStatus().wireName,
      "brand" to if (configuredField == SensitiveField.NUMBER) brand(sensitiveDigits()) else "unknown",
      "focused" to input.hasFocus(),
      "touched" to touched
    )
    if (force || payload != lastEvent) {
      lastEvent = payload
      updateAccessibilityDescription()
      onStateChange(payload)
    }
  }

  private fun updateAccessibilityDescription() {
    val parts = mutableListOf(accessibilityLabelText)
    if (sensitiveDigits().isNotEmpty()) parts += enteredAccessibilityValue
    if (presentedStatus() == FieldStatus.INVALID) parts += invalidAccessibilityValue
    parts += accessibilityHintText
    input.contentDescription = parts.filter(String::isNotBlank).joinToString(". ")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) input.tooltipText = accessibilityHintText
  }

  private fun digits(value: String) = value.filter(Char::isDigit)

  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

  private class BlockCopyCutActionMode : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
      menu?.removeItem(android.R.id.copy)
      menu?.removeItem(android.R.id.cut)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
      menu?.removeItem(android.R.id.copy)
      menu?.removeItem(android.R.id.cut)
      return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) =
      item?.itemId == android.R.id.copy || item?.itemId == android.R.id.cut

    override fun onDestroyActionMode(mode: ActionMode?) = Unit
  }

  private class NoExportEditText(context: Context) : EditText(context) {
    override fun onTextContextMenuItem(id: Int): Boolean {
      if (id == android.R.id.copy || id == android.R.id.cut) return false
      return super.onTextContextMenuItem(id)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
      if (event.isCtrlPressed && (keyCode == KeyEvent.KEYCODE_C || keyCode == KeyEvent.KEYCODE_X)) {
        return true
      }
      return super.onKeyShortcut(keyCode, event)
    }

    override fun onSaveInstanceState(): android.os.Parcelable? = null
  }
}
