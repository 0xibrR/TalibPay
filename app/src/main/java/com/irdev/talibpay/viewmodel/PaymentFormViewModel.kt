package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.PaymentInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentValidation
import com.irdev.talibpay.domain.validatePayment
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.Extras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @param dues المستحقات التي يمكن الدفع لها (غير المسددة، ومعها مستحق الدفعة المعدَّلة)
 * @param selectedDueId المستحق المختار مبدئيًا
 * @param existing الدفعة عند التعديل
 */
data class PaymentFormState(
    val dues: List<DueSummary>,
    val selectedDueId: String?,
    val existing: Payment?
) {
    /** المتاح للدفع في المستحق: المتبقي عليه + قيمة الدفعة المعدَّلة إن كانت منه. */
    fun availableFor(dueId: String): Double {
        val due = dues.firstOrNull { it.charge.id == dueId } ?: return 0.0
        val own = existing?.takeIf { it.chargeId == dueId }?.paid ?: 0.0
        return Money.sum(listOf(due.remaining, own))
    }
}

/** تسجيل دفعة لمستحق (بدون معرّف دفعة) أو تعديل دفعة موجودة. */
class PaymentFormViewModel(savedStateHandle: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    private val studentId: String = savedStateHandle[Extras.STUDENT_ID] ?: ""
    private val paymentId: String? = savedStateHandle[Extras.PAYMENT_ID]
    private val requestedDueId: String? = savedStateHandle[Extras.CHARGE_ID]

    val isEditing: Boolean get() = paymentId != null

    private val _state = MutableStateFlow<PaymentFormState?>(null)
    val state: StateFlow<PaymentFormState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val ledger = repository.getStudent(studentId)?.ledger ?: return@launch
                val existing = paymentId?.let { id -> ledger.payments.firstOrNull { it.id == id } }
                val dues = ledger.dues.filter {
                    it.remaining > 0 || it.charge.id == existing?.chargeId
                }
                // الافتراضي: مستحق الدفعة المعدَّلة، أو المطلوب، أو أقدم مستحق غير مسدد
                val selected = existing?.chargeId
                    ?: requestedDueId?.takeIf { id -> dues.any { it.charge.id == id } }
                    ?: dues.firstOrNull()?.charge?.id
                _state.value = PaymentFormState(dues, selected, existing)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun save(dueId: String?, cashText: String, bankText: String, paymentDate: String, note: String) {
        if (studentId.isEmpty()) {
            send(UiEvent.Message(R.string.error_generic))
            return
        }
        if (dueId == null) {
            send(UiEvent.Message(R.string.choose_due))
            return
        }
        runAction {
            // نقرأ أحدث البيانات قبل التحقق، فقد تكون دفعة أخرى سُجّلت من جهاز آخر
            val due = repository.getStudent(studentId)?.ledger?.due(dueId)
                ?: return@runAction send(UiEvent.Message(R.string.choose_due))
            val paidByOthers = Money.sum(due.payments.filter { it.id != paymentId }.map { it.paid })

            val amounts = when (
                val result = validatePayment(cashText, bankText, due.charge.amount, paidByOthers)
            ) {
                is PaymentValidation.Valid -> result
                PaymentValidation.InvalidAmount -> return@runAction send(UiEvent.Message(R.string.invalid_amount))
                PaymentValidation.EmptyPayment -> return@runAction send(UiEvent.Message(R.string.payment_amount_required))
                is PaymentValidation.ExceedsDue -> return@runAction send(
                    UiEvent.Message(R.string.payment_exceeds_due, Money.format(result.maxAllowed))
                )
            }

            val input = PaymentInput(dueId, amounts.cash, amounts.bank, paymentDate, note)
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
