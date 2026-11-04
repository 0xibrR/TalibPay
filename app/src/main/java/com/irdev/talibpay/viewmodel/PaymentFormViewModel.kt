package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.util.formatAmount
import com.irdev.talibpay.R
import com.irdev.talibpay.data.PaymentInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.PaymentValidation
import com.irdev.talibpay.domain.validatePayment
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.ToastKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @param dues الـ charges التي يمكن الدفع لها (غير المسددة، ومعها charge الدفعة التي تُعدَّل)
 * @param selectedDueId الـ charge المختار مبدئيًا
 * @param existing الدفعة عند التعديل
 */
data class PaymentFormState(
    val dues: List<DueSummary>,
    val selectedDueId: String?,
    val existing: Payment?
) {
    /** المتاح للدفع في الـ charge: المتبقي عليه + قيمة الدفعة التي تُعدَّل إن كانت منه. */
    fun availableFor(dueId: String): Double {
        val due = dues.firstOrNull { it.charge.id == dueId } ?: return 0.0
        // الجزء المطبَّق فقط من الدفعة (الزيادة التي ذهبت للـ wallet لا تُحسب)
        val own = existing?.takeIf { it.chargeId == dueId }?.let { Money.subtract(it.paid, it.credited) } ?: 0.0
        return Money.sum(listOf(due.remaining, own))
    }
}

/** تسجيل دفعة لـ charge (بدون id دفعة) أو تعديل دفعة موجودة. */
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
                // الافتراضي: charge الدفعة التي تُعدَّل، أو المطلوب، أو أقدم charge غير مسدد
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
            send(UiEvent.Message(R.string.error_generic, kind = ToastKind.ERROR))
            return
        }
        if (dueId == null) {
            send(UiEvent.Message(R.string.choose_due))
            return
        }
        runAction {
            // نقرأ أحدث البيانات قبل الـ validation، فقد تكون دفعة أخرى سُجّلت من جهاز آخر
            val due = repository.getStudent(studentId)?.ledger?.due(dueId)
                ?: return@runAction send(UiEvent.Message(R.string.choose_due))
            val editing = due.payments.firstOrNull { it.id == paymentId }
            // دفعة أضافت credit للـ wallet لا تُعدَّل حتى لا يختل رصيد الـ wallet
            if (editing != null && editing.credited > 0) {
                return@runAction send(UiEvent.Message(R.string.payment_locked_by_wallet))
            }
            // المسدَّد من الـ charge بغير هذه الدفعة: خصم الـ wallet + الجزء المطبَّق من الدفعات الأخرى
            val paidByOthers = Money.sum(
                listOf(due.walletUsed) +
                    due.payments.filter { it.id != paymentId }.map { Money.subtract(it.paid, it.credited) }
            )

            val amounts = when (
                // دفعة جديدة: الزيادة عن المتبقي تذهب للـ wallet. التعديل: لا يتجاوز المتبقي
                val result = validatePayment(
                    cashText, bankText, due.charge.amount, paidByOthers, allowCredit = paymentId == null
                )
            ) {
                is PaymentValidation.Valid -> result
                PaymentValidation.InvalidAmount -> return@runAction send(UiEvent.Message(R.string.invalid_amount))
                PaymentValidation.EmptyPayment -> return@runAction send(UiEvent.Message(R.string.payment_amount_required))
                is PaymentValidation.ExceedsDue -> return@runAction send(
                    UiEvent.Message(R.string.payment_exceeds_due, formatAmount(result.maxAllowed))
                )
            }

            val input = PaymentInput(dueId, amounts.cash, amounts.bank, paymentDate, note)
            val id = paymentId
            if (id == null) {
                // صفحة الطالب تعرض رسالة الحفظ مع زر إرسال الـ receipt
                send(UiEvent.PaymentSaved(repository.addPayment(studentId, input)))
            } else {
                repository.updatePayment(studentId, id, input)
                send(UiEvent.Message(R.string.updated_successfully, kind = ToastKind.SUCCESS))
                send(UiEvent.Finished)
            }
        }
    }
}
