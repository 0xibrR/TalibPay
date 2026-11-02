package com.irdev.talibpay.domain

import com.irdev.talibpay.model.Charge
import com.irdev.talibpay.model.Payment
import com.irdev.talibpay.model.WalletEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTest {

    private fun entry(id: String, type: WalletEntryType, amount: Double, chargeId: String = "", createdAt: Long = 0) =
        WalletEntry(id = id, type = type.name, amount = amount, chargeId = chargeId, createdAt = createdAt)

    @Test
    fun `overpayment goes to the wallet, the rest settles the due`() {
        assertEquals(PaymentAllocation(applied = 100.0, credited = 50.0), Wallet.allocate(150.0, dueRemaining = 100.0))
        assertEquals(PaymentAllocation(applied = 60.0, credited = 0.0), Wallet.allocate(60.0, dueRemaining = 100.0))
        // المستحق مسدد بالكامل: كل المبلغ للمحفظة
        assertEquals(PaymentAllocation(applied = 0.0, credited = 40.0), Wallet.allocate(40.0, dueRemaining = 0.0))
    }

    @Test
    fun `balance is deposits minus uses and refunds`() {
        val entries = listOf(
            entry("d1", WalletEntryType.DEPOSIT, 50.0),
            entry("d2", WalletEntryType.DEPOSIT, 30.0),
            entry("u1", WalletEntryType.USE, 40.0, chargeId = "c2"),
            entry("r1", WalletEntryType.REFUND, 15.0)
        )
        assertEquals(25.0, Wallet.balance(entries), 0.0)
        assertEquals(0.0, Wallet.balance(emptyList()), 0.0)
    }

    @Test
    fun `new due uses the wallet fully or partially`() {
        assertEquals(100.0, Wallet.useFor(balance = 150.0, chargeAmount = 100.0), 0.0) // كليًا
        assertEquals(30.0, Wallet.useFor(balance = 30.0, chargeAmount = 100.0), 0.0) // جزئيًا
        assertEquals(0.0, Wallet.useFor(balance = 0.0, chargeAmount = 100.0), 0.0)
    }

    @Test
    fun `fixed ids prevent depositing or deducting twice`() {
        assertEquals(Wallet.depositId("p1"), Wallet.depositId("p1"))
        assertEquals("use-auto-2026-11", Wallet.useId("auto-2026-11"))
    }

    @Test
    fun `ledger counts only the applied part of an overpayment toward the due`() {
        val charge = Charge(id = "c1", amount = 100.0, createdAt = 1)
        // دفع 150 لمستحق 100: 50 ذهبت للمحفظة
        val payment = Payment(id = "p1", chargeId = "c1", cash = 150.0, credited = 50.0)
        val ledger = StudentLedger.build(
            listOf(charge), listOf(payment),
            listOf(entry("dep-p1", WalletEntryType.DEPOSIT, 50.0))
        )
        val due = ledger.due("c1")!!
        assertEquals(DueStatus.PAID, due.status)
        assertEquals(100.0, due.paid, 0.0)
        assertEquals(0.0, due.remaining, 0.0)
        // المدفوع الفعلي يبقى 150 (المال المستلم)، والمحفظة 50
        assertEquals(150.0, ledger.totals.paid, 0.0)
        assertEquals(150.0, ledger.totals.paidCash, 0.0)
        assertEquals(50.0, ledger.walletBalance, 0.0)
    }

    @Test
    fun `wallet use settles a new due without counting as a cash payment`() {
        val c1 = Charge(id = "c1", amount = 100.0, createdAt = 1)
        val c2 = Charge(id = "c2", amount = 100.0, createdAt = 2)
        val payment = Payment(id = "p1", chargeId = "c1", cash = 150.0, credited = 50.0)
        val wallet = listOf(
            entry("dep-p1", WalletEntryType.DEPOSIT, 50.0, createdAt = 1),
            entry("use-c2", WalletEntryType.USE, 50.0, chargeId = "c2", createdAt = 2)
        )
        val ledger = StudentLedger.build(listOf(c1, c2), listOf(payment), wallet)
        val due2 = ledger.due("c2")!!

        assertEquals(50.0, due2.walletUsed, 0.0)
        assertEquals(50.0, due2.remaining, 0.0)
        assertEquals(DueStatus.PARTIAL, due2.status)
        assertTrue(due2.payments.isEmpty()) // ليست دفعة
        assertEquals(150.0, ledger.totals.paidCash, 0.0) // لا نقد جديد
        assertEquals(0.0, ledger.walletBalance, 0.0)
        assertEquals(50.0, ledger.totals.remaining, 0.0)
    }

    @Test
    fun `payment after a wallet use shows the remaining after both`() {
        val c = Charge(id = "c2", amount = 100.0, createdAt = 2)
        val wallet = listOf(entry("use-c2", WalletEntryType.USE, 30.0, chargeId = "c2"))
        val payment = Payment(id = "p2", chargeId = "c2", bank = 50.0)
        val due = StudentLedger.build(listOf(c), listOf(payment), wallet).due("c2")!!
        assertEquals(20.0, due.payments.single().remaining, 0.0)
        assertEquals(80.0, due.paid, 0.0)
    }

    @Test
    fun `due with a wallet use counts as having activity (cannot be deleted)`() {
        val c = Charge(id = "c2", amount = 100.0)
        val withUse = StudentLedger.build(listOf(c), emptyList(), listOf(entry("use-c2", WalletEntryType.USE, 10.0, "c2")))
        val empty = StudentLedger.build(listOf(c), emptyList())
        assertTrue(withUse.due("c2")!!.hasActivity)
        assertFalse(empty.due("c2")!!.hasActivity)
    }

    @Test
    fun `ledgers without a wallet are unchanged`() {
        val charge = Charge(id = "c1", amount = 100.0)
        val payments = listOf(Payment(id = "p1", chargeId = "c1", cash = 60.0))
        val ledger = StudentLedger.build(listOf(charge), payments)
        assertEquals(40.0, ledger.due("c1")!!.remaining, 0.0)
        assertEquals(60.0, ledger.totals.paid, 0.0)
        assertEquals(0.0, ledger.walletBalance, 0.0)
    }

    @Test
    fun `refund needs a positive amount within the balance`() {
        assertEquals(RefundValidation.Valid(20.0), validateRefund("20", balance = 50.0))
        assertEquals(RefundValidation.ExceedsBalance(50.0), validateRefund("60", balance = 50.0))
        assertEquals(RefundValidation.InvalidAmount, validateRefund("0", balance = 50.0))
        assertEquals(RefundValidation.InvalidAmount, validateRefund("abc", balance = 50.0))
    }

    @Test
    fun `new payments may exceed the due, edits may not`() {
        assertEquals(PaymentValidation.Valid(150.0, 0.0), validatePayment("150", "0", 100.0, 0.0, allowCredit = true))
        assertEquals(PaymentValidation.ExceedsDue(100.0), validatePayment("150", "0", 100.0, 0.0, allowCredit = false))
    }
}
