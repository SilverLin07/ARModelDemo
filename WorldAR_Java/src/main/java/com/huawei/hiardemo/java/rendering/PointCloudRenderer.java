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
package com.huawei.hiardemo.java.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.huawei.hiar.ARPointCloud;
import com.huawei.hiardemo.java.R;
import com.huawei.hiardemo.java.UtilsCommon;

public class PointCloudRenderer {
    private static final String TAG = ARPointCloud.class.getSimpleName();

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int FLOATS_PER_POINT = 4;  // X,Y,Z,confidence.
    private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
    private static final int INITIAL_BUFFER_POINTS = 1000;

    private int mVbo;
    private int mVboSize;

    private int mProgramName;
    private int mPositionAttribute;
    private int mModelViewProjectionUniform;
    private int mColorUniform;
    private int mPointSizeUniform;

    private int mNumPoints = 0;

    private ARPointCloud mPointCloud = null;

    public PointCloudRenderer() {
    }


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
                GLES20.GL_VERTEX_SHADER, R.raw.pointcloud_vertex);
        int pointCloudShader = ShaderHelper.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.pointcloud_fragment);

        mProgramName = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgramName, vertexShader);
        GLES20.glAttachShader(mProgramName, pointCloudShader);
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

    public void update(ARPointCloud cloud) {
        if (mPointCloud == cloud) {
            return;
        }

        ShaderHelper.checkGLError(TAG, "before update");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mPointCloud = cloud;

        mNumPoints = mPointCloud.getPoints().remaining() / FLOATS_PER_POINT;
        if (mNumPoints * BYTES_PER_POINT > mVboSize) {
            while (mNumPoints * BYTES_PER_POINT > mVboSize) {
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT,
                mPointCloud.getPoints());
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "after update");
    }

    public void draw( float[] cameraView, float[] cameraPerspective) {
        ShaderHelper.checkGLError(TAG, "before draw");

        float[] modelViewProjection = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

        GLES20.glUseProgram(mProgramName);
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glVertexAttribPointer(
                mPositionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColorUniform, 255.0f / 255.0f, 241.0f / 255.0f, 67.0f / 255.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionUniform, 1, false, modelViewProjection, 0);
        GLES20.glUniform1f(mPointSizeUniform, 10.0f);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderHelper.checkGLError(TAG, "after draw");
    }
}
