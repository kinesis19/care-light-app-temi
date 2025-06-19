package com.belight.carelighttemi;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.activitystream.ActivityStreamPublishMessage;
import com.robotemi.sdk.listeners.OnBeWithMeStatusChangedListener;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.robotemi.sdk.listeners.OnLocationsUpdatedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.listeners.OnBatteryStatusChangedListener;

import com.robotemi.sdk.BatteryData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Calendar;
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
        OnLocationsUpdatedListener,
        OnBatteryStatusChangedListener {

    private Robot robot;

    private static final String TAG = "TemiHomeActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference userDocRef;
    private ListenerRegistration commandListener;

    private com.google.firebase.Timestamp lastProcessedTimestamp = null;
    private int lastKnownBatteryPercentage = -1;
    private boolean lastKnownChargingStatus = false;
    private boolean isWaitingForMedicationConfirmation = false;
    private Float targetAngleOnArrival = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        robot = Robot.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userDocRef = db.collection("users").document(currentUser.getUid());
        }
    }

  @Override
    protected void onStart() {
        super.onStart();
        robot.addNlpListener(this);
        robot.addOnRobotReadyListener(this);
        robot.addOnBeWithMeStatusChangedListener(this);
        robot.addOnGoToLocationStatusChangedListener(this);
        robot.addConversationViewAttachesListenerListener(this);
        robot.addWakeupWordListener(this);
        robot.addTtsListener(this);
        robot.addOnLocationsUpdatedListener(this);
        robot.addOnBatteryStatusChangedListener(this);

        setupCommandListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        robot.removeOnRobotReadyListener(this);
        robot.removeOnLocationsUpdateListener(this);
        robot.removeOnGoToLocationStatusChangedListener(this);
        robot.removeOnBatteryStatusChangedListener(this);
        robot.removeNlpListener(this);
        robot.removeConversationViewAttachesListenerListener(this);
        robot.removeWakeupWordListener(this);
        robot.removeTtsListener(this);
        robot.removeOnBeWithMeStatusChangedListener(this);

        if (commandListener != null) {
            commandListener.remove();
        }
    }

    private void setupCommandListener() {
        if (userDocRef == null) return;
        commandListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) {
                Log.w(TAG, "Listen error or data null", e);
                return;
            }
            Object commandObject = snapshot.get("temiCommand");
            if (commandObject instanceof Map) {
                Map<String, Object> temiCommand = (Map<String, Object>) commandObject;
                String command = (String) temiCommand.get("command");
                com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) temiCommand.get("timestamp");
                if (command == null || timestamp == null) return;

                if (!"none".equals(command) && (lastProcessedTimestamp == null || timestamp.compareTo(lastProcessedTimestamp) > 0)) {
                    lastProcessedTimestamp = timestamp;
                    processTemiCommand(temiCommand);
                    if (!"skidJoy".equals(command)) {
                        clearCommandInFirestore();
                    }
                }
            }
        });
    }

    // Feat: 수신된 명령을 처리하는 메소드
    private void processTemiCommand(Map<String, Object> commandData) {
        String command = (String) commandData.get("command");
        if (command == null) return;
        Log.d(TAG, "Processing command: " + command);

        switch (command) {
            case "speak": {
                String message = (String) commandData.get("message");
                if (message != null) speak(message, true); // 일반적인 말하기는 UI를 가리지 않음
                break;
            }
            case "goToLocation": {
                Object paramsObject = commandData.get("parameters");
                if (paramsObject instanceof Map) {
                    Map<String, Object> params = (Map<String, Object>) paramsObject;
                    String location = (String) params.get("location");
                    String message = (String) commandData.get("message");
                    Number angleNumber = (Number) params.get("angle");
                    targetAngleOnArrival = (angleNumber != null) ? angleNumber.floatValue() : null;

                    if (message != null && (message.contains("약") || message.contains("전달"))) {
                        isWaitingForMedicationConfirmation = true;
                    }
                    if (location != null) goToLocation(location);
                }
                break;
            }
            case "saveLocation": {
                Object paramsObject = commandData.get("parameters");
                if (paramsObject instanceof Map) {
                    String locationName = (String) ((Map<?, ?>) paramsObject).get("locationName");
                    if (locationName != null && !locationName.isEmpty()) saveLocationWithName(locationName);
                }
                break;
            }
            case "deleteLocation": {
                Object paramsObject = commandData.get("parameters");
                if (paramsObject instanceof Map) {
                    String locationName = (String) ((Map<?, ?>) paramsObject).get("locationName");
                    if (locationName != null && !locationName.isEmpty()) {
                        if (robot.deleteLocation(locationName)) speak("위치를 삭제했습니다.", true);
                        else speak("위치 삭제에 실패했습니다.", true);
                    }
                }
                break;
            }
            case "skidJoy": {
                Object paramsObject = commandData.get("parameters");
                if (paramsObject instanceof Map) {
                    Map<String, Object> params = (Map<String, Object>) paramsObject;
                    Number linear = (Number) params.get("x");
                    Number angular = (Number) params.get("y");
                    if (linear != null && angular != null) robot.skidJoy(linear.floatValue(), angular.floatValue());
                }
                break;
            }
            case "setAlarms": {
                Object paramsObject = commandData.get("parameters");
                if (paramsObject instanceof Map) {
                    Map<String, Object> params = (Map<String, Object>) paramsObject;
                    String wakeupTime = (String) params.get("wakeupTime");
                    String medicationTime = (String) params.get("medicationTime");
                    if (wakeupTime != null) {
                        String[] parts = wakeupTime.split(":");
                        setAlarm(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), "WAKE_UP");
                    }
                    if (medicationTime != null) {
                        String[] parts = medicationTime.split(":");
                        setAlarm(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), "MEDICATION");
                    }
                    speak("알람이 설정되었습니다.", true);
                }
                break;
            }
            case "medicationConfirmed": {
                speak("네, 약 복용을 확인했습니다. 잘 하셨어요!", true);
                break;
            }
            default:
                Log.w(TAG, "Unknown command: " + command);
                break;
        }
    }


    // Feat: 위치 이동 및 도착 후 회전 기능
    private void goToLocation(String location) {
        String matchedLocation = robot.getLocations().stream()
                .filter(loc -> loc.equalsIgnoreCase(location))
                .findFirst().orElse(null);
        if (matchedLocation != null) robot.goTo(matchedLocation);
        else speak("오류: '" + location + "' 위치가 저장되어 있지 않습니다.", true);
    }

    private void saveLocationWithName(String locationName) {
        boolean nameExists = robot.getLocations().stream().anyMatch(loc -> loc.equalsIgnoreCase(locationName));
        if (nameExists) speak("'" + locationName + "' 이라는 이름은 이미 사용 중입니다.", true);
        else if (robot.saveLocation(locationName)) speak(locationName + " 위치를 저장했습니다.", true);
        else speak(locationName + " 위치 저장에 실패했습니다.", true);
    }


    private void clearCommandInFirestore() {
        if (userDocRef == null) return;
        Map<String, Object> resetCommand = new HashMap<>();
        resetCommand.put("command", "none");
        resetCommand.put("timestamp", FieldValue.serverTimestamp());
        userDocRef.update("temiCommand", resetCommand);
    }

    @Override
    public void onPublish(@NonNull ActivityStreamPublishMessage activityStreamPublishMessage) {

    }

    @Override
    public void onConversationAttaches(boolean b) {

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
        Log.i(TAG, "onGoToLocationStatusChanged: Received status '" + status + "' FOR location '" + location + "'. Description: " + description);
        Toast.makeText(this, location + "으로 이동: " + status, Toast.LENGTH_SHORT).show();

        if (userDocRef != null) {
            Map<String, Object> stateUpdate = new HashMap<>();
            String statusMessage;
            switch (status) {
                case OnGoToLocationStatusChangedListener.START:
                case OnGoToLocationStatusChangedListener.GOING:
                    statusMessage = location + "으로 이동 중";
                    break;
                case OnGoToLocationStatusChangedListener.COMPLETE:
                    statusMessage = location; // 도착 완료 시, 현재 위치는 목적지 이름
                    break;
                case OnGoToLocationStatusChangedListener.ABORT:
                    statusMessage = "이동 취소됨";
                    break;
                default:
                    statusMessage = "상태 확인 중";
                    break;
            }
            stateUpdate.put("robotState.currentLocation", statusMessage);
            userDocRef.update(stateUpdate);
        }

        switch (status) {
            case OnGoToLocationStatusChangedListener.START:
                speak(location + "으로 이동을 시작합니다.", true);
                break;
            case OnGoToLocationStatusChangedListener.CALCULATING:
                // 경로 계산 중
                break;
            case OnGoToLocationStatusChangedListener.GOING:
                // 이동 중
                break;
            case OnGoToLocationStatusChangedListener.COMPLETE:
                speak(location + "에 도착했습니다.", true);
                if (targetAngleOnArrival != null) {
                    robot.turnBy(targetAngleOnArrival.intValue(), 1.0f);
                    targetAngleOnArrival = null;
                }

                if (isWaitingForMedicationConfirmation) {
                    isWaitingForMedicationConfirmation = false;

                    speak("어르신, 약 드시고 화면의 버튼을 눌러주세요.", true);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(HomeActivity.this, MedicationConfirmActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }, 2000); // 2초 딜레이
                }

                break;
            case OnGoToLocationStatusChangedListener.ABORT:
                speak("이동이 취소되었습니다.", true);
                isWaitingForMedicationConfirmation = false;
                targetAngleOnArrival = null;
                break;
        }
    }

    @Override
    public void onLocationsUpdated(@NonNull List<String> locations) {
        // 위치가 새로 저장되거나 업데이트될 때마다 호출됨
        String locationListStr = locations.stream().collect(Collectors.joining(", "));
        Log.d(TAG, "Updated locations: [" + locationListStr + "]");
        Toast.makeText(this, "위치 목록이 업데이트되었습니다.", Toast.LENGTH_SHORT).show();

        // Firestore의 robotState 필드를 업데이트
        if (userDocRef != null) {
            userDocRef.update("robotState.savedLocations", locations)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully updated locations in Firestore."))
                    .addOnFailureListener(e -> Log.w(TAG, "Error updating locations in Firestore.", e));
        }
    }

    @Override
    public void onRobotReady(boolean isReady) {

        if (isReady) {
            try {
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                robot.onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void speak(String text, boolean showOnConversationLayer) {
        if (text == null || text.isEmpty()) return;
        TtsRequest ttsRequest = TtsRequest.create(text, showOnConversationLayer);
        robot.speak(ttsRequest);
    }

    @Override
    public void onBatteryStatusChanged(@Nullable BatteryData batteryData) {
        BatteryData currentBatteryData = robot.getBatteryData();

        // 만약 최신 배터리 정보를 가져올 수 없다면, 아무것도 하지 않고 종료함.
        if (currentBatteryData == null) {
            return;
        }

        int currentPercentage = currentBatteryData.getBatteryPercentage();
        boolean isCurrentlyCharging = currentBatteryData.isCharging();

        // 마지막으로 기록된 값과 다를 경우에만 Firestore 정보를 업데이트함.
            if (currentPercentage != lastKnownBatteryPercentage || isCurrentlyCharging != lastKnownChargingStatus) {
            Log.d(TAG, "Battery status changed: " + currentPercentage + "%, Charging: " + isCurrentlyCharging);

            // 업데이트할 데이터를 담을 맵 생성
            Map<String, Object> batteryUpdates = new HashMap<>();
            batteryUpdates.put("robotState.batteryPercentage", currentPercentage);
            batteryUpdates.put("robotState.isCharging", isCurrentlyCharging);

            // 충전 중일 때만 상태 메시지를 오버라이드함.
            if (isCurrentlyCharging) {
                batteryUpdates.put("robotState.statusMessage", "충전 중");
            }

            if (userDocRef != null) {
                userDocRef.update(batteryUpdates)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully updated battery status in Firestore."))
                        .addOnFailureListener(e -> Log.w(TAG, "Error updating battery status in Firestore.", e));
            }

            // 마지막 상태 업데이트
            lastKnownBatteryPercentage = currentPercentage;
            lastKnownChargingStatus = isCurrentlyCharging;
        }
    }

    @Override
    public void onNlpCompleted(@NonNull NlpResult nlpResult) {

    }

    private void confirmMedicationTaken() {
        speak("네, 알겠습니다. 복용 완료로 기록할게요.", true);
        if (userDocRef != null) {
            String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            userDocRef.update("lastMedicationTakenDate", todayDate);
        }
    }

    private void setAlarm(int hour, int minute, String alarmType) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        String message = "";
        int requestCode = 0;
        if ("WAKE_UP".equals(alarmType)) {
            message = "어르신, 좋은 아침입니다! 일어나실 시간이에요.";
            requestCode = 1001;
        } else if ("MEDICATION".equals(alarmType)) {
            message = "어르신, 약 드실 시간입니다.";
            requestCode = 1002;
        }
        intent.putExtra("ALARM_MESSAGE", message);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        } catch (SecurityException e) {
            Toast.makeText(this, "알람 설정 권한이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}