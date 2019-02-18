package com.huawei.hiardemo.bodyar2d.rendering;
/*
 * Copyright (c) Huawei Technology Co., Ltd. All Rights Reserved.
 * Huawei Technology Proprietary and Confidential.
 */
import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.huawei.hiar.ARBody;
import com.huawei.hiar.ARTrackable;
import com.huawei.hiardemo.bodyar2d.R;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by x00436406 on 2018/4/10.
 */

public class BodySkeletonRenderer {
    private static final String TAG = "BodySkeletonRenderer";

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

    private int mPointsNum = 0;
    private FloatBuffer mSkeletonPoints;

    private float vertices[] = {
            0.0f, 0.0f, 0.0f,
            0.5f, 0.5f, 0.0f,
            -0.5f, 0.5f, 0.0f,
            0.5f, -0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
    };

    private ARBody mLastBody = null;
    private OnTextInfoChangeListener mTextInfoListener;
    public BodySkeletonRenderer() {
    }


    public void createOnGlThread(Context context) {
        ShaderUtil.checkGLError(TAG, "before create");

        int buffers[] = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        mVboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "buffer alloc");

        int vertexShader = ShaderUtil.loadGLShader(TAG, context,
                GLES20.GL_VERTEX_SHADER, R.raw.line_body_vertex);
        int passthroughShader = ShaderUtil.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        mProgramName = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgramName, vertexShader);
        GLES20.glAttachShader(mProgramName, passthroughShader);
        GLES20.glLinkProgram(mProgramName);
        GLES20.glUseProgram(mProgramName);

        ShaderUtil.checkGLError(TAG, "program");

        mPositionAttribute = GLES20.glGetAttribLocation(mProgramName, "a_Position");
        mColorUniform = GLES20.glGetUniformLocation(mProgramName, "u_Color");
        mModelViewProjectionUniform = GLES20.glGetUniformLocation(
                mProgramName, "u_ModelViewProjection");
        mPointSizeUniform = GLES20.glGetUniformLocation(mProgramName, "u_PointSize");

        ShaderUtil.checkGLError(TAG, "program  params");
    }

    private void update(ARBody body) {


        //performanceHelper.printLogIfUpdate("point cloud timestamp: " + cloud.getTimestampNs(), POINT_CLOUD, Long.toString(cloud.getTimestampNs()));

        ShaderUtil.checkGLError(TAG, "before update");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        //mLastBody = body;

        // If the VBO is not large enough to fit the new point cloud, resize it.
        //mNumPoints = body.getBodySkeletonPointsNum();//5;
        mNumPoints = mPointsNum;//5;

        //mNumPoints = 5;
        if (mNumPoints * BYTES_PER_POINT > mVboSize) {
            while (mNumPoints * BYTES_PER_POINT > mVboSize) {
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        //GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT,
        //        FloatBuffer.wrap(vertices));
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT, mSkeletonPoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "after update");
    }

    /**
     * Renders the body Skeleton.
     */
    private void draw() {

        ShaderUtil.checkGLError(TAG, "Before draw");

        GLES20.glUseProgram(mProgramName);
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glVertexAttribPointer(
                mPositionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f);
        GLES20.glUniform1f(mPointSizeUniform, 30.0f);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "Draw");
    }

    public void updateData(Collection<ARBody> bodies, float width, float height,float fpsResult) {
        Log.i(TAG, "bodies size:" + bodies.size());
        for (ARBody body : bodies) {
            if (body.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                Log.i(TAG, "bodies size:" + body.toString());
                calcSkeletonPoints(body, width, height);
                this.update(body);
                this.draw();
                StringBuilder sb = new StringBuilder();
                sb.append("FPS="+fpsResult);
                sb.append("\n");
                showTextInfo(sb.toString());
            } else {
                Log.i(TAG, "TrackingState != TRACKING");
            }
        }
    }

    private boolean calcSkeletonPoints(ARBody arBody, float width, float height) {
        int index = 0;
        int isExist[] = arBody.getSkeletonPointIsExist2D ();
        this.mPointsNum = isExist.length ;
        int validPonitNum = 0;
        float point[] = new float[this.mPointsNum * 3];
        float coor[] = arBody.getSkeletonPoint2D();
        Log.d(TAG, "calcSkeletonPoints: isExist="+(isExist.length)+";coor.length="+coor.length);
        for (int i = 0; i < isExist.length ; i++) {
            if (isExist[i] != 0) {
                point[index++] = coor[3*i];
                point[index++] = coor[3*i + 1];
                point[index++] = coor[3*i + 2];
                validPonitNum ++;
            }
        }
        Log.i(" bodies", "bodies mSkeletonPoints:" + mPointsNum + "》》     " + Arrays.toString(point));
        this.mSkeletonPoints = FloatBuffer.wrap(point);

        this.mPointsNum = validPonitNum;
        Log.d(TAG, "ARBody BodySkeletonNumber = " + this.mPointsNum);
        if (this.mPointsNum > 0) {
            Log.d(TAG, "ARBody points_Change[" + point[0] + "," + point[1] + "," + point[2] + "]");
        }
        return true;
    }

    public void setListener(OnTextInfoChangeListener listener) {
        this.mTextInfoListener = listener;
    }

    public interface OnTextInfoChangeListener {
        public boolean textInfoChanged(String text, float positionX, float positionY);
    }

    private void showTextInfo(String text) {

        if (mTextInfoListener != null) {
            mTextInfoListener.textInfoChanged(text, 0, 0);
        }
    }


}
