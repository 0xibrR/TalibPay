package com.irdev.talibpay

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import de.hdodenhof.circleimageview.CircleImageView
import kotlin.properties.Delegates

@Suppress("DEPRECATION")
class SettingsActivity : AppCompatActivity() {


    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: Dialog
    private lateinit var builder: Dialog

    private lateinit var avatar: CircleImageView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView


    private val sharedPrefKey = "appSettings"
    private val nightModeKey = "NightMode"
    lateinit var appPref: SharedPreferences
    lateinit var sharedPrefsEdit: SharedPreferences.Editor
    var nightModeStatus by Delegates.notNull<Int>()


    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        title = getString(R.string.settings)

        initProgressDialog()
        initDialog()

        firebaseAuth = FirebaseAuth.getInstance()


        appPref = this.getSharedPreferences(sharedPrefKey, 0)!!
        nightModeStatus = appPref.getInt("NightMode", 3)
        setThemee(nightModeStatus)


        val btnDeleteAccount = findViewById<MaterialButton>(R.id.btnDeleteAccount)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        val btnChangeTheme = findViewById<MaterialButton>(R.id.btnToggleTheme)
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        val btnChangePassword = findViewById<MaterialButton>(R.id.btnChangePassword)
        avatar = findViewById(R.id.imgAvatar)
        tvName = findViewById(R.id.tvUserName)
        tvEmail = findViewById(R.id.tvUserEmail)



        btnDeleteAccount.setOnClickListener {
            deleteAccount()
        }

        btnLogout.setOnClickListener {
            signOut()
        }

        btnChangeTheme.setOnClickListener {
            setThemeDialog()
        }

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        tvVersion.text = getString(R.string.app_version_placeholder, versionName)

        btnChangePassword.setOnClickListener { showChangePasswordDialog() }

        loadProfile()
    }


    private fun setThemee(nightStatus: Int) {
        when (nightStatus) {
            1 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Log.d("AllNoteFrag", "Light theme SetTheme()")
            }

            2 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Log.d("AllNoteFrag", "Dark theme SetTheme()")
            }

            else -> {
                Log.d("AllNoteFrag", "System theme SetTheme()")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                else
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
            }
        }

    }

    @SuppressLint("UseKtx", "CutPasteId")
    private fun setThemeDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.alert_dialog_theme_select, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        val themeRadioGroup = view.findViewById<RadioGroup>(R.id.theme_button_group)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.findViewById<RadioButton>(R.id.deafultRadioButton).text =
                getString(R.string.system_default)
        } else {
            view.findViewById<RadioButton>(R.id.deafultRadioButton).text =
                getString(R.string.follow_battery_saver)
        }

        when (nightModeStatus) {
            1 -> view.findViewById<RadioButton>(R.id.lightRadioButton).isChecked = true
            2 -> view.findViewById<RadioButton>(R.id.darkRadioButton).isChecked = true
            3 -> view.findViewById<RadioButton>(R.id.deafultRadioButton).isChecked = true
        }

        themeRadioGroup.setOnCheckedChangeListener { _, id ->
            sharedPrefsEdit = appPref.edit()
            when (id) {
                R.id.lightRadioButton -> {
                    sharedPrefsEdit.putInt(nightModeKey, 1)
                    sharedPrefsEdit.apply()
                    nightModeStatus = 1
                    Log.d("AllNoteFrag", "Light theme")
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }

                R.id.darkRadioButton -> {
                    sharedPrefsEdit.putInt(nightModeKey, 2)
                    sharedPrefsEdit.apply()
                    nightModeStatus = 2
                    Log.d("AllNoteFrag", "Dark theme")
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }

                R.id.deafultRadioButton -> {
                    sharedPrefsEdit.putInt(nightModeKey, 3)
                    sharedPrefsEdit.apply()
                    nightModeStatus = 3
                    Log.d("AllNoteFrag", "System theme")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
                    else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)

                }
            }
            dialog.dismiss()
        }

    }

    @SuppressLint("InflateParams", "UseKtx")
    private fun initDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dilaog_email_password, null)
        builder = Dialog(this)
        builder.setContentView(dialogView)
        builder.setCancelable(false)
        builder.setCanceledOnTouchOutside(false)
        builder.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

    }

    @SuppressLint("InflateParams", "UseKtx")
    private fun initProgressDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.progress_bar_dialog, null)
        progressDialog = Dialog(this)
        progressDialog.setContentView(dialogView)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun signOut() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sign_out))
            .setMessage(getString(R.string.signed_out_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                // تسجيل الخروج من Firebase
                firebaseAuth.signOut()

                // تسجيل الخروج من Google
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient = GoogleSignIn.getClient(this, gso)

                googleSignInClient.signOut().addOnCompleteListener {
                    Toast.makeText(
                        this,
                        getString(R.string.signed_out_successfully),
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun signOutDeleteAccount() {
        // Firebase sign out
        FirebaseAuth.getInstance().signOut()

        // Google sign out
        val googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        )
        googleSignInClient.signOut().addOnCompleteListener {
            Toast.makeText(this, getString(R.string.signed_out_successfully), Toast.LENGTH_SHORT)
                .show()

            // إعادة التوجيه إلى شاشة الدخول
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun deleteUserData(uid: String, onComplete: (Boolean) -> Unit) {
        progressDialog.show()

        val databaseRef = FirebaseDatabase.getInstance().reference.child("TalibPay").child(uid)

        databaseRef.removeValue().addOnCompleteListener { task ->

            progressDialog.dismiss()

            if (task.isSuccessful) {
                Toast.makeText(
                    this,
                    getString(R.string.data_deleted_successfully),
                    Toast.LENGTH_SHORT
                ).show()
                onComplete(true)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.failed_to_delete_data) + ": ${task.exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
                onComplete(false)
            }
        }
    }

    private fun deleteAccount() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val currentProviderId = user.providerData.find { it.providerId != "firebase" }?.providerId

        when (currentProviderId) {
            "google.com" -> {
                val account = GoogleSignIn.getLastSignedInAccount(this)
                if (account != null) {
                    val idToken = account.idToken
                    if (idToken != null) {
                        progressDialog.show()

                        val googleCredential = GoogleAuthProvider.getCredential(idToken, null)
                        user.reauthenticate(googleCredential).addOnCompleteListener { reauthTask ->
                            progressDialog.dismiss()
                            if (reauthTask.isSuccessful) {
                                val builderAlertDialog = AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.delete_account))
                                    .setMessage(getString(R.string.delete_account_message))

                                val confirmDialog =
                                    builderAlertDialog.setPositiveButton(getString(R.string.ok)) { _, _ ->
                                        progressDialog.show()
                                        deleteUserData(user.uid) { dataDeleted ->
                                            if (dataDeleted) {
                                                user.delete().addOnCompleteListener { deleteTask ->
                                                    progressDialog.dismiss()
                                                    if (deleteTask.isSuccessful) {
                                                        Toast.makeText(
                                                            this,
                                                            getString(R.string.google_user_deleted_successfully),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        signOutDeleteAccount()
                                                    } else {
                                                        Toast.makeText(
                                                            this,
                                                            "${getString(R.string.error_deleting_google_user)} ${deleteTask.exception?.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            } else {
                                                progressDialog.dismiss()
                                                Toast.makeText(
                                                    this,
                                                    getString(R.string.failed_to_delete_user_data_account_deletion_cancelled),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                        .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                                        .create()

                                confirmDialog.setCanceledOnTouchOutside(false)
                                confirmDialog.setCancelable(false)
                                confirmDialog.show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "${getString(R.string.error_authenticating_google_user)} ${reauthTask.exception?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.unable_to_get_google_id),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.no_google_account_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            "password" -> {
                val btnOk = builder.findViewById<MaterialButton>(R.id.btn_ok_dialog)
                val btnCancel = builder.findViewById<MaterialButton>(R.id.btnCancel_dialog)
                val edtEmail = builder.findViewById<TextInputEditText>(R.id.edt_email_dialog)
                val edtPassword = builder.findViewById<TextInputEditText>(R.id.edt_password_dialog)
                builder.show()

                btnOk.setOnClickListener {
                    val email = edtEmail.text.toString()
                    val password = edtPassword.text.toString()

                    if (check(edtEmail) && check(edtPassword)) {
                        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            progressDialog.show()
                            val credential = EmailAuthProvider.getCredential(email, password)
                            user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                                progressDialog.dismiss()
                                if (reauthTask.isSuccessful) {
                                    val builderAlertDialog = AlertDialog.Builder(this)
                                        .setTitle(getString(R.string.delete_account))
                                        .setMessage(getString(R.string.delete_account_message))

                                    val confirmDialog =
                                        builderAlertDialog.setPositiveButton(getString(R.string.delete_account)) { _, _ ->
                                            progressDialog.show()
                                            deleteUserData(user.uid) { dataDeleted ->
                                                if (dataDeleted) {
                                                    user.delete()
                                                        .addOnCompleteListener { deleteTask ->
                                                            progressDialog.dismiss()
                                                            if (deleteTask.isSuccessful) {
                                                                Toast.makeText(
                                                                    this,
                                                                    getString(R.string.email_password_user_deleted_successfully),
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                                builder.dismiss()
                                                                signOutDeleteAccount()
                                                            } else {
                                                                Toast.makeText(
                                                                    this,
                                                                    "${getString(R.string.error_deleting_email_password_user)} ${deleteTask.exception?.message}",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                        }
                                                } else {
                                                    progressDialog.dismiss()
                                                    Toast.makeText(
                                                        this,
                                                        getString(R.string.failed_to_delete_user_data_account_deletion_cancelled),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                            .setNegativeButton(getString(R.string.cancel)) { d, _ -> d.dismiss() }
                                            .create()

                                    confirmDialog.show()

                                } else {
                                    Toast.makeText(
                                        this,
                                        "${getString(R.string.error_authenticating_email_password_user)} ${reauthTask.exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            edtEmail.error = getString(R.string.check_email)
                        }
                    }
                }

                btnCancel.setOnClickListener {
                    builder.dismiss()
                }
            }

            else -> {
                Toast.makeText(
                    this,
                    getString(R.string.account_provider_type_unknown),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // في حال لم يكن مسجّلًا – يمكنك توجيهه إلى شاشة تسجيل الدخول
            tvName.text = getString(R.string.user_name)
            tvEmail.text = getString(R.string.user_email_com)
            avatar.setImageResource(R.drawable.outline_account_circle_24)
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        // الاسم
        tvName.text = user.displayName ?: getString(R.string.user_name)

        // البريد
        tvEmail.text = user.email ?: getString(R.string.user_email_com)

        // الصورة
        val photoUrl = user.photoUrl
        if (photoUrl != null) {
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.outline_account_circle_24)
                .error(R.drawable.outline_account_circle_24)
                .into(avatar)
        } else {
            avatar.setImageResource(R.drawable.outline_account_circle_24)
        }
    }

    @SuppressLint("MissingInflatedId", "UseKtx", "InflateParams")
    private fun showChangePasswordDialog() {

        /* 1) نفخ التخطيط وإنشاء الـ Dialog */
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val dlg = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        /* 2) مراجع عناصر الواجهة داخل الـDialog */
        val edtOld = dialogView.findViewById<TextInputEditText>(R.id.edtOldPassword)
        val edtNew = dialogView.findViewById<TextInputEditText>(R.id.edtNewPassword)
        val edtConfirm = dialogView.findViewById<TextInputEditText>(R.id.edtConfirmPassword)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave_ChangePassword)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel_ChangePassword)

        /* 3) تحديد نوع الحساب (Has-Password أم Google only) */
        val user = FirebaseAuth.getInstance().currentUser
        val hasPassword =
            user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

        // إخفاء حقل كلمة المرور القديمة إذا كان الحساب Google-only
        edtOld.isVisible = hasPassword

        /* 4) منطق الحفظ */
        btnSave.setOnClickListener {

            val oldPass = edtOld.text.toString().trim()
            val newPass = edtNew.text.toString().trim()
            val confirm = edtConfirm.text.toString().trim()

            // تحققات سريعة
            if (newPass.isEmpty() || confirm.isEmpty() || (hasPassword && oldPass.isEmpty())) {
                Snackbar.make(dialogView, getString(R.string.fill_all_fields), Snackbar.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                edtNew.error = getString(R.string.password_too_short); return@setOnClickListener
            }
            if (newPass != confirm) {
                edtConfirm.error =
                    getString(R.string.passwords_not_match); return@setOnClickListener
            }
            if (user == null) {
                startActivity(
                    Intent(
                        this,
                        LoginActivity::class.java
                    )
                ); finish(); return@setOnClickListener
            }

            btnSave.isEnabled = false   // منع النقر المزدوج

            /* ـــ الحالة ❶: لديه كلمة مرور مسبقًا ـــ */
            if (hasPassword) {
                val cred = EmailAuthProvider.getCredential(user.email ?: "", oldPass)

                user.reauthenticate(cred).addOnSuccessListener {
                    user.updatePassword(newPass).addOnCompleteListener { t ->
                        btnSave.isEnabled = true
                        if (t.isSuccessful) {
                            dlg.dismiss()
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                getString(R.string.password_changed_success),
                                Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            Snackbar.make(
                                dialogView, getString(R.string.password_changed_failed),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }.addOnFailureListener {
                    btnSave.isEnabled = true
                    edtOld.error = getString(R.string.wrong_old_password)
                }

                /* ـــ الحالة ❷: حساب Google فقط ⇒ ربط Password لأول مرة ـــ */
            } else {
                val cred = EmailAuthProvider.getCredential(
                    user.email ?: return@setOnClickListener,
                    newPass
                )

                user.linkWithCredential(cred).addOnCompleteListener { linkTask ->
                    btnSave.isEnabled = true
                    val root = findViewById<View>(android.R.id.content)

                    if (linkTask.isSuccessful) {
                        user.reload().addOnSuccessListener {
                            if (!user.isEmailVerified) {
                                user.sendEmailVerification()
                                Snackbar.make(
                                    root,
                                    getString(R.string.check_your_email_to_verify),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            } else {
                                Snackbar.make(
                                    root,
                                    getString(R.string.password_changed_success),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                            dlg.dismiss()
                        }
                    } else {
                        Snackbar.make(
                            root,
                            getString(R.string.password_changed_failed),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }

            }
        }

        /* 5) إلغاء */
        btnCancel.setOnClickListener { dlg.dismiss() }

        dlg.show()
    }

    private fun check(edt: EditText): Boolean {
        if (edt.text.isEmpty()) {
            edt.error = getString(R.string.enter_value)
            return false
        }
        return true
    }
}