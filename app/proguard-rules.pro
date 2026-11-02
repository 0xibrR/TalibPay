# Firebase Realtime Database يحوّل البيانات إلى الكلاسات بالـ reflection،
# فيجب الإبقاء على أسماء الحقول والـ constructor الافتراضي للموديلات.
-keepattributes Signature,*Annotation*
-keep class com.irdev.talibpay.model.** { *; }

# أرقام الأسطر في الـ stack traces لتسهيل تتبع الأعطال، مع إخفاء أسماء الملفات الأصلية
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
