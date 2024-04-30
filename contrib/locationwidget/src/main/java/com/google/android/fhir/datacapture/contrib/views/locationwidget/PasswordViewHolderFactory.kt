/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.datacapture.contrib.views.locationwidget

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.fhir.datacapture.R
import com.google.android.fhir.datacapture.extensions.getRequiredOrOptionalText
import com.google.android.fhir.datacapture.extensions.itemControlCode
import com.google.android.fhir.datacapture.extensions.tryUnwrapContext
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.NotValidated
import com.google.android.fhir.datacapture.validation.Valid
import com.google.android.fhir.datacapture.validation.ValidationResult
import com.google.android.fhir.datacapture.views.HeaderView
import com.google.android.fhir.datacapture.views.QuestionnaireViewItem
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderDelegate
import com.google.android.fhir.datacapture.views.factories.QuestionnaireItemViewHolderFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.StringType

object PasswordViewHolderFactory :
  QuestionnaireItemViewHolderFactory(
    com.google.android.fhir.datacapture.contrib.views.locationwidget.R.layout.password_view,
  ) {
  override fun getQuestionnaireItemViewHolderDelegate() =
    object : QuestionnaireItemViewHolderDelegate {
      override lateinit var questionnaireViewItem: QuestionnaireViewItem

      private var password: String = ""

      private lateinit var header: HeaderView
      protected lateinit var passwordInputLayout: TextInputLayout
      protected lateinit var confirmPasswordInputLayout: TextInputLayout
      private lateinit var passwordEditText: TextInputEditText
      private lateinit var confirmPasswordEditText: TextInputEditText
      private var passwordTextWatcher: TextWatcher? = null
      private var confirmPasswordTextWatcher: TextWatcher? = null
      private lateinit var appContext: AppCompatActivity

      override fun init(itemView: View) {
        appContext = itemView.context.tryUnwrapContext()!!
        header = itemView.findViewById(R.id.header)
        passwordInputLayout =
          itemView.findViewById(
            com.google.android.fhir.datacapture.contrib.views.locationwidget.R.id
              .password_input_layout,
          )
        passwordEditText =
          itemView
            .findViewById<TextInputEditText?>(
              com.google.android.fhir.datacapture.contrib.views.locationwidget.R.id
                .password_edit_text,
            )
            .apply {
              setRawInputType(InputType.TYPE_CLASS_TEXT)

              setOnEditorActionListener { view, actionId, _ ->
                if (actionId != EditorInfo.IME_ACTION_NEXT) {
                  false
                }
                view.focusSearch(View.FOCUS_DOWN)?.requestFocus(View.FOCUS_DOWN) ?: false
              }
              setOnFocusChangeListener { view, focused ->
                if (!focused) {
                  (view.context.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                      as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                }
              }
            }

        confirmPasswordInputLayout =
          itemView.findViewById(
            com.google.android.fhir.datacapture.contrib.views.locationwidget.R.id
              .confirm_password_input_layout,
          )
        confirmPasswordEditText =
          itemView
            .findViewById<TextInputEditText?>(
              com.google.android.fhir.datacapture.contrib.views.locationwidget.R.id
                .confirm_password_edit_text,
            )
            .apply {
              setRawInputType(InputType.TYPE_CLASS_TEXT)

              setOnEditorActionListener { view, actionId, _ ->
                if (actionId != EditorInfo.IME_ACTION_NEXT) {
                  false
                }
                view.focusSearch(View.FOCUS_DOWN)?.requestFocus(View.FOCUS_DOWN) ?: false
              }
              setOnFocusChangeListener { view, focused ->
                if (!focused) {
                  (view.context.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                      as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                }
              }
            }
      }

      override fun bind(questionnaireViewItem: QuestionnaireViewItem) {
        header.bind(questionnaireViewItem)
        with(passwordInputLayout) {
          hint = "Enter Password"
          helperText = getRequiredOrOptionalText(questionnaireViewItem, context)
        }
        passwordEditText.removeTextChangedListener(passwordTextWatcher)

        with(confirmPasswordInputLayout) {
          hint = "Confirm Password"
          helperText = getRequiredOrOptionalText(questionnaireViewItem, context)
        }
        displayValidationResult(questionnaireViewItem.validationResult)

        confirmPasswordEditText.removeTextChangedListener(confirmPasswordTextWatcher)
        updateUI()

        passwordTextWatcher =
          passwordEditText.doAfterTextChanged { editable: Editable? ->
            appContext.lifecycleScope.launch { password = editable.toString() }
          }
        confirmPasswordTextWatcher =
          confirmPasswordEditText.doAfterTextChanged { editable: Editable? ->
            appContext.lifecycleScope.launch { handleInput(editable!!, questionnaireViewItem) }
          }
      }

      override fun setReadOnly(isReadOnly: Boolean) {
        passwordInputLayout.isEnabled = !isReadOnly
        passwordEditText.isEnabled = !isReadOnly
        confirmPasswordInputLayout.isEnabled = !isReadOnly
        confirmPasswordEditText.isEnabled = !isReadOnly
      }

      private fun displayValidationResult(validationResult: ValidationResult) {
        val confirmPassword = questionnaireViewItem.answers.singleOrNull()?.value?.toString() ?: ""
        println("*** $password $confirmPassword")
        if (password != confirmPassword) {
          confirmPasswordInputLayout.error = "Password do not match.."
        } else {
          confirmPasswordInputLayout.error = null
        }
        passwordInputLayout.error =
          when (validationResult) {
            is NotValidated,
            Valid, -> null
            is Invalid -> validationResult.getSingleStringValidationMessage()
          }
      }

      private suspend fun handleInput(
        editable: Editable,
        questionnaireViewItem: QuestionnaireViewItem,
      ) {
        val input = getValue(editable.toString())
        if (input != null) {
          questionnaireViewItem.setAnswer(input)
        } else {
          questionnaireViewItem.clearAnswer()
        }
      }

      private fun updateUI() {
        val text = questionnaireViewItem.answers.singleOrNull()?.value?.toString() ?: ""
        println("*** $text --- ${confirmPasswordEditText.text}")
        if (isTextUpdatesRequired(text, confirmPasswordEditText.text.toString())) {
          confirmPasswordEditText.setText(text)
        }
      }

      private fun isTextUpdatesRequired(answerText: String, inputText: String): Boolean {
        if (answerText.isEmpty() && inputText.isEmpty()) {
          return false
        }
        if (answerText.isEmpty() || inputText.isEmpty()) {
          return true
        }
        // Avoid shifting focus by updating text field if the values are the same
        return answerText != inputText
      }
    }

  private fun getValue(
    text: String,
  ): QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent? {
    return text.let {
      if (it.isEmpty()) {
        null
      } else {
        QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent().setValue(StringType(it))
      }
    }
  }

  fun matcher(questionnaireItem: Questionnaire.QuestionnaireItemComponent): Boolean {
    return questionnaireItem.itemControlCode == PASSWORD_WIDGET_UI_CONTROL_CODE
  }

  private const val PASSWORD_WIDGET_UI_CONTROL_CODE = "password-widget"
}
