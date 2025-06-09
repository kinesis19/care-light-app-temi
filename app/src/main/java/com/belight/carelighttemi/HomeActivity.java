package com.belight.carelighttemi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Nullable import 추가
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration; // 리스너 관리를 위해 추가
import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.activitystream.ActivityStreamPublishMessage;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.robotemi.sdk.listeners.OnLocationsUpdatedListener;
import com.robotemi.sdk.listeners.OnRobotLiftedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends AppCompatActivity implements
        Robot.NlpListener,
        OnRobotReadyListener,
        Robot.ConversationViewAttachesListener,
        Robot.WakeupWordListener,
        Robot.ActivityStreamPublishListener,
        Robot.TtsListener,
        OnBeWithMeStatusChangedListener,
        OnGoToLocationStatusChangedListener,
        OnLocationsUpdatedListener {

    private Robot robot;

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

        robot = Robot.getInstance();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupCommandListener();

        robot.addOnRobotReadyListener(this);
        robot.addNlpListener(this);
        robot.addOnBeWithMeStatusChangedListener(this);
        robot.addOnGoToLocationStatusChangedListener(this);
        robot.addConversationViewAttachesListenerListener(this);
        robot.addWakeupWordListener(this);
        robot.addTtsListener(this);
        robot.addOnLocationsUpdatedListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 액티비티가 보이지 않게 되면 리스너를 제거하여 리소스를 절약함.
        if (commandListener != null) {
            commandListener.remove();
            Log.d(TAG, "Command listener removed.");
        }

        robot.removeOnRobotReadyListener(this);
        robot.removeNlpListener(this);
        robot.removeOnBeWithMeStatusChangedListener(this);
        robot.removeOnGoToLocationStatusChangedListener(this);
        robot.removeConversationViewAttachesListenerListener(this);
        robot.removeWakeupWordListener(this);
        robot.removeTtsListener(this);
        robot.removeOnLocationsUpdateListener(this);
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
                Object commandObject = snapshot.get("temiCommand");

                if (commandObject instanceof Map) {
                    // 타입이 Map인 것을 확인했으므로 안전하게 형 변환
                    Map<String, Object> temiCommand = (Map<String, Object>) commandObject;

                    String command = (String) temiCommand.get("command");
                    com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) temiCommand.get("timestamp");

                    if (timestamp == null) {
                        Log.w(TAG, "Command received without a timestamp. Ignoring.");
                        return; // timestamp가 없으면 이벤트를 처리하지 않음
                    }

                    if (!"none".equals(command) && (lastProcessedTimestamp == null || timestamp.compareTo(lastProcessedTimestamp) > 0)) {
                        lastProcessedTimestamp = timestamp;
                        processTemiCommand(temiCommand);
                        clearCommandInFirestore(userDocRef);
                    }
                }
            } else {
                Log.d(TAG, "Current data: null");
            }
        });
    }

    // Feat: 수신된 명령을 처리하는 메소드
    private void processTemiCommand(Map<String, Object> commandData) {
        String command = (String) commandData.get("command");
        String message = (String) commandData.get("message");

        if (command == null || message == null) {
            Log.w(TAG, "Command or message is null.");
            return;
        }

        Log.d(TAG, "Processing command: " + command + " with message: " + message);

        switch (command) {
            case "showToast":
                Toast.makeText(HomeActivity.this, "명령 수신: " + message, Toast.LENGTH_LONG).show();
                break;

            case "speak":
                message = "아 집에 가고 싶다 피곤하다 ";
                speak(message);
                break;

            // TODO: 향후 다른 명령 추가
            // case "navigateTo":
            //     robot.goTo(message); // message에 장소 이름("주방", "거실" 등)을 담아 보냄
            //     break;

            default:
                Log.w(TAG, "Unknown command received: " + command);
                break;
        }
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

    @Override
    public void onPublish(@NonNull ActivityStreamPublishMessage activityStreamPublishMessage) {

    }

    @Override
    public void onConversationAttaches(boolean b) {

    }

    @Override
    public void onNlpCompleted(@NonNull NlpResult nlpResult) {

    }

    @Override
    public void onTtsStatusChanged(@NonNull TtsRequest ttsRequest) {

    }

    @Override
    public void onWakeupWord(@NonNull String s, int i) {

    }

    @Override
    public void onBeWithMeStatusChanged(@NonNull String s) {

    }

    @Override
    public void onGoToLocationStatusChanged(@NonNull String s, @NonNull String s1, int i, @NonNull String s2) {

    }

    @Override
    public void onLocationsUpdated(@NonNull List<String> list) {

    }

    @Override
    public void onRobotReady(boolean isReady) {

        if (isReady) { // 로봇이 준비되었다면
            try {
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void speak(String textToSpeak) {
        if (robot == null) {
            Log.e(TAG, "Robot instance is not initialized!");
            return;
        }

        if (textToSpeak != null && !textToSpeak.isEmpty()) {
            TtsRequest ttsRequest = TtsRequest.create(textToSpeak, true);
            robot.speak(ttsRequest);
            Log.d(TAG, "Speaking: " + textToSpeak);
        } else {
            Log.d(TAG, "Speak command received with empty text.");
        }
    }
}