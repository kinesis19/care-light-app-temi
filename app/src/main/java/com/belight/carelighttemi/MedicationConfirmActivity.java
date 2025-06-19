package com.belight.carelighttemi;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MedicationConfirmActivity extends AppCompatActivity {

    private static final String TAG = "MedicationConfirm";
    private Robot robot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medication_confirm);

        robot = Robot.getInstance();

        Button btnConfirm = findViewById(R.id.btn_confirm_medication_on_temi);
        btnConfirm.setOnClickListener(v -> {
            speak("네, 확인했습니다. 참 잘하셨어요!");

            // Firestore에 복용 기록을 남기는 명령을 전송함.
            sendMedicationConfirmedCommand();

            // 잠시 후 현재 액티비티를 종료함.
            // TTS가 출력될 시간을 확보하기 위해 약간의 딜레이를 줌.
            new android.os.Handler(getMainLooper()).postDelayed(this::finish, 2000);
        });
    }

    private void speak(String text) {
        TtsRequest ttsRequest = TtsRequest.create(text, true);
        robot.speak(ttsRequest);
    }

    private void sendMedicationConfirmedCommand() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없어 기록에 실패했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = currentUser.getUid();
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Firestore 문서에 직접 날짜를 업데이트하는 기능이다아아아아악
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("lastMedicationTakenDate", todayDate)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully updated medication date to Firestore."))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating medication date.", e));
    }
}
