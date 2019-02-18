/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.huawei.hiardemo.handar;

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.AREnginesSelector;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARHand;
import com.huawei.hiar.ARHandTrackingConfig;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable;
import com.huawei.hiar.exceptions.ARUnSupportedConfigurationException;
import com.huawei.hiardemo.handar.rendering.BackgroundRenderer;
import com.huawei.hiardemo.handar.rendering.HandGestureRenderer;
import com.huawei.hiardemo.handar.rendering.HandSkeletonRenderer;
import com.huawei.hiardemo.handar.rendering.HandSkeletonLineRenderer;

import java.util.Collection;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = MainActivity.class.getSimpleName();
    private ARSession mSession;
    private GLSurfaceView mSurfaceView;
    private DisplayRotationHelper mDisplayRotationHelper;

    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private HandGestureRenderer mHandGestureRenderer = new HandGestureRenderer();
    private HandSkeletonRenderer mHandSkeletonRenderer = new HandSkeletonRenderer();
    private HandSkeletonLineRenderer mHandSkeletonLineRenderer = new HandSkeletonLineRenderer();

    // Tap handling and UI.
    private TextView cameraPoseTextView;
    private float width;
    private float height;
    private boolean installRequested;
    private float updateInterval = 0.5f;
    private long lastInterval;
    private int frames = 0;
    private float fps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraPoseTextView = (TextView) findViewById(R.id.textView);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
        mDisplayRotationHelper = new DisplayRotationHelper(this);

        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        installRequested = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if (mSession == null) {
            try {
                AREnginesSelector.AREnginesAvaliblity enginesAvaliblity = AREnginesSelector.checkAllAvailableEngines(this);
                if ((enginesAvaliblity.ordinal() &
                        AREnginesSelector.AREnginesAvaliblity.HWAR_ENGINE_SUPPORTED.ordinal()) != 0) {

                    AREnginesSelector.setAREngine(AREnginesSelector.AREnginesType.HWAR_ENGINE);
                }else{
                    Toast.makeText(this,
                            "This device does not support Huawei AR Engine !",
                            Toast.LENGTH_LONG).show();
                    Log.e(TAG, "This device does not support Huawei AR Engine ");
                    return;
                }

                switch (AREnginesApk.requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }
                Log.d(TAG, "onResume: AREnginesSelector.getCreatedEngine()=" + AREnginesSelector.getCreatedEngine());
                if (!CameraPermissionHelper.hasPermission(this)) {
                    CameraPermissionHelper.requestPermission(this);
                    return;
                }
                mSession = new ARSession(this);
            } catch (Exception e) {
                Toast.makeText(this,
                        "Exception when create session:unknown error!",
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Exception when create session", e);
                if (mSession != null) {
                    mSession.stop();
                    mSession = null;
                }
                return;
            }

            ARHandTrackingConfig config = new ARHandTrackingConfig(mSession);
            config.setCameraLensFacing(ARConfigBase.CameraLensFacing.FRONT);
            config.setPowerMode(ARConfigBase.PowerMode.ULTRA_POWER_SAVING);

            long Item = ARConfigBase.ENABLE_DEPTH;
            config.setEnableItem(Item);
            mSession.configure(config);
            Log.d(TAG, "Item = " + config.getEnableItem());
        }
        mSession.resume();
        mSurfaceView.onResume();
        mDisplayRotationHelper.onResume();
        lastInterval = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (mSession != null) {
            mDisplayRotationHelper.onPause();
            mSurfaceView.onPause();
            mSession.pause();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (mSession != null) {
            mSession.stop();
            mSession = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasPermission(this)) {
            Toast.makeText(this,
                    "This application needs camera permission.", Toast.LENGTH_LONG).show();

            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.d(TAG, "onWindowFocusChanged");
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        }
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        mBackgroundRenderer.createOnGlThread(/*context=*/this);
        mHandSkeletonRenderer.createOnGlThread(this);
        mHandSkeletonLineRenderer.createOnGlThread(this);
        mHandGestureRenderer.createOnGlThread(this);
        mHandGestureRenderer.setListener(new HandGestureRenderer.OnTextInfoChangeListener() {
            @Override
            public boolean textInfoChanged(String text, float positionX, float positionY) {
                showHandTypeTextView(text, positionX, positionY);
                return true;
            }
        });
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        this.width = width;
        this.height = height;
        Log.d(TAG, "onSurfaceChanged! [" + width + ", " + height + "]");
        GLES20.glViewport(0, 0, width, height);
        mDisplayRotationHelper.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        Log.d(TAG, "onDrawFrame");

        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);

        try {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
            ARFrame frame = mSession.update();
            ARCamera camera = frame.getCamera();
            float  fpsResult=FPSCalculate();
            Log.d(TAG, "onDrawFrame: fpsResult="+fpsResult);

            mBackgroundRenderer.draw(frame);
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            Log.d(TAG, "onDrawFrame: camera pos " + camera.getPose().toString());
            Collection<ARHand> hands = mSession.getAllTrackables(ARHand.class);
            Log.i(TAG, "hand gestures size:" + hands.size());
            mHandGestureRenderer.updateData(hands,fpsResult);
            mHandSkeletonLineRenderer.updateData(hands,projmtx);
            mHandSkeletonRenderer.updateData(hands,projmtx);
            Log.i(TAG, "camera.getTrackingState():" + camera.getTrackingState());
            if (camera.getTrackingState() == ARTrackable.TrackingState.PAUSED) {
                Log.i(TAG, "camera.getTrackingState():" + camera.getTrackingState());
                return;
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    private void showHandTypeTextView(final String text, final float positionX, final float positionY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cameraPoseTextView.setTextColor(Color.WHITE);
                cameraPoseTextView.setTextSize(10f);
                if (text != null) {
                    cameraPoseTextView.setText(text);
                    cameraPoseTextView.setPadding((int) positionX, (int) positionY, 0, 0);
                } else {
                    cameraPoseTextView.setText("");
                }
            }
        });
    }

    float FPSCalculate() {
        ++frames;
        long timeNow = System.currentTimeMillis();
//        Log.d(TAG, "FPSCalculate: frames=" + frames + ";timeNow=" + timeNow + ";lastInterval=" + lastInterval
//                + ";lastInterval + updateInterval=" + (timeNow - lastInterval) / 1000.0f);
        if (((timeNow - lastInterval) / 1000) > updateInterval) {
            fps = (float) (frames / ((timeNow - lastInterval) / 1000.0f));
            frames = 0;
            lastInterval = timeNow;
            Log.d(TAG, "FPSCalculate: fps=" + fps);
        }
        return fps;
    }
}
