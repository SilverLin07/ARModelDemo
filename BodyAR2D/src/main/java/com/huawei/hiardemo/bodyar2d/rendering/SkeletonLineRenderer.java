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
import java.util.Collection;

/**
 * Created by x00436406 on 2018/4/10.
 */

public class SkeletonLineRenderer {
    private static final String TAG = "SkeletonLineRenderer";

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
    private int mPointsLineNum = 0;
    private FloatBuffer mLinePoints;

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.
    private ARBody mLastBody = null;

    public SkeletonLineRenderer() {
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

    /**
     * Updates the OpenGL buffer contents to the provided point.  Repeated calls with the same
     * point cloud will be ignored.
     */
    public void update(ARBody body) {//cwx556793
        if (mLastBody == body) {
            // Redundant call.
            //return;
        }

        ShaderUtil.checkGLError(TAG, "before update");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mLastBody = body;

        // If the VBO is not large enough to fit the new point cloud, resize it.
        //mNumPoints = body.getSkeletonLinePointsNum();//5;
        mNumPoints = mPointsLineNum;//5;

        if (mNumPoints * BYTES_PER_POINT > mVboSize) {
            while (mNumPoints * BYTES_PER_POINT > mVboSize) {
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }

        Log.d(TAG, "skeleton.getSkeletonLinePointsNum()" + mNumPoints);
        Log.d(TAG, "Skeleton Line Points: " + mLinePoints.toString());

        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT, mLinePoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "after update");
    }

    /**
     * Renders the body Skeleton.
     */
    public void draw() {

        ShaderUtil.checkGLError(TAG, "Before draw");

        GLES20.glUseProgram(mProgramName);
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glEnableVertexAttribArray(mColorUniform);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glLineWidth(18.0f);
        GLES20.glVertexAttribPointer(
                mPositionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColorUniform, 255.0f / 255.0f, 0.0f / 255.0f, 0.0f / 255.0f, 1.0f);
        GLES20.glUniform1f(mPointSizeUniform, 100.0f);

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glDisableVertexAttribArray(mColorUniform);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "Draw");
    }

    public void updateData(Collection<ARBody> bodies, float width, float height) {
        Log.i(TAG, "bodies size:" + bodies.size());
        for (ARBody body : bodies) {
            if (body.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                Log.i(TAG, "bodies size:" + body.toString());
                calcSkeletonPoints(body, width, height);
                this.update(body);
                this.draw();
            } else {
                Log.i(TAG, "TrackingState != TRACKING");
            }

        }
    }

    public boolean calcSkeletonPoints(ARBody arBody, float width, float height) {
        this.mPointsLineNum = 0;
        int connec[] = arBody.getBodySkeletonConnection();
        float coor[] = arBody.getSkeletonPoint2D();
        float linePoint[] = new float[connec.length * 3 * 2];
        int isExist[] = arBody.getSkeletonPointIsExist2D ();
        for (int j = 0; j < connec.length; j += 2) {
            if (isExist[connec[j]] != 0 && isExist[connec[j + 1]] != 0) {
                linePoint[this.mPointsLineNum * 3] = coor[3 * connec[j]];
                linePoint[this.mPointsLineNum * 3 + 1] = coor[3 * connec[j] + 1];
                linePoint[this.mPointsLineNum * 3 + 2] = coor[3 * connec[j] + 2];

                linePoint[this.mPointsLineNum * 3 + 3] = coor[3 * connec[j + 1]];
                linePoint[this.mPointsLineNum * 3 + 4] = coor[3 * connec[j + 1] + 1];
                linePoint[this.mPointsLineNum * 3 + 5] = coor[3 * connec[j + 1] + 2];
                this.mPointsLineNum += 2;
            }
        }
        this.mLinePoints = FloatBuffer.wrap(linePoint);
        return true;
    }

}
