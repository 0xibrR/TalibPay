package com.irdev.talibpay.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.irdev.talibpay.R
import com.irdev.talibpay.adapter.StudentAdapter
import com.irdev.talibpay.data.StudentWithPayments
import com.irdev.talibpay.databinding.ActivityArchiveBinding
import com.irdev.talibpay.util.Extras
import com.irdev.talibpay.util.SwipeAction
import com.irdev.talibpay.util.applySystemBarInsets
import com.irdev.talibpay.util.attachSwipeActions
import com.irdev.talibpay.util.collectWhileStarted
import com.irdev.talibpay.util.setupToolbar
import com.irdev.talibpay.viewmodel.ArchiveViewModel
import com.irdev.talibpay.viewmodel.UiEvent

/** الطلاب المؤرشفون: فتح أي طالب يعرض سجله كاملًا، والسحب يعيده للقائمة. */
class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArchiveBinding
    private val viewModel: ArchiveViewModel by viewModels()
    private val adapter = StudentAdapter { item ->
        startActivity(
            Intent(this, DetailActivity::class.java).putExtra(Extras.STUDENT_ID, item.student.id)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.main, applyTop = false)
        setupToolbar(binding.toolbar)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        // السحب لليسار يعيد الطالب للقائمة (نفس اتجاه الأرشفة في الشاشة الرئيسية)
        binding.recyclerView.attachSwipeActions(
            right = null,
            left = SwipeAction(R.drawable.ic_unarchive, R.color.swipe_restore) { position ->
                viewModel.restore(adapter.currentList[position])
            }
        )

        collectWhileStarted(viewModel.students) { list ->
            if (list == null) return@collectWhileStarted
            binding.tvEmpty.isVisible = list.isEmpty()
            adapter.submitList(list)
        }
        collectWhileStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.StudentRestored -> showRestoredSnackbar(event.student)
                is UiEvent.Message -> Snackbar.make(binding.root, event.res, Snackbar.LENGTH_LONG).show()
                UiEvent.NotSignedIn -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finishAffinity()
                }
                else -> Unit
            }
        }
    }

    private fun showRestoredSnackbar(item: StudentWithPayments) {
        Snackbar.make(
            binding.root,
            getString(R.string.restored_successfully_with_name, item.student.name),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.undo) { viewModel.undoRestore(item) }
            .show()
    }
}
