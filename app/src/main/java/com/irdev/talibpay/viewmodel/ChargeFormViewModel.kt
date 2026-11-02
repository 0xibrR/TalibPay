package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.ChargeInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.domain.ChargeValidation
import com.irdev.talibpay.domain.DueSummary
import com.irdev.talibpay.domain.Money
import com.irdev.talibpay.domain.validateCharge
import com.irdev.talibpay.util.Extras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** بيانات تعبئة النموذج: المستحق عند التعديل، أو اقتراح قيمة عند الإضافة. */
data class ChargeFormPrefill(val due: DueSummary?, val suggestedAmount: Double?)

/** إضافة مستحق (بدون معرّف) أو تعديل مستحق موجود. */
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
                    // غالبًا الرسوم شهرية بنفس القيمة: نقترح قيمة آخر مستحق
                    ChargeFormPrefill(null, data.dues.lastOrNull()?.charge?.amount)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    /**
     * @param keepGeneratedTitle الاسم المعروض مولَّد (رسوم شهرية أو قديمة) ولم يغيّره المستخدم:
     * نبقيه فارغًا في البيانات ليبقى مولَّدًا ويُترجم مع لغة التطبيق.
     */
    fun save(title: String, keepGeneratedTitle: Boolean, amountText: String, dueDate: String, note: String) {
        if (studentId.isEmpty()) {
            send(UiEvent.Message(R.string.error_generic))
            return
        }
        runAction {
            val id = chargeId
            // عند التعديل: لا تقل القيمة عمّا دُفع من المستحق
            val alreadyPaid = if (id == null) 0.0
            else repository.getStudent(studentId)?.ledger?.due(id)?.paid ?: 0.0

            val valid = when (val result = validateCharge(title, amountText, alreadyPaid)) {
                is ChargeValidation.Valid -> result
                ChargeValidation.MissingTitle -> return@runAction send(UiEvent.Message(R.string.due_title_required))
                ChargeValidation.InvalidAmount -> return@runAction send(UiEvent.Message(R.string.due_amount_required))
                is ChargeValidation.BelowPaid -> return@runAction send(
                    UiEvent.Message(R.string.due_below_paid, Money.format(result.alreadyPaid))
                )
            }
            val input = ChargeInput(if (keepGeneratedTitle) "" else valid.title, valid.amount, dueDate, note)
            if (id == null) {
                repository.addCharge(studentId, input)
                send(UiEvent.Message(R.string.due_added))
            } else {
                repository.updateCharge(studentId, id, input)
                send(UiEvent.Message(R.string.updated_successfully))
            }
            send(UiEvent.Finished)
        }
    }
}
