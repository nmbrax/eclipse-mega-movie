/**
 * This activity is not part of the main app, but is used
 * for experimenting with different camera settings
 */

package ideum.com.megamovie.Java.PatagoniaTest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import ideum.com.megamovie.Java.CameraControl.CameraPreviewAndCaptureFragment;
import ideum.com.megamovie.Java.LocationAndTiming.GPSFragment;
import ideum.com.megamovie.R;

public class CameraTestActivity extends AppCompatActivity {

    private CameraPreviewAndCaptureFragment mCameraFragment;
    private TextView mSensitivityTextView;
    private TextView mFocusDistanceTextView;
    private TextView mDurationTextView;

    private static final int SENSITIVITY_INCREMEMENT = 30;
    private static final long DURATION_INCREMENT = 1;
    private static final float FOCUS_DISTANCE_INCREMENT = 0.1f;
    private static final boolean SHOULD_USE_DELAY = false;
    private static final long CAPTURE_DELAY_MILLS = 4000;

    private static final int REQUEST_PERMISSIONS = 0;

    private static final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (!hasAllPermissionsGranted()) {
            requestCameraPermissions();
            return;
        }


        setContentView(R.layout.activity_camera_test);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mCameraFragment = (CameraPreviewAndCaptureFragment) getFragmentManager().findFragmentById(R.id.camera_test_fragment);
        mSensitivityTextView = (TextView) findViewById(R.id.sensitivity_text_view);
        mFocusDistanceTextView = (TextView) findViewById(R.id.focusDistance_text_view);
        mDurationTextView = (TextView) findViewById(R.id.duration_text_view);
        updateTextViews();

        // gps just used for image metadata in practice mode
        GPSFragment mGPSFragment = new GPSFragment();
        getFragmentManager().beginTransaction().add(
                android.R.id.content, mGPSFragment).commit();
        mCameraFragment.setLocationProvider(mGPSFragment);

    }
    private void updateTextViews(){
        mSensitivityTextView.setText("Sensitivity: " + String.valueOf(mCameraFragment.mSensorSensitivity));
        mFocusDistanceTextView.setText("Focus: " + String.valueOf(mCameraFragment.mFocusDistance));
        mDurationTextView.setText("Duration: " + String.valueOf(mCameraFragment.mDuration/1000000));
    }

    public void increaseSensitivity(View view) {
        mCameraFragment.incrementSensitivity(SENSITIVITY_INCREMEMENT);
        updateTextViews();
    }

    public void decreaseSensitivity(View view) {
        mCameraFragment.decrementSensitivity(SENSITIVITY_INCREMEMENT);
        updateTextViews();
    }

    public void increaseFocusDistance(View view) {
        mCameraFragment.incrementFocusDistance(FOCUS_DISTANCE_INCREMENT);
        updateTextViews();
    }
    public void decreaseFocusDistance(View view) {
        mCameraFragment.decrementFocusDistance(FOCUS_DISTANCE_INCREMENT);
        updateTextViews();
    }
    public void increaseDuration(View view) {
        mCameraFragment.incrementDuration(DURATION_INCREMENT);
        updateTextViews();
    }

    public void decreaseDuration(View view) {
        mCameraFragment.decrementDuration(DURATION_INCREMENT);
        updateTextViews();
    }
    public void captureImage(View view) {
        if (SHOULD_USE_DELAY) {
            new CountDownTimer(CAPTURE_DELAY_MILLS, 1000) {

                public void onTick(long millisUntilFinished) {
                }

                public void onFinish() {
                    takePhoto();
                }
            }.start();
        } else {
            takePhoto();
        }
    }
    private void takePhoto() {
        Toast.makeText(getApplicationContext(),"Photo taken!",Toast.LENGTH_SHORT).show();
        mCameraFragment.captureRawImage();
    }

    public void loadMapActivity(View view) {
        startActivity(new Intent(this, MapActivity.class));
    }

    private void requestCameraPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS);
    }

    private boolean hasAllPermissionsGranted() {
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}




