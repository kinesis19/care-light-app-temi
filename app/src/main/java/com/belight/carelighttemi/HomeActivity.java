package com.belight.carelighttemi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import java.util.stream.Collectors;

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
    // 도착 후, 회전할 각도
    private Float targetAngleOnArrival = null;



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

        Button btnSaveLocation = findViewById(R.id.btn_save_location);
        btnSaveLocation.setOnClickListener(v -> {
            String locationName = "Room1";

            // 현재 저장된 모든 위치 목록을 가져옴
            List<String> locations = robot.getLocations();

            // 이미 존재하는지 확인함.
            boolean locationExists = false;
            for (String loc : locations) {
                if (loc.equalsIgnoreCase(locationName)) {
                    locationExists = true;
                    break;
                }
            }

            // 만약 위치가 존재한다면 삭제함.
            if (locationExists) {
                boolean deleteResult = robot.deleteLocation(locationName);
                Log.d(TAG, "Location '" + locationName + "' already exists. Deleting it first. Deletion result: " + deleteResult);
            }

            // 현재 위치를 "Room1"으로 저장함.
            boolean saveResult = robot.saveLocation(locationName);
            if (saveResult) {
                speak("현재 위치를 " + locationName + "으로 저장합니다.");
                Toast.makeText(this, locationName + " 위치 저장 요청됨", Toast.LENGTH_SHORT).show();
            } else {
                speak(locationName + " 위치 저장에 실패했습니다. 지도가 생성되었는지, 로봇이 안정된 상태인지 확인해주세요.");
                Toast.makeText(this, locationName + " 위치 저장 실패", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnDeleteLocation = findViewById(R.id.btn_delete_location); // XML에 해당 ID의 버튼 추가 필요
        btnDeleteLocation.setOnClickListener(v -> {
            String locationToDelete = "Room1";
            boolean result = robot.deleteLocation(locationToDelete);
            if (result) {
                speak(locationToDelete + " 위치를 삭제했습니다.");
                Toast.makeText(this, locationToDelete + " 위치 삭제 성공", Toast.LENGTH_SHORT).show();
            } else {
                speak(locationToDelete + " 위치 삭제에 실패했습니다.");
                Toast.makeText(this, locationToDelete + " 위치 삭제 실패", Toast.LENGTH_SHORT).show();
            }
        });
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

        if (command == null) {
            Log.w(TAG, "Command is null.");
            return;
        }

        Log.d(TAG, "Processing command: " + command);

        switch (command) {
            case "showToast":
            case "speak":
                String message = (String) commandData.get("message");
                if (message != null) {
                    if (command.equals("showToast")) {
                        Toast.makeText(HomeActivity.this, "명령 수신: " + message, Toast.LENGTH_LONG).show();
                    } else {
                        speak(message);
                    }
                }
                break;

            case "getMedicine":
                // 파라미터 맵 추출
                Object paramsObject = commandData.get("parameters");
                if (paramsObject instanceof Map) {
                    Map<String, Object> params = (Map<String, Object>) paramsObject;
                    String location = (String) params.get("location");
                    // Firestore에서 숫자는 Long으로 오는 경우가 많으므로 Long으로 받고 float으로 변환
                    Number angleNumber = (Number) params.get("angle");
                    Float angle = (angleNumber != null) ? angleNumber.floatValue() : null;

                    if (location != null) {
                        goToLocationWithAngle(location, angle);
                    } else {
                        Log.w(TAG, "getMedicine command is missing 'location' parameter.");
                    }
                }
                break;

            default:
                Log.w(TAG, "Unknown command received: " + command);
                break;
        }
    }

    // Feat: 위치 이동 및 도착 후 회전 기능
    private void goToLocationWithAngle(String location, Float angle) {
        List<String> locations = robot.getLocations();
        boolean locationExists = locations.stream().anyMatch(loc -> loc.equalsIgnoreCase(location));

        if (locationExists) {
            // 도착 후 회전할 각도를 멤버 변수에 저장
            this.targetAngleOnArrival = angle;
            speak(location + "(으)로 이동합니다.");
            robot.goTo(location);
        } else {
            String errorMessage = "오류: '" + location + "' 위치가 저장되어 있지 않습니다.";
            speak(errorMessage);
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            // 이동 명령이 아니므로 임시 각도 초기화
            this.targetAngleOnArrival = null;
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
    public void onGoToLocationStatusChanged(@NonNull String location, @NonNull String status, int descriptionId, @NonNull String description) {
        Log.d(TAG, "onGoToLocationStatusChanged: location=" + location + ", status=" + status + ", description=" + description);
        Toast.makeText(this, location + "으로 이동: " + status, Toast.LENGTH_SHORT).show();

        switch (status) {
            case OnGoToLocationStatusChangedListener.START:
                // speak(location + "으로 이동을 시작합니다."); // goToLocationWithAngle 메소드에서 이미 말했으므로 중복될 수 있어 주석 처리
                break;

            case OnGoToLocationStatusChangedListener.COMPLETE:
                speak(location + "에 도착했습니다.");
                if (targetAngleOnArrival != null) {
                    speak(targetAngleOnArrival + "도 회전합니다.");
                    robot.turnBy(targetAngleOnArrival.intValue(), 1.0f); // 1.0f는 속도, 조절 가능
                    targetAngleOnArrival = null; // 사용 후에는 반드시 null로 초기화하여 다음 이동에 영향 없게 함
                }
                break;

            case OnGoToLocationStatusChangedListener.ABORT:
                speak("이동이 취소되었습니다.");
                targetAngleOnArrival = null;
                break;
        }
    }

    @Override
    public void onLocationsUpdated(@NonNull List<String> locations) {
        // 위치가 새로 저장되거나 업데이트될 때마다 호출됨
        String locationList = locations.stream().collect(Collectors.joining(", "));
        Log.d(TAG, "Updated locations: [" + locationList + "]");
        Toast.makeText(this, "위치 목록이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();
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