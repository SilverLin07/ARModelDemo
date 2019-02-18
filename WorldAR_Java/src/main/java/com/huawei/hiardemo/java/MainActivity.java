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
package com.huawei.hiardemo.java;

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

import com.huawei.hiar.ARAnchor;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARHitResult;
import com.huawei.hiar.ARLightEstimate;
import com.huawei.hiar.ARPlane;
import com.huawei.hiar.ARPoint;
import com.huawei.hiar.ARPointCloud;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable;
import com.huawei.hiar.AREnginesSelector;
import com.huawei.hiar.ARWorldTrackingConfig;
import com.huawei.hiar.exceptions.ARUnSupportedConfigurationException;
import com.huawei.hiar.exceptions.ARUnavailableClientSdkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableDeviceNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableEmuiNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableServiceApkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableServiceNotInstalledException;
import com.huawei.hiar.exceptions.ARUnavailableUserDeclinedInstallationException;
import com.huawei.hiardemo.java.rendering.BackgroundRenderer;
import com.huawei.hiardemo.java.rendering.PlaneRenderer;
import com.huawei.hiardemo.java.rendering.PointCloudRenderer;
import com.huawei.hiardemo.java.rendering.VirtualObjectRenderer;
import com.huawei.hiar.AREnginesApk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private static final String TAG = MainActivity.class.getSimpleName();
    private ARSession mSession;
    private GLSurfaceView mSurfaceView;
    private GestureDetector mGestureDetector;
    private DisplayRotationHelper mDisplayRotationHelper;

    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private VirtualObjectRenderer mVirtualObject = new VirtualObjectRenderer();
    private PlaneRenderer mPlaneRenderer = new PlaneRenderer();
    private PointCloudRenderer mPointCloud = new PointCloudRenderer();

    private final float[] mAnchorMatrix = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
    private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};
    // Anchors created from taps used for object placing with a given color.
    private static class ColoredARAnchor {
        public final ARAnchor anchor;
        public final float[] color;

        public ColoredARAnchor(ARAnchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(2);
    private ArrayList<ColoredARAnchor> mAnchors = new ArrayList<>();

    private float mScaleFactor = 0.15f;
    private boolean installRequested;
    private float updateInterval = 0.5f;
    private long lastInterval;
    private int frames = 0;
    private float fps;
    private TextView mFpsTextView;
    private TextView mSearchingTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFpsTextView =  findViewById(R.id.fpsTextView);
        mSearchingTextView = findViewById(R.id.searchingTextView);
        mSurfaceView = findViewById(R.id.surfaceview);
        mDisplayRotationHelper = new DisplayRotationHelper(this);

        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                onSingleTap(e);
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

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
        super.onResume();
        Exception exception = null;
        String message = null;
        if (null == mSession) {
            try {
                //If you do not want to switch engines, AREnginesSelector is useless.
                // You just need to use AREnginesApk.requestInstall() and the default engine
                // is Huawei AR Engine.
                AREnginesSelector.AREnginesAvaliblity enginesAvaliblity = AREnginesSelector.checkAllAvailableEngines(this);
                if ((enginesAvaliblity.ordinal() &
                        AREnginesSelector.AREnginesAvaliblity.HWAR_ENGINE_SUPPORTED.ordinal()) != 0) {

                    AREnginesSelector.setAREngine(AREnginesSelector.AREnginesType.HWAR_ENGINE);

                    switch (AREnginesApk.requestInstall(this, !installRequested)) {
                        case INSTALL_REQUESTED:
                            installRequested = true;
                            return;
                        case INSTALLED:
                            break;
                    }

                    if (!CameraPermissionHelper.hasPermission(this)) {
                        CameraPermissionHelper.requestPermission(this);
                        return;
                    }

                    mSession = new ARSession(/*context=*/this);
                } else {
                    message = "This device does not support Huawei AR Engine ";
                }
                ARConfigBase config = new ARWorldTrackingConfig(mSession);
                mSession.configure(config);

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
            }catch (Exception e) {
                message = "exception throwed";
                exception = e;
            }
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Creating sesson", exception);
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
        lastInterval = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {

        super.onPause();
        if (mSession != null) {
            mDisplayRotationHelper.onPause();
            mSurfaceView.onPause();
            mSession.pause();
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
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
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
        mQueuedSingleTaps.offer(e);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        mBackgroundRenderer.createOnGlThread(/*context=*/this);

        try {
            mVirtualObject.createOnGlThread(/*context=*/this, "AR_logo.obj", "AR_logo.png");
            mVirtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);

        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to read plane texture");
        }
        try {
            mPlaneRenderer.createOnGlThread(/*context=*/this, "trigrid.png");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read plane texture");
        }

        mPointCloud.createOnGlThread(/*context=*/this);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mDisplayRotationHelper.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        showFpsTextView(String.valueOf(FPSCalculate()));
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (null == mSession) {
            return;
        }
        mDisplayRotationHelper.updateSessionIfNeeded(mSession);

        try {
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
            ARFrame frame = mSession.update();
            ARCamera camera = frame.getCamera();

            handleTap(frame, camera);

            mBackgroundRenderer.draw(frame);

            if (camera.getTrackingState() == ARTrackable.TrackingState.PAUSED) {
                return;
            }

            float[] projmtx = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            float[] viewmtx = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
            camera.getViewMatrix(viewmtx, 0);

            ARLightEstimate le = frame.getLightEstimate();
            float lightIntensity = 1;
            if (le.getState() != ARLightEstimate.State.NOT_VALID) {
                lightIntensity = le.getPixelIntensity();
            }
            ARPointCloud arPointCloud = frame.acquirePointCloud();
            mPointCloud.update(arPointCloud);
            mPointCloud.draw(viewmtx, projmtx);
            arPointCloud.release();

            if (mSearchingTextView != null) {
                for (ARPlane plane : mSession.getAllTrackables(ARPlane.class)) {
                    if (plane.getType() != ARPlane.PlaneType.UNKNOWN_FACING &&
                            plane.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                        hideLoadingMessage();
                        break;
                    }
                }
            }
            mPlaneRenderer.drawPlanes(mSession.getAllTrackables(ARPlane.class), camera.getDisplayOrientedPose(), projmtx);

            Iterator<ColoredARAnchor> ite = mAnchors.iterator();
            while (ite.hasNext()) {
                ColoredARAnchor coloredAnchor = ite.next();
                if (coloredAnchor.anchor.getTrackingState() == ARTrackable.TrackingState.STOPPED) {
                    ite.remove();
                } else if (coloredAnchor.anchor.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                    coloredAnchor.anchor.getPose().toMatrix(mAnchorMatrix, 0);
                    mVirtualObject.updateModelMatrix(mAnchorMatrix, mScaleFactor);
                    mVirtualObject.draw(viewmtx, projmtx, lightIntensity, coloredAnchor.color);
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }


    private void hideLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSearchingTextView != null) {
                    mSearchingTextView.setVisibility(View.GONE);
                    mSearchingTextView = null;
                }
            }
        });
    }


    @Override
    protected void onDestroy() {
        if (mSession != null) {
            mSession.stop();
            mSession = null;
        }
        super.onDestroy();
    }

    private void showFpsTextView(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFpsTextView.setTextColor(Color.RED);
                mFpsTextView.setTextSize(15f);
                if (text != null) {
                    mFpsTextView.setText(text);
                } else {
                    mFpsTextView.setText("");
                }
            }
        });
    }

    float FPSCalculate() {
        ++frames;
        long timeNow = System.currentTimeMillis();
        if (((timeNow - lastInterval) / 1000) > updateInterval) {
            fps =  (frames / ((timeNow - lastInterval) / 1000.0f));
            frames = 0;
            lastInterval = timeNow;
        }
        return fps;
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(ARFrame frame, ARCamera camera) {
        MotionEvent tap = mQueuedSingleTaps.poll();
        if (tap != null && camera.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
            for (ARHitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                ARTrackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof ARPlane
                        && ((ARPlane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof ARPoint
                        && ((ARPoint) trackable).getOrientationMode() == ARPoint.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (mAnchors.size() >= UtilsCommon.MAX_TRACKING_ANCHOR_NUM) {
                        mAnchors.get(0).anchor.detach();
                        mAnchors.remove(0);
                    }

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    float[] objColor;
                    if (trackable instanceof ARPoint) {
                        objColor = new float[] {66.0f, 133.0f, 244.0f, 255.0f};
                    } else if (trackable instanceof ARPlane) {
                        objColor = new float[] {139.0f, 195.0f, 74.0f, 255.0f};
                    } else {
                        objColor = DEFAULT_COLOR;
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    mAnchors.add(new ColoredARAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }
        }
    }
}
