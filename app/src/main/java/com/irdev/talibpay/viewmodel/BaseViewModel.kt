package com.irdev.talibpay.viewmodel

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irdev.talibpay.data.NotSignedInException
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.util.errorMessageRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** أحداث تُعرض مرة واحدة (رسائل، تنقل). */
sealed interface UiEvent {
    data class Message(@param:StringRes val res: Int, val arg: String? = null) : UiEvent
    data object NotSignedIn : UiEvent
    data object Finished : UiEvent
    data class SignedIn(val displayName: String?) : UiEvent
    data object SignedOut : UiEvent
    data class StudentArchived(val student: StudentWithPayments) : UiEvent
    data class StudentRestored(val student: StudentWithPayments) : UiEvent
}

abstract class BaseViewModel : ViewModel() {

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    protected fun send(event: UiEvent) {
        _events.trySend(event)
    }

    protected fun handleError(e: Throwable) {
        if (e is NotSignedInException) {
            send(UiEvent.NotSignedIn)
        } else {
            Log.w(javaClass.simpleName, "Operation failed", e)
            send(UiEvent.Message(errorMessageRes(e)))
        }
    }

    /** ينفذ عملية مع حالة التحميل ومعالجة الأخطاء، ويتجاهل النقر المتكرر أثناء التنفيذ. */
    protected fun runAction(block: suspend () -> Unit) {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _loading.value = false
            }
        }
    }
}
