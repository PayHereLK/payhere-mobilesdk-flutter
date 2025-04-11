##---------------Begin: proguard configuration common for all Android apps ----------
-optimizationpasses 3
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-dump class_files.txt
-printseeds seeds.txt
-printusage unused.txt
##-printmapping mapping.txt
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-allowaccessmodification
-keepattributes *Annotation*
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-repackageclasses ''

-keep public class lk.payhere.androidsdk.model.*{
    *;
}

# Retrofit annotations and interfaces
-keepattributes Signature
-keepattributes *Annotation*

-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# PayHere Classes
-keep interface lk.payhere.androidsdk.PayhereSDK { *; }
-keep interface u2.c { *; }
-keep class lk.payhere.androidsdk.models.PaymentMethodResponse { *; }
-keep class lk.payhere.androidsdk.models.** { *; }
-keep class lk.payhere.androidsdk.** { *; }