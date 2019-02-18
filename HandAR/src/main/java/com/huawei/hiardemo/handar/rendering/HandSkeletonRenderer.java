package com.huawei.hiardemo.handar.rendering;
/*
 * Copyright (c) Huawei Technology Co., Ltd. All Rights Reserved.
 * Huawei Technology Proprietary and Confidential.
 */
import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import com.huawei.hiar.ARHand;
import com.huawei.hiardemo.handar.R;

import java.nio.FloatBuffer;
import java.util.Collection;

public class HandSkeletonRenderer {
    private static final String TAG = "HandSkeletonRenderer";

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
    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.


    public HandSkeletonRenderer() {
    }

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer.  Must be
     * called on the OpenGL thread, typically in
     * {@link GLSurfaceView.Renderer#onSurfaceCreated(GL10, EGLConfig)}.
     *
     * @param context Needed to access shader source.
     */
    public void createOnGlThread(Context context) {
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
                GLES20.GL_VERTEX_SHADER, R.raw.line_hand_vertex);
        int passthroughShader = ShaderHelper.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

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
    public void update() {

        //performanceHelper.printLogIfUpdate("point cloud timestamp: " + cloud.getTimestampNs(), POINT_CLOUD, Long.toString(cloud.getTimestampNs()));
        ShaderHelper.checkGLError(TAG, "before update");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mNumPoints = mPointsNum;

        if (mNumPoints * BYTES_PER_POINT > mVboSize) {
            while (mNumPoints * BYTES_PER_POINT > mVboSize) {
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }

        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT,
                mSkeletonPoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "after update");
    }

    /**
     * Renders the hand Skeleton.
     */
    public void draw(float[] projmtx) {

        ShaderHelper.checkGLError(TAG, "Before draw");

        GLES20.glUseProgram(mProgramName);
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glVertexAttribPointer(
                mPositionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionUniform, 1, false, projmtx, 0);
        GLES20.glUniform1f(mPointSizeUniform, 30.0f);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "Draw");
    }

    public void updateData(Collection<ARHand> hands,float[] projmtx) {
        Log.d(TAG, "updateData: ");
        for (ARHand hand : hands) {
            //if (hand.getTrackingState() == ARTrackingState.TRACKING) { //comment for test fake data
//            Log.i(TAG, "hand data: ");
            if(hand.getHandskeletonArray().length<= 0
                    || hand.getHandSkeletonConnection().length <= 0) {
                Log.d(TAG, "updateData: now return");
                return;
            }
            if (calcSkeletonPoints(hand)) {
                this.update();
                this.draw(projmtx);
            }
            // }
        }
    }

    public boolean calcSkeletonPoints(ARHand arHand) {

        float point[] = arHand.getHandskeletonArray();
        if (point.length <= 0) {
            return false;
        }
        mPointsNum=point.length/3;
//        for(int i=0;i<mPointsNum;i++){
//            point[3*i+2]=0.0f;
//        }
        this.mSkeletonPoints = FloatBuffer.wrap(point);
        Log.d(TAG, "ARHand HandSkeletonNumber = " + this.mPointsNum);
        return true;
    }

}
