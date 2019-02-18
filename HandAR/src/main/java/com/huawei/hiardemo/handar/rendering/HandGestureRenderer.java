package com.huawei.hiardemo.handar.rendering;
/*
 * Copyright (c) Huawei Technology Co., Ltd. All Rights Reserved.
 * Huawei Technology Proprietary and Confidential.
 */
import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.huawei.hiar.ARHand;
import com.huawei.hiar.ARTrackable;
import com.huawei.hiardemo.handar.R;

import java.nio.FloatBuffer;
import java.util.Collection;


public class HandGestureRenderer {

    private static final String TAG = HandGestureRenderer.class.getSimpleName();

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int FLOATS_PER_POINT = 3;  // X,Y,Z,confidence.
    private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
    private static final int INITIAL_BUFFER_POINTS = 150;

    private int mVbo;
    private int mVboSize;

    private int mProgramName;
    private int mPositionAttribute;
    private int mModelViewProjectionUniform;
    private int mColorUniform;
    private int mPointSizeUniform;

    private int mNumPoints = 0;
    private Context mContext;

    private OnTextInfoChangeListener mTextInfoListener;

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.

    public HandGestureRenderer() {
    }


    public void createOnGlThread(Context context) {
        mContext = context;
        ShaderHelper.checkGLError(TAG, "before create");

        int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        mVboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "buffer alloc");

        int vertexShader = ShaderHelper.loadGLShader(TAG, context,
                GLES20.GL_VERTEX_SHADER, R.raw.line_gesture_vertex);
        int passthroughShader = ShaderHelper.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        //create Program
        mProgramName = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgramName, vertexShader);
        GLES20.glAttachShader(mProgramName, passthroughShader);
        GLES20.glLinkProgram(mProgramName);
        GLES20.glUseProgram(mProgramName);

        ShaderHelper.checkGLError(TAG, "program");

        mPositionAttribute = GLES20.glGetAttribLocation(mProgramName, "a_Position");
        mColorUniform = GLES20.glGetUniformLocation(mProgramName, "u_Color");
        mModelViewProjectionUniform = GLES20.glGetUniformLocation(
                mProgramName, "u_ModelViewProjection");
        mPointSizeUniform = GLES20.glGetUniformLocation(mProgramName, "u_PointSize");

        ShaderHelper.checkGLError(TAG, "program  params");
    }

    /**
     * Updates the OpenGL buffer contents to the provided point.  Repeated calls with the same
     * point cloud will be ignored.
     */
    public void update(FloatBuffer floatBuffer, int NumPoints) {

        //performanceHelper.printLogIfUpdate("point cloud timestamp: " + cloud.getTimestampNs(), POINT_CLOUD, Long.toString(cloud.getTimestampNs()));

        ShaderHelper.checkGLError(TAG, "before update");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        // If the VBO is not large enough to fit the new point cloud, resize it.
        mNumPoints = NumPoints;
        if (mNumPoints * BYTES_PER_POINT > mVboSize) {
            while (mNumPoints * BYTES_PER_POINT > mVboSize) {
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }

        Log.d(TAG, "gesture.getGestureHandPointsNum()" + mNumPoints);
        //Log.d(TAG,"Skeleton Line Points: " + skeleton.getSkeletonLinePoints().toString());

        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * 3 * 4,
                floatBuffer);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "after update");
    }


    public void draw() {

        ShaderHelper.checkGLError(TAG, "Before draw");

        GLES20.glUseProgram(mProgramName);
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glEnableVertexAttribArray(mColorUniform);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glVertexAttribPointer(
                mPositionAttribute, 3, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColorUniform, 255.0f / 255.0f, 0.0f / 255.0f, 0.0f / 255.0f, 1.0f);
        GLES20.glUniform1f(mPointSizeUniform, 50.0f);

        GLES20.glLineWidth(18f);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glDisableVertexAttribArray(mColorUniform);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "Draw");
    }

    public void updateData(Collection<ARHand> hands,float fpsResult) {
        if (hands.size() > 0) {
            for (ARHand hand : hands) {
                StringBuilder sb = new StringBuilder();

                //
                sb.append("FPS="+fpsResult);
                sb.append("\n");
                sb.append("GestureType="+hand.getGestureType());
                sb.append("\n");
                //
                sb.append("GestureCoordinateSystem="+hand.getGestureCoordinateSystem());
                sb.append("\n");

                //
                float[] gestureOrientation = hand.getGestureOrientation();
                sb.append("gestureOrientation length:["+gestureOrientation.length+"]\n");
                for (int i = 0; i < gestureOrientation.length; i++) {
                    Log.i(TAG, "gestureOrientation:" + gestureOrientation[i]);
                    sb.append("gestureOrientation["+i+"]:["+gestureOrientation[i]+"]\n");
                }
                sb.append("\n");


                //
                int[] gestureAction = hand.getGestureAction();
                sb.append("gestureAction length:["+gestureAction.length+"]\n");
                for (int i = 0; i < gestureAction.length; i++) {
                    Log.i(TAG, "gestureAction:" + gestureAction[i]);
                    sb.append("gestureAction["+i+"]:["+gestureAction[i]+"]\n");
                }
                sb.append("\n");

                //
                float[] gestureCenter = hand.getGestureCenter();
                sb.append("gestureCenter length:["+gestureCenter.length+"]\n");
                for (int i = 0; i < gestureCenter.length; i++) {
                    Log.i(TAG, "gestureCenter:" + gestureCenter[i]);
                    sb.append("gestureCenter["+i+"]:["+gestureCenter[i]+"]\n");
                }
                sb.append("\n");

                //
                float[] gesturePoints = hand.getGestureHandBox();
                sb.append("GestureHandBox length:["+gesturePoints.length+"]\n");
                for (int i = 0; i < gesturePoints.length; i++) {
                    Log.i(TAG, "gesturePoints:" + gesturePoints[i]);
                    sb.append("gesturePoints["+i+"]:["+gesturePoints[i]+"]\n");
                }
                sb.append("\n");

                //
//                sb.append("HandskeletonType="+hand.getHandskeletonType());
//                sb.append("\n");
                //
                sb.append("Handtype="+hand.getHandtype());
                sb.append("\n");
                //
                sb.append("SkeletonCoordinateSystem="+hand.getSkeletonCoordinateSystem());
                sb.append("\n");
                //
                float[] skeletonArray = hand.getHandskeletonArray();
                sb.append("HandskeletonArray length:["+skeletonArray.length+"]\n");
                Log.i(TAG, "skeletonArray.length:" + skeletonArray.length);
                for (int i = 0; i < skeletonArray.length; i++) {
                    Log.i(TAG, "skeletonArray:" + skeletonArray[i]);
                }
                sb.append("\n");

                //
                int[] handSkeletonConnection = hand.getHandSkeletonConnection();
                sb.append("HandSkeletonConnection length:["+handSkeletonConnection.length+"]\n");
                Log.i(TAG, "handSkeletonConnection.length:" + handSkeletonConnection.length);
                for (int i = 0; i < handSkeletonConnection.length; i++) {
                    Log.i(TAG, "handSkeletonConnection:" + handSkeletonConnection[i]);
                }



                sb.append("\n-----------------------------------------------------");

                if (hand.getTrackingState() == ARTrackable.TrackingState.TRACKING)
                {
                    drawHandBox(gesturePoints);
                }
                showTextInfo(gesturePoints, sb.toString());
            }
        } else {
            showTextInfo(false);
        }
    }

    public void drawHandBox(float[] gesturePoints) {

        float[] glGesturePoints = {
                gesturePoints[0], gesturePoints[1], gesturePoints[2],
                gesturePoints[3], gesturePoints[1], gesturePoints[2],
                gesturePoints[3], gesturePoints[4], gesturePoints[5],
                gesturePoints[0], gesturePoints[4], gesturePoints[5],

        };
        //' Log.i(TAG, "gesturePointsChange" + startX + ":" + startY + ":" + endX + ":" + endY);
        int gesturePointsNum = glGesturePoints.length / 3;
        FloatBuffer mVertices = FloatBuffer.wrap(glGesturePoints);
        this.update(mVertices, gesturePointsNum);
        this.draw();
    }

    public float calcTextViewPointVertical(float x, float width, float height) {
        float screen_x = (1 + x) / 2;
        float temp_width = screen_x * width;
        return temp_width;
    }

    public float calcTextViewPointHorizontal(float y, float width, float height) {

        float screen_y = (1 - y) / 2;
        float temp_height = screen_y * height;
        return temp_height;
    }

    public void setListener(OnTextInfoChangeListener listener) {
        this.mTextInfoListener = listener;
    }

    public interface OnTextInfoChangeListener {
        public boolean textInfoChanged(String text, float positionX, float positionY);
    }

    private void showTextInfo(float[] gesturePoints, String text) {
        if (mTextInfoListener != null) {
            mTextInfoListener.textInfoChanged(text, 0, 0);
        }
    }

    private void showTextInfo(boolean isShow) {
        mTextInfoListener.textInfoChanged(null, 0, 0);
    }
}
