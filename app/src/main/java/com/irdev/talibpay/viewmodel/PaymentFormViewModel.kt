package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.PaymentInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.PaymentMethod
import com.irdev.talibpay.domain.PaymentValidation
import com.irdev.talibpay.domain.validatePayment
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.Extras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** إضافة دفعة (بدون معرّف دفعة) أو تعديل دفعة موجودة. */
class PaymentFormViewModel(savedStateHandle: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    private val studentId: String = savedStateHandle[Extras.STUDENT_ID] ?: ""
    private val paymentId: String? = savedStateHandle[Extras.PAYMENT_ID]

    private val _existing = MutableStateFlow<Payment?>(null)

    /** الدفعة الحالية لتعبئة النموذج عند التعديل. */
    val existing: StateFlow<Payment?> = _existing.asStateFlow()

    init {
        val id = paymentId
        if (id != null) {
            viewModelScope.launch {
                try {
                    _existing.value = repository.getStudent(studentId)
                        ?.payments?.firstOrNull { it.id == id }
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        }
    }

    fun save(
        cashText: String,
        bankText: String,
        totalText: String,
        methodPosition: Int,
        paymentDate: String,
        note: String
    ) {
        if (studentId.isEmpty()) {
            send(UiEvent.Message(R.string.error_generic))
            return
        }
        val method = PaymentMethod.fromSpinnerPosition(methodPosition)
        if (method == null) {
            send(UiEvent.Message(R.string.please_choose_payment_method))
            return
        }
        val amounts = when (val result = validatePayment(cashText, bankText, totalText)) {
            is PaymentValidation.Valid -> result
            PaymentValidation.InvalidAmount -> {
                send(UiEvent.Message(R.string.invalid_amount))
                return
            }

            PaymentValidation.PaidExceedsTotal -> {
                send(UiEvent.Message(R.string.The_amount_paid_may_not_be_greater_than_the_full_fee))
                return
            }
        }
        val input = PaymentInput(
            cash = amounts.cash,
            bank = amounts.bank,
            total = amounts.total,
            method = method,
            paymentDate = paymentDate,
            note = note
        )
        runAction {
            val id = paymentId
            if (id == null) {
                repository.addPayment(studentId, input)
                send(UiEvent.Message(R.string.payment_added_successfully))
            } else {
                repository.updatePayment(studentId, id, input)
                send(UiEvent.Message(R.string.updated_successfully))
            }
            send(UiEvent.Finished)
        }
    }
}
