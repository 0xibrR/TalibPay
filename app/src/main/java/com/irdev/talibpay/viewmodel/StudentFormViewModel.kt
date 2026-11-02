package com.irdev.talibpay.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.R
import com.irdev.talibpay.data.StudentInput
import com.irdev.talibpay.data.StudentRepository
import com.irdev.talibpay.model.Student
import com.irdev.talibpay.util.Extras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** إضافة طالب (بدون معرّف) أو تعديل طالب موجود. */
class StudentFormViewModel(savedStateHandle: SavedStateHandle) : BaseViewModel() {

    private val repository = StudentRepository()
    private val studentId: String? = savedStateHandle[Extras.STUDENT_ID]

    private val _existing = MutableStateFlow<Student?>(null)

    /** بيانات الطالب الحالية لتعبئة النموذج عند التعديل. */
    val existing: StateFlow<Student?> = _existing.asStateFlow()

    init {
        val id = studentId
        if (id != null) {
            viewModelScope.launch {
                try {
                    _existing.value = repository.getStudent(id)?.student
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        }
    }

    fun save(input: StudentInput) = runAction {
        val id = studentId
        if (id == null) {
            repository.addStudent(input)
            send(UiEvent.Message(R.string.added_successfully))
        } else {
            repository.updateStudent(id, input)
            send(UiEvent.Message(R.string.updated_successfully))
        }
        send(UiEvent.Finished)
    }
}
