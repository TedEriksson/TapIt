package uk.co.vism.tapit;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.annotation.IntDef;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Random;

public class MainActivity extends WearableActivity implements SensorEventListener {

    private static final int SHAKE_THRESHOLD = 1200;
    private static final int TWIST_THRESHOLD = 6;

    private static final int START_GAP = 5000;

    private BoxInsetLayout mContainerView;
    private TextView mTextView;
    private TextView mScore;

    private TextView mHighScore;
    private ImageButton reset;

    Vibrator vibrator;

    SensorManager mSensorManager;
    Sensor mAccelerometer;

    long lastUpdate;

    float last_x, last_y, last_z;
    float x, y, z;

    float firstTwist;
    int multiplier;

    boolean success = false;

    boolean lock = false;

    @IntDef({TAP, SHAKE, TWIST})
    @Retention(RetentionPolicy.SOURCE)
    @interface Gesture {};

    final static int TAP = 0;
    final static int SHAKE = 1;
    final static int TWIST = 2;

    int currenTime = START_GAP;

    int currentScore = 0;

    @Gesture
    int currentGesture;

    Handler handler;

    Runnable checkResult = new Runnable() {
        @Override
        public void run() {
            if (success) {
                Log.d("Gesture", "SUCCESS");
                mContainerView.post(new Runnable() {
                    @Override
                    public void run() {
                        mContainerView.setBackgroundColor(getResources().getColor(R.color.action));
                        mScore.setText(Integer.toString(currentScore));
                    }
                });
                success = false;

                currentScore++;

                float rand = new Random().nextFloat();

                final String text;

                if (rand < 0.33f) {
                    currentGesture = TAP;
                    text = "Tap it!";
                } else if (rand < 0.66) {
                    currentGesture = TWIST;
                    text = "Twist it!";
                } else {
                    currentGesture = SHAKE;
                    text = "Shake it!";
                }

                Log.d("Gesture", text);

                mTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(text);
                    }
                });

                currenTime = (int) (currenTime * 0.9);

                int div = currenTime / 8;

                vibrator.vibrate(new long[] {0, div, div, div, div, div,div,div,div}, -1);
                handler.postDelayed(checkResult, currenTime);
            } else {
                fail();
            }
        }
    };

    void fail() {
        lock = true;
        vibrator.cancel();
        Log.d("Gesture", "FAIL");

        SharedPreferences sharedPreferences = getSharedPreferences("vism.tapit", 0);
        int high = sharedPreferences.getInt("score", 0);

        if (currentScore > high) {
            sharedPreferences.edit().putInt("score", currentScore).apply();
            high = currentScore;
        }

        mHighScore.setText(String.format("High Score: %d", high));

        mHighScore.setVisibility(View.VISIBLE);
        reset.setVisibility(View.VISIBLE);

        reset.setEnabled(true);
        reset.setVisibility(View.VISIBLE);
        reset.setScaleX(0);
        reset.setScaleY(0);
        reset.setRotation(-270);
        reset.animate().scaleX(1).scaleY(1).rotation(0);

        mContainerView.setBackgroundColor(getResources().getColor(R.color.fail));
        if (handler != null) {
            handler.removeCallbacks(checkResult);
            handler = null;
        }
    }

    void startGame() {
        lock = false;
        currentScore = 0;
        currenTime = START_GAP;

        mHighScore.setVisibility(View.GONE);

        reset.setEnabled(false);
        reset.setScaleX(1);
        reset.setScaleY(1);
        reset.animate().scaleX(0).scaleY(0).rotation(-270).withEndAction(new Runnable() {
            @Override
            public void run() {
                reset.setVisibility(View.GONE);
            }
        });

        success = true;
        handler = new Handler();
        handler.post(checkResult);
    }

    @Override
    protected void onStop() {
        super.onStop();
        vibrator.cancel();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setAmbientEnabled();

        mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mTextView = (TextView) findViewById(R.id.text);
        mScore = (TextView) findViewById(R.id.score);

        mHighScore = (TextView) findViewById(R.id.highscore);
        reset = (ImageButton) findViewById(R.id.reset);

        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startGame();
            }
        });

        // TAP
        mContainerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("Gesture", "TAP");
                checkGesture(TAP);
            }
        });

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        startGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    private void updateDisplay() {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            // only allow one update every 100ms.
            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                x = sensorEvent.values[0];
                y = sensorEvent.values[1];
                z = sensorEvent.values[2];

                float speed = Math.abs(x+y+z - last_x - last_y - last_z) / diffTime * 10000;

                if (y < last_y + 0.1) {
                    multiplier++;
                } else {
                    firstTwist = y;
                    multiplier = 0;
                }

//                Log.d("Check", "twist amount:" + (y - firstTwist));

                // TWIST
                if (y - firstTwist < -4 && multiplier > TWIST_THRESHOLD) {
                    mTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("Gesture", "TWIST");
                            checkGesture(TWIST);
                        }
                    });
                }

                // SHAKE
                if (speed > SHAKE_THRESHOLD) {
//                    Log.d("sensor", "shake detected w/ speed: " + speed);
                    mTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d("Gesture", "SHAKE");
                            checkGesture(SHAKE);
                        }
                    });
                }
                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    void checkGesture(@Gesture int gesture) {
        if (!lock && currentGesture == gesture) {
            if (!success) {
                success = true;
                mContainerView.setBackgroundColor(getResources().getColor(R.color.success));
                vibrator.vibrate(new long[] {0, 50, 50, 50}, -1);
            }
        } else {
            success = false;
            fail();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
