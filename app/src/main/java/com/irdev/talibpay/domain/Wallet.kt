package com.irdev.talibpay.domain

import com.irdev.talibpay.model.WalletEntry

enum class WalletEntryType { DEPOSIT, USE, REFUND }

val WalletEntry.entryType: WalletEntryType?
    get() = WalletEntryType.entries.firstOrNull { it.name == type }

/** توزيع دفعة: الجزء الذي يسدد الـ charge، والزيادة التي تذهب للـ wallet. */
data class PaymentAllocation(val applied: Double, val credited: Double)

object Wallet {

    /** ids ثابتة تمنع تكرار الـ deposit لنفس الدفعة أو الـ use لنفس الـ charge. */
    fun depositId(paymentId: String) = "dep-$paymentId"
    fun useId(chargeId: String) = "use-$chargeId"

    /** الرصيد = deposits − (uses + refunds)، ولا يقل عن صفر. */
    fun balance(entries: Collection<WalletEntry>): Double {
        val deposits = Money.sum(entries.filter { it.entryType == WalletEntryType.DEPOSIT }.map { it.amount })
        val spent = Money.sum(
            entries.filter { it.entryType == WalletEntryType.USE || it.entryType == WalletEntryType.REFUND }
                .map { it.amount }
        )
        return Money.subtract(deposits, spent).coerceAtLeast(0.0)
    }

    /** ما يزيد عن المتبقي على الـ charge يذهب للـ wallet. */
    fun allocate(amount: Double, dueRemaining: Double): PaymentAllocation {
        val applied = minOf(amount, dueRemaining.coerceAtLeast(0.0))
        return PaymentAllocation(applied = Money.round(applied), credited = Money.subtract(amount, applied))
    }

    /** المخصوم من الـ wallet لـ charge جديد: الأقل بين الرصيد وقيمة الـ charge. */
    fun useFor(balance: Double, chargeAmount: Double): Double =
        Money.round(minOf(balance, chargeAmount).coerceAtLeast(0.0))
}

sealed interface RefundValidation {
    data class Valid(val amount: Double) : RefundValidation
    data object InvalidAmount : RefundValidation
    data class ExceedsBalance(val balance: Double) : RefundValidation
}

fun validateRefund(amountText: String?, balance: Double): RefundValidation {
    val amount = Money.parse(amountText) ?: return RefundValidation.InvalidAmount
    if (amount <= 0) return RefundValidation.InvalidAmount
    if (amount > balance) return RefundValidation.ExceedsBalance(balance)
    return RefundValidation.Valid(amount)
}
