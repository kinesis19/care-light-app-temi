package com.belight.carelighttemi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String message = intent.getStringExtra("ALARM_MESSAGE");
        if (message != null && !message.isEmpty()) {
            Log.d(TAG, "Alarm received. Speaking message: " + message);

            // robot 인스턴스를 직접 가져와서 TTS를 실행함.
            Robot robot = Robot.getInstance();
            // TTS가 다른 동작을 방해하지 않도록 isShowOnConversationLayer를 true로 설정함.
            TtsRequest ttsRequest = TtsRequest.create(message, true);
            robot.speak(ttsRequest);
        }
    }
}
