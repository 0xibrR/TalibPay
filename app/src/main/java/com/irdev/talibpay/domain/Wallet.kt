package com.irdev.talibpay.domain

import com.irdev.talibpay.model.WalletEntry

enum class WalletEntryType { DEPOSIT, USE, REFUND }

val WalletEntry.entryType: WalletEntryType?
    get() = WalletEntryType.entries.firstOrNull { it.name == type }

/** توزيع دفعة: الجزء الذي يسدد المستحق، والزيادة التي تذهب للمحفظة. */
data class PaymentAllocation(val applied: Double, val credited: Double)

object Wallet {

    /** معرّفات ثابتة تمنع تكرار الإيداع لنفس الدفعة أو الخصم لنفس المستحق. */
    fun depositId(paymentId: String) = "dep-$paymentId"
    fun useId(chargeId: String) = "use-$chargeId"

    /** الرصيد = الإيداعات − (الاستخدامات + الاستردادات)، ولا يقل عن صفر. */
    fun balance(entries: Collection<WalletEntry>): Double {
        val deposits = Money.sum(entries.filter { it.entryType == WalletEntryType.DEPOSIT }.map { it.amount })
        val spent = Money.sum(
            entries.filter { it.entryType == WalletEntryType.USE || it.entryType == WalletEntryType.REFUND }
                .map { it.amount }
        )
        return Money.subtract(deposits, spent).coerceAtLeast(0.0)
    }

    /** ما يزيد عن المتبقي على المستحق يذهب للمحفظة. */
    fun allocate(amount: Double, dueRemaining: Double): PaymentAllocation {
        val applied = minOf(amount, dueRemaining.coerceAtLeast(0.0))
        return PaymentAllocation(applied = Money.round(applied), credited = Money.subtract(amount, applied))
    }

    /** ما يُخصم من المحفظة لمستحق جديد: الأقل بين الرصيد وقيمة المستحق. */
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
