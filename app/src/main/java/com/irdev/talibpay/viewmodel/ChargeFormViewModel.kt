package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.util.formatAmount
import com.irdev.talibpay.R
import com.irdev.talibpay.data.ChargeInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.hasMonthlyBilling
import com.irdev.talibpay.domain.ChargeValidation
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.validateCharge
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.ToastKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * بيانات تعبئة الـ form: الـ charge عند التعديل، أو قيمة مقترحة عند الإضافة.
 * @param suggestMonthTitle اقتراح "رسوم الشهر" كاسم للـ charge الجديد: فقط لطالب بدون monthly fee تلقائية،
 *   وإلا سيتكرر اسم الـ monthly charge التلقائي نفسه.
 */
data class ChargeFormPrefill(val due: DueSummary?, val suggestedAmount: Double?, val suggestMonthTitle: Boolean = false)

/** إضافة charge (بدون id) أو تعديل charge موجود. */
class ChargeFormViewModel(savedStateHandle: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    private val studentId: String = savedStateHandle[Extras.STUDENT_ID] ?: ""
    private val chargeId: String? = savedStateHandle[Extras.CHARGE_ID]

    val isEditing: Boolean get() = chargeId != null

    private val _prefill = MutableStateFlow<ChargeFormPrefill?>(null)
    val prefill: StateFlow<ChargeFormPrefill?> = _prefill.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val data = repository.getStudent(studentId) ?: return@launch
                val id = chargeId
                _prefill.value = if (id != null) {
                    ChargeFormPrefill(data.ledger.due(id), null)
                } else {
                    // طالب بدون رسوم تلقائية: غالبًا يضيف رسوم كل شهر يدويًا بنفس القيمة، فنقترح الاسم والقيمة.
                    // من له رسوم تلقائية: الـ charge اليدوي شيء آخر (كتب، تسجيل...)، فلا اقتراح.
                    val manualBilling = !data.student.hasMonthlyBilling
                    ChargeFormPrefill(
                        null,
                        if (manualBilling) data.dues.lastOrNull()?.charge?.amount else null,
                        suggestMonthTitle = manualBilling
                    )
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * @param keepGeneratedTitle الاسم المعروض generated (رسوم شهرية أو legacy) ولم يغيّره المستخدم:
     * نتركه فارغًا في البيانات ليبقى generated ويُترجم حسب لغة التطبيق.
     */
    fun save(title: String, keepGeneratedTitle: Boolean, amountText: String, dueDate: String, note: String) {
        if (studentId.isEmpty()) {
            send(UiEvent.Message(R.string.error_generic, kind = ToastKind.ERROR))
            return
        }
        runAction {
            val id = chargeId
            // عند التعديل: القيمة لا تقل عن المدفوع من الـ charge
            val alreadyPaid = if (id == null) 0.0
            else repository.getStudent(studentId)?.ledger?.due(id)?.paid ?: 0.0

            val valid = when (val result = validateCharge(title, amountText, alreadyPaid)) {
                is ChargeValidation.Valid -> result
                ChargeValidation.MissingTitle -> return@runAction send(UiEvent.Message(R.string.due_title_required))
                ChargeValidation.InvalidAmount -> return@runAction send(UiEvent.Message(R.string.due_amount_required))
                is ChargeValidation.BelowPaid -> return@runAction send(
                    UiEvent.Message(R.string.due_below_paid, formatAmount(result.alreadyPaid))
                )
            }
            val input = ChargeInput(if (keepGeneratedTitle) "" else valid.title, valid.amount, dueDate, note)
            if (id == null) {
                repository.addCharge(studentId, input)
                send(UiEvent.Message(R.string.due_added, kind = ToastKind.SUCCESS))
            } else {
                repository.updateCharge(studentId, id, input)
                send(UiEvent.Message(R.string.updated_successfully, kind = ToastKind.SUCCESS))
            }
            send(UiEvent.Finished)
        }
    }
}
