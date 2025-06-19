[1mdiff --git a/app/src/main/AndroidManifest.xml b/app/src/main/AndroidManifest.xml[m
[1mindex fed1ba9..06e8163 100644[m
[1m--- a/app/src/main/AndroidManifest.xml[m
[1m+++ b/app/src/main/AndroidManifest.xml[m
[36m@@ -33,21 +33,27 @@[m
                 <category android:name="android.intent.category.LAUNCHER" />[m
             </intent-filter>[m
         </activity>[m
[31m-        [m
[32m+[m
         <activity[m
             android:name=".MedicationConfirmActivity"[m
             android:exported="false"[m
[31m-            android:theme="@style/Theme.AppCompat.NoActionBar" />[m
[32m+[m[32m            android:theme="@style/Theme.CareLightTemi" />[m
[32m+[m
 [m
         <receiver[m
             android:name=".AlarmReceiver"[m
             android:enabled="true"[m
[31m-            android:exported="false" />[m
[32m+[m[32m            android:exported="true">[m
[32m+[m[32m            <intent-filter>[m
[32m+[m[32m                <action android:name="android.intent.action.BOOT_COMPLETED" />[m
[32m+[m[32m            </intent-filter>[m
[32m+[m[32m        </receiver>[m
 [m
         <meta-data[m
             android:name="com.robotemi.sdk.metadata.SKILL"[m
             android:value="@string/app_name" />[m
 [m
[32m+[m
     </application>[m
 [m
 </manifest>[m
\ No newline at end of file[m
[1mdiff --git a/app/src/main/java/com/belight/carelighttemi/HomeActivity.java b/app/src/main/java/com/belight/carelighttemi/HomeActivity.java[m
[1mindex 5e8d6c9..2ac6967 100644[m
[1m--- a/app/src/main/java/com/belight/carelighttemi/HomeActivity.java[m
[1m+++ b/app/src/main/java/com/belight/carelighttemi/HomeActivity.java[m
[36m@@ -4,6 +4,8 @@[m [mimport android.content.Intent;[m
 import android.os.Bundle;[m
 import android.util.Log;[m
 import android.widget.Toast;[m
[32m+[m[32mimport android.os.Handler;[m
[32m+[m[32mimport android.os.Looper;[m
 [m
 import android.app.AlarmManager;[m
 import android.app.PendingIntent;[m
[36m@@ -311,12 +313,21 @@[m [mpublic class HomeActivity extends AppCompatActivity implements[m
                 }[m
 [m
                 if (isWaitingForMedicationConfirmation) {[m
[31m-                    speak("어르신, 약은 다 드셨나요? 다 드셨으면 '먹었어'라고 말씀해주세요.", false);[m
[32m+[m[32m                    isWaitingForMedicationConfirmation = false;[m
[32m+[m
[32m+[m[32m                    speak("어르신, 약 드시고 화면의 버튼을 눌러주세요.", true);[m
[32m+[m
[32m+[m[32m                    new Handler(Looper.getMainLooper()).postDelayed(() -> {[m
[32m+[m[32m                        Intent intent = new Intent(HomeActivity.this, MedicationConfirmActivity.class);[m
[32m+[m[32m                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);[m
[32m+[m[32m                        startActivity(intent);[m
[32m+[m[32m                    }, 2000); // 2초 딜레이[m
                 }[m
 [m
                 break;[m
             case OnGoToLocationStatusChangedListener.ABORT:[m
                 speak("이동이 취소되었습니다.", true);[m
[32m+[m[32m                isWaitingForMedicationConfirmation = false;[m
                 targetAngleOnArrival = null;[m
                 break;[m
         }[m
[36m@@ -396,14 +407,7 @@[m [mpublic class HomeActivity extends AppCompatActivity implements[m
 [m
     @Override[m
     public void onNlpCompleted(@NonNull NlpResult nlpResult) {[m
[31m-        String recognizedText = nlpResult.action;[m
[31m-        Log.d(TAG, "Nlp Result: " + recognizedText);[m
[31m-        if (isWaitingForMedicationConfirmation) {[m
[31m-            if (recognizedText != null && (recognizedText.contains("먹었어") || recognizedText.contains("먹었다"))) {[m
[31m-                confirmMedicationTaken();[m
[31m-                isWaitingForMedicationConfirmation = false;[m
[31m-            }[m
[31m-        }[m
[32m+[m
     }[m
 [m
     private void confirmMedicationTaken() {[m
