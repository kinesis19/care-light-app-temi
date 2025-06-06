package com.belight.carelighttemi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable; // Nullable import 추가
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration; // 리스너 관리를 위해 추가

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "TemiHomeActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration commandListener; // Firestore 리스너 참조 변수

    // 가장 최근에 처리한 명령의 타임스탬프를 저장 (중복 실행 방지용)
    private com.google.firebase.Timestamp lastProcessedTimestamp = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Firebase 인스턴스 초기화
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // TODO: 하단 메뉴 버튼들에 대한 OnClickListener 설정 (필요시)
        // 예: findViewById(R.id.btn_settings).setOnClickListener(...)
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 액티비티가 사용자에게 보일 때 리스너를 설정하고 시작합니다.
        setupCommandListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 액티비티가 보이지 않게 되면 리스너를 제거하여 리소스를 절약합니다.
        if (commandListener != null) {
            commandListener.remove();
            Log.d(TAG, "Command listener removed.");
        }
    }

    private void setupCommandListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // 로그인 상태가 아니면 로그인 화면으로 보냅니다.
            Log.e(TAG, "User not logged in. Redirecting to LoginActivity.");
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        String userUid = currentUser.getUid();
        final DocumentReference userDocRef = db.collection("users").document(userUid);

        Log.d(TAG, "Setting up command listener for user: " + userUid);
        commandListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                // [수정 1] Unchecked Cast 경고 해결을 위해 Object로 먼저 받고 instanceof로 타입 확인
                Object commandObject = snapshot.get("temiCommand");

                if (commandObject instanceof Map) {
                    // 타입이 Map인 것을 확인했으므로 안전하게 형 변환
                    Map<String, Object> temiCommand = (Map<String, Object>) commandObject;

                    String command = (String) temiCommand.get("command");
                    com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) temiCommand.get("timestamp");

                    // [수정 2] NullPointerException 방지를 위해 timestamp null 체크
                    if (timestamp == null) {
                        Log.w(TAG, "Command received without a timestamp. Ignoring.");
                        return; // timestamp가 없으면 이벤트를 처리하지 않음
                    }

                    // 유효한 새 명령인지 확인
                    if (!"none".equals(command) && (lastProcessedTimestamp == null || timestamp.compareTo(lastProcessedTimestamp) > 0)) {
                        lastProcessedTimestamp = timestamp; // 처리한 타임스탬프 업데이트
                        String message = (String) temiCommand.get("message");

                        if ("showToast".equals(command) && message != null) {
                            Toast.makeText(HomeActivity.this, "명령 수신: " + message, Toast.LENGTH_LONG).show();
                            // TODO: Temi SDK 로직 추가
                        }

                        // 명령 처리 후 초기화
                        clearCommandInFirestore(userDocRef);
                    }
                }
            } else {
                Log.d(TAG, "Current data: null");
            }
        });
    }

    private void clearCommandInFirestore(DocumentReference userDocRef) {
        Map<String, Object> resetCommand = new HashMap<>();
        resetCommand.put("command", "none");
        resetCommand.put("message", "Command cleared");
        resetCommand.put("timestamp", FieldValue.serverTimestamp());

        userDocRef.update("temiCommand", resetCommand)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Command field successfully cleared!"))
                .addOnFailureListener(e -> Log.w(TAG, "Error clearing command field", e));
    }
}