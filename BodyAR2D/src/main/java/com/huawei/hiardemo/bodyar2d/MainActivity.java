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
package com.huawei.hiardemo.bodyar2d;

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import com.huawei.hiar.ARBody;
import com.huawei.hiar.ARBodyTrackingConfig;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.AREnginesSelector;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable;
import com.huawei.hiar.exceptions.ARUnSupportedConfigurationException;
import com.huawei.hiar.exceptions.ARUnavailableClientSdkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableDeviceNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableEmuiNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableServiceApkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableServiceNotInstalledException;
import com.huawei.hiar.exceptions.ARUnavailableUserDeclinedInstallationException;
import com.huawei.hiardemo.bodyar2d.rendering.BackgroundRenderer;
import com.huawei.hiardemo.bodyar2d.rendering.BodySkeletonRenderer;
import com.huawei.hiardemo.bodyar2d.rendering.SkeletonLineRenderer;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = MainActivity.class.getSimpleName();
    private ARSession mSession;
    private GLSurfaceView mSurfaceView;
    private GestureDetector mGestureDetector;
    private Snackbar mLoadingMessageSnackbar = null;
    private DisplayRotationHelper mDisplayRotationHelper;

    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();

    private BodySkeletonRenderer mBodySkeleton = new BodySkeletonRenderer();
    private SkeletonLineRenderer mSkeletonConnection = new SkeletonLineRenderer();

    // Tap handling and UI.
    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(2);

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
        setContentView(com.huawei.hiardemo.bodyar2d.R.layout.activity_main);

        cameraPoseTextView = (TextView) findViewById(com.huawei.hiardemo.bodyar2d.R.id.textView);
        mSurfaceView = (GLSurfaceView) findViewById(com.huawei.hiardemo.bodyar2d.R.id.surfaceview);
        mDisplayRotationHelper = new DisplayRotationHelper(this);
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                //onSingleTap(e);
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

//        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                Log.d(TAG, "onTouched!");
//                return mGestureDetector.onTouchEvent(event);
//            }
//        });

        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        installRequested = false;

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        Exception exception = null;
        String message = null;

        if (mSession == null) {
            try {
                AREnginesSelector.AREnginesAvaliblity enginesAvaliblity = AREnginesSelector
                        .checkAllAvailableEngines(this);
                if ((enginesAvaliblity.ordinal() &
                        AREnginesSelector.AREnginesAvaliblity.HWAR_ENGINE_SUPPORTED.ordinal()) !=
                        0) {

                    AREnginesSelector.setAREngine(AREnginesSelector.AREnginesType.HWAR_ENGINE);

                    switch (AREnginesApk.requestInstall(this, !installRequested)) {
                        case INSTALL_REQUESTED:
                            installRequested = true;
                            return;
                        case INSTALLED:
                            break;
                    }
                    Log.d(TAG, "onResume: AREnginesSelector.getCreatedEngine()=" +
                            AREnginesSelector.getCreatedEngine());
                    if (!CameraPermissionHelper.hasPermission(this)) {
                        CameraPermissionHelper.requestPermission(this);
                        return;
                    }
                    mSession = new ARSession(this);

                    ARBodyTrackingConfig config = new ARBodyTrackingConfig(mSession);
                    Log.d(TAG, "onResume: config=" + config.toString());

                    mSession.configure(config);
                } else {
                    message = "This device does not support Huawei AR Engine ";
                }

            } catch (ARUnavailableServiceNotInstalledException e) {
                message = "Please install HuaweiARService.apk";
                exception = e;
            } catch (ARUnavailableServiceApkTooOldException e) {
                message = "Please update HuaweiARService.apk";
                exception = e;
            } catch (ARUnavailableClientSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (ARUnavailableDeviceNotCompatibleException e) {
                message = "This device does not support Huawei AR Engine ";
                exception = e;
            } catch (ARUnavailableEmuiNotCompatibleException e) {
                message = "Please update EMUI version";
                exception = e;
            } catch (ARUnavailableUserDeclinedInstallationException e) {
                message = "Please agree to install!";
                exception = e;
            } catch (ARUnSupportedConfigurationException e) {
                message = "The configuration is not supported by the device!";
                exception = e;
            } catch (Exception e) {
                message = "exception throwed";
                exception = e;
            }

            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Creating session", exception);
                if (mSession != null) {
                    mSession.stop();
                    mSession = null;
                }
                return;
            }

        }
        mSession.resume();
        mSurfaceView.onResume();
        mDisplayRotationHelper.onResume();
        lastInterval = (System.currentTimeMillis());

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
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onSingleTap(MotionEvent e) {
        // Queue tap if there is space. Tap is lost if queue is full.
        Log.d(TAG, "queue a motion event into mQueuedSingleTaps!");
        mQueuedSingleTaps.offer(e);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(/*context=*/this);
        mBodySkeleton.createOnGlThread(this);
        mSkeletonConnection.createOnGlThread(this);
        mBodySkeleton.setListener(new BodySkeletonRenderer.OnTextInfoChangeListener() {
            @Override
            public boolean textInfoChanged(String text, float positionX, float positionY) {
                showBodyTypeTextView(text, positionX, positionY);
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
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);
        try {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
            ARFrame frame = mSession.update();
            ARCamera camera = frame.getCamera();
            float fpsResult = FPSCalculate();

            mBackgroundRenderer.draw(frame);
            Collection<ARBody> bodies = mSession.getAllTrackables(ARBody.class);
            Log.i(TAG, "bodies size:" + bodies.size());
            mBodySkeleton.updateData(bodies, width, height, fpsResult);
            mSkeletonConnection.updateData(bodies, width, height);

            // if not tracking, don't draw 3d objects
            if (camera.getTrackingState() != ARTrackable.TrackingState.TRACKING) {
                return;
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }


    private void showBodyTypeTextView(final String text, final float positionX, final float
            positionY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cameraPoseTextView.setTextColor(Color.RED);
                cameraPoseTextView.setTextSize(15f);
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
//        Log.d(TAG, "FPSCalculate: frames=" + frames + ";timeNow=" + timeNow + ";lastInterval="
// + lastInterval
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
