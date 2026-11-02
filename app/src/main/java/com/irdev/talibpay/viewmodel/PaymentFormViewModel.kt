package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.PaymentInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.Money
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

    private val _currentFees = MutableStateFlow<Double?>(null)

    /** رسوم الطالب الحالية لتعبئة حقل الرسوم عند إضافة دفعة جديدة. */
    val currentFees: StateFlow<Double?> = _currentFees.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val data = repository.getStudent(studentId) ?: return@launch
                val id = paymentId
                if (id != null) {
                    _existing.value = data.payments.firstOrNull { it.id == id }
                } else if (data.student.total > 0) {
                    _currentFees.value = data.student.total
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun save(
        cashText: String,
        bankText: String,
        totalText: String,
        method: PaymentMethod?,
        paymentDate: String,
        note: String
    ) {
        if (studentId.isEmpty()) {
            send(UiEvent.Message(R.string.error_generic))
            return
        }
        if (method == null) {
            send(UiEvent.Message(R.string.please_choose_payment_method))
            return
        }
        runAction {
            // التحقق تراكمي: نحتاج ما دفعه الطالب في دفعاته الأخرى
            val payments = repository.getStudent(studentId)?.payments.orEmpty()
            val paidByOthers = Money.sum(
                payments.filter { it.id != paymentId }.map { Money.sum(listOf(it.cash, it.bank)) }
            )
            val amounts = when (
                val result = validatePayment(cashText, bankText, totalText, method, paidByOthers)
            ) {
                is PaymentValidation.Valid -> result
                PaymentValidation.InvalidAmount -> return@runAction send(UiEvent.Message(R.string.invalid_amount))
                PaymentValidation.MissingFees -> return@runAction send(UiEvent.Message(R.string.fees_required))
                PaymentValidation.MethodMismatch -> return@runAction send(UiEvent.Message(R.string.method_mismatch))
                is PaymentValidation.PaidExceedsTotal -> return@runAction send(
                    UiEvent.Message(R.string.paid_exceeds_fees, Money.format(result.maxAllowed))
                )
            }

            val input = PaymentInput(
                cash = amounts.cash,
                bank = amounts.bank,
                total = amounts.total,
                method = method,
                paymentDate = paymentDate,
                note = note
            )
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
