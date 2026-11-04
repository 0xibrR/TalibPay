# TalibPay

**Student tuition and payments, organized.**
An Android app for tutors, centers and small schools to track students, monthly fees, payments and balances, built with Kotlin and Firebase.

> **بالعربي:** TalibPay تطبيق أندرويد لإدارة رسوم الطلاب ودفعاتهم: رسوم شهرية تلقائية، دفعات نقدية وبنكية، محفظة للمبالغ الزائدة، تذكير الأهالي عبر واتساب، إيصالات وكشوف حساب، تقارير شهرية وتصدير Excel، مع قفل بالبصمة أو الرمز. الواجهة بالعربية والإنجليزية.

---

## Features

### Students
- Add, edit, archive and restore students (swipe to archive, with undo); delete from the student's page only.
- Registration date, phone with country code, notes and **attendance days** (e.g. Sat, Tue, Thu).
- Search, filters (overdue, partially paid, fully paid) and sorting.

### Fees and payments
- **Automatic monthly fees** generated on the registration day each month, even when the app is closed (WorkManager). Optional backdating since registration.
- **Discounts** per student (percentage or fixed amount, with a reason).
- **Dues (charges)**: monthly or manual (books, registration...). Every payment is recorded against a specific due.
- **Cash and bank** payments. Anything paid above what's due goes to the student's **wallet** and is used automatically for the next dues. Wallet refunds are supported.
- Every money operation runs inside a Firebase transaction, so it stays correct across devices and offline.

### Documents and reports
- **Payment receipts** and **account statements**, shared as an image or PDF.
- **Monthly report**: collected money, the month's dues, who hasn't paid, and a **collection chart** for recent months.
- **Excel export** (.xlsx) of students, dues and payments.
- **WhatsApp reminders** to parents with a ready message listing overdue dues, from a student's page or from the overdue list.

### Security and settings
- Sign in with email/password (with email verification) or Google.
- Optional **app lock** with fingerprint and/or a 6-digit PIN: the PIN is never stored (PBKDF2 + Android Keystore HMAC), settings are signed against tampering, and wrong attempts trigger growing waits.
- Arabic and English (per-app language), light/dark theme and multiple currencies.
- **Announcements** to all users via Firebase Cloud Messaging (service issues, new updates).

---

## Tech stack

| Area | Used |
|------|------|
| Language | Kotlin, Coroutines and Flow |
| Architecture | MVVM (ViewModel, StateFlow), Repository |
| UI | Material 3, ViewBinding, RecyclerView |
| Backend | Firebase Authentication, Realtime Database (offline persistence), Cloud Messaging |
| Background work | WorkManager |
| Security | BiometricPrompt, Android Keystore |
| Minimum Android | 7.0 (API 24), target API 35 |

No third-party libraries are used for PDF, Excel or charts; they're built in.

---

## Project structure

```
app/src/main/java/com/irdev/talibpay/
├── activity/       Screens
├── adapter/        RecyclerView adapters
├── application/    Application class (startup setup)
├── data/           Firebase repositories, monthly dues worker
├── domain/         Business logic: billing, ledger, wallet, discounts, reports (unit tested)
├── model/          Firebase data models
├── notification/   FCM announcements
├── security/       App lock: PIN, fingerprint, lockout policy
├── util/           UI helpers, exports (PDF, image, Excel), chart
└── viewmodel/      ViewModels
```

Firebase data is stored per user under `TalibPay/{uid}/students/{studentId}` (with `charges`, `payments` and `wallet`). Access and data shape are enforced by [`database.rules.json`](database.rules.json).

---

## Getting started

1. Clone the repo and open it in Android Studio.
2. Create a Firebase project and add an Android app with the package `com.irdev.talibpay`. Replace `app/google-services.json` with your own.
3. In Firebase Console:
   - **Authentication**: enable **Email/Password** and **Google**, and add your app's SHA-1 fingerprint for Google sign-in.
   - **Realtime Database**: create a database, then deploy the rules (they include the indexes the app needs):
     ```bash
     firebase deploy --only database
     ```
4. Run the app from Android Studio.

### Running the tests
```bash
./gradlew testDebugUnitTest
```

---

## Sending announcements

From **Firebase Console → Messaging → New campaign → Notification**, set the target to a **Topic**:

| Topic | Who receives it |
|-------|-----------------|
| `announcements` | All users |
| `announcements_ar` | Users with the app in Arabic |
| `announcements_en` | Users with the app in English |

Set the Android notification channel to `announcements`. To open a link when the notification is tapped (for example an update page), add custom data with the key `url` and an `https://` link.

---

## Author

**Ibrahim Ramadan**
