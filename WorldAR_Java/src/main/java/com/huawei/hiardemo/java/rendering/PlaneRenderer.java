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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.huawei.hiar.ARPlane;
import com.huawei.hiar.ARPose;
import com.huawei.hiar.ARTrackable;
import com.huawei.hiardemo.java.R;
import com.huawei.hiardemo.java.UtilsCommon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector2d;

public class PlaneRenderer {
    private static final boolean DRAW_CONCAVE = true;
    private static final String TAG = PlaneRenderer.class.getSimpleName();

    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int BYTES_PER_SHORT = Short.SIZE / 8;
    private static final int COORDS_PER_VERTEX = 3;

    private static final int VERTS_PER_BOUNDARY_VERT = 2;
    private static final int INDICES_PER_BOUNDARY_VERT = 3;
    private static final int INITIAL_BUFFER_BOUNDARY_VERTS = 64;

    private static final int INITIAL_VERTEX_BUFFER_SIZE_BYTES =
            BYTES_PER_FLOAT * COORDS_PER_VERTEX * VERTS_PER_BOUNDARY_VERT *
                    INITIAL_BUFFER_BOUNDARY_VERTS;

    private static final int INITIAL_INDEX_BUFFER_SIZE_BYTES =
            BYTES_PER_SHORT * INDICES_PER_BOUNDARY_VERT * INDICES_PER_BOUNDARY_VERT *
                    INITIAL_BUFFER_BOUNDARY_VERTS;

    private static final float FADE_RADIUS_M = 0.05f;
    private static final float DOTS_PER_METER = 10.0f;
    private static final float EQUILATERAL_TRIANGLE_SCALE = (float) (1 / Math.sqrt(3));

    private static final float[] GRID_CONTROL = {0.2f, 0.4f, 2.0f, 1.5f};

    private int mPlaneProgram;
    private int[] mTextures = new int[1];

    private int mPlaneXZPositionAlphaAttribute;

    private int mPlaneModelUniform;
    private int mPlaneModelViewProjectionUniform;
    private int mTextureUniform;
    private int mLineColorUniform;
    private int mDotColorUniform;
    private int mGridControlUniform;
    private int mPlaneUvMatrixUniform;
    private int mPlaneFacingUniform;

    private FloatBuffer mVertexBuffer = ByteBuffer.allocateDirect(INITIAL_VERTEX_BUFFER_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private ShortBuffer mIndexBuffer = ByteBuffer.allocateDirect(INITIAL_INDEX_BUFFER_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asShortBuffer();

    private float[] mModelMatrix = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
    private float[] mModelViewMatrix = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
    private float[] mModelViewProjectionMatrix = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
    private float[] mPlaneColor = new float[4];
    private float[] mPlaneAngleUvMatrix = new float[4];

    private Map<ARPlane, Integer> mPlaneIndexMap = new HashMap<>();

    public PlaneRenderer() {
    }

    public void createOnGlThread(Context context, String gridDistanceTextureName)
            throws IOException {
        int vertexShader = ShaderHelper.loadGLShader(TAG, context,
                GLES20.GL_VERTEX_SHADER, R.raw.plane_vertex);
        int passthroughShader = ShaderHelper.loadGLShader(TAG, context,
                GLES20.GL_FRAGMENT_SHADER, R.raw.plane_fragment);

        mPlaneProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mPlaneProgram, vertexShader);
        GLES20.glAttachShader(mPlaneProgram, passthroughShader);
        GLES20.glLinkProgram(mPlaneProgram);
        GLES20.glUseProgram(mPlaneProgram);

        ShaderHelper.checkGLError(TAG, "Program creation");

        // Read the texture.
        Bitmap textureBitmap = BitmapFactory.decodeStream(
                context.getAssets().open(gridDistanceTextureName));

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(mTextures.length, mTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        ShaderHelper.checkGLError(TAG, "texture loading");

        mPlaneXZPositionAlphaAttribute = GLES20.glGetAttribLocation(mPlaneProgram,
                "a_XZPositionAlpha");

        mPlaneModelUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_Model");
        mPlaneModelViewProjectionUniform =
                GLES20.glGetUniformLocation(mPlaneProgram, "u_ModelViewProjection");
        mTextureUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_Texture");
        mLineColorUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_lineColor");
        mDotColorUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_dotColor");
        mGridControlUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_gridControl");
        mPlaneUvMatrixUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_PlaneUvMatrix");
        mPlaneFacingUniform = GLES20.glGetUniformLocation(mPlaneProgram, "u_PlaneFacing");

        ShaderHelper.checkGLError(TAG, "program parameters");
    }

    private void updatePlaneParameters(float[] planeMatrix, float extentX, float extentZ,
                                       FloatBuffer boundary) {
        System.arraycopy(planeMatrix, 0, mModelMatrix, 0, UtilsCommon.MAX_TRACKING_ANCHOR_NUM);
        if (boundary == null) {
            mVertexBuffer.limit(0);
            mIndexBuffer.limit(0);
            return;
        }

        boundary.rewind();
        int boundaryVertices = boundary.limit() / 2;
        int numVertices;
        int numIndices;

        numVertices = boundaryVertices * VERTS_PER_BOUNDARY_VERT;
        numIndices = boundaryVertices * INDICES_PER_BOUNDARY_VERT;


        if (mVertexBuffer.capacity() < numVertices * COORDS_PER_VERTEX) {
            int size = mVertexBuffer.capacity();
            while (size < numVertices * COORDS_PER_VERTEX) {
                size *= 2;
            }
            mVertexBuffer = ByteBuffer.allocateDirect(BYTES_PER_FLOAT * size)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        mVertexBuffer.rewind();


        if (mIndexBuffer.capacity() < numIndices) {
            int size = mIndexBuffer.capacity();
            while (size < numIndices) {
                size *= 2;
            }
            mIndexBuffer = ByteBuffer.allocateDirect(BYTES_PER_SHORT * size)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
        }
        mIndexBuffer.rewind();

        if (DRAW_CONCAVE) {
            mVertexBuffer.limit(numVertices * COORDS_PER_VERTEX / 2);
            if (boundaryVertices >= 2) {
                mIndexBuffer.limit((boundaryVertices - 2) * 3);
            }
        } else {
            mVertexBuffer.limit(numVertices * COORDS_PER_VERTEX);
            mIndexBuffer.limit(numIndices);
        }

        Vector2d[] vertices2D = new Vector2d[boundaryVertices];
        for (int i = 0; i < vertices2D.length; ++i) {
            vertices2D[i] = new Vector2d(boundary.get(i * 2), boundary.get(i * 2 + 1));
        }

        Triangulator tr = new Triangulator(vertices2D);
        int[] indices = tr.Triangulate();

        float xScale = Math.max((extentX - 2 * FADE_RADIUS_M) / extentX, 0.0f);
        float zScale = Math.max((extentZ - 2 * FADE_RADIUS_M) / extentZ, 0.0f);

        for (int i = 0; i < vertices2D.length; ++i) {
            float x = 0;
            float z = 0;
            if (DRAW_CONCAVE) {
                x = (float)vertices2D[i].x;
                z = (float)vertices2D[i].y;

                mVertexBuffer.put(x);
                mVertexBuffer.put(z);
                mVertexBuffer.put(1.0f);
            } else {
                x = boundary.get();
                z = boundary.get();
                mVertexBuffer.put(x);
                mVertexBuffer.put(z);
                mVertexBuffer.put(0.0f);
                mVertexBuffer.put(x * xScale);
                mVertexBuffer.put(z * zScale);
                mVertexBuffer.put(1.0f);
            }

        }

        if (!DRAW_CONCAVE) {
            mIndexBuffer.put((short) ((boundaryVertices - 1) * 2));
            for (int i = 0; i < boundaryVertices; ++i) {
                mIndexBuffer.put((short) (i * 2));
                mIndexBuffer.put((short) (i * 2 + 1));
            }
            mIndexBuffer.put((short) 1);
        }

        if (DRAW_CONCAVE) {
            for (int i = 0; i < indices.length; ++i) {
                mIndexBuffer.put((short) indices[i]);
            }
        } else {
            for (int i = 1; i < boundaryVertices / 2; ++i) {
                mIndexBuffer.put((short) ((boundaryVertices - 1 - i) * 2 + 1));
                mIndexBuffer.put((short) (i * 2 + 1));
            }
            if (boundaryVertices % 2 != 0) {
                mIndexBuffer.put((short) ((boundaryVertices / 2) * 2 + 1));
            }
        }

    }

    private void draw(float[] cameraView, float[] cameraPerspective) {
        ShaderHelper.checkGLError(TAG, "before drawing plane");

        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0);

        mVertexBuffer.rewind();
        GLES20.glVertexAttribPointer(
                mPlaneXZPositionAlphaAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
                BYTES_PER_FLOAT * COORDS_PER_VERTEX, mVertexBuffer);

        GLES20.glUniformMatrix4fv(mPlaneModelUniform, 1, false, mModelMatrix, 0);
        GLES20.glUniformMatrix4fv(
                mPlaneModelViewProjectionUniform, 1, false, mModelViewProjectionMatrix, 0);

        mIndexBuffer.rewind();
        if (DRAW_CONCAVE) {
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, mIndexBuffer.limit(),
                    GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);
        } else {
            GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, mIndexBuffer.limit(),
                    GLES20.GL_UNSIGNED_SHORT, mIndexBuffer);
        }
        ShaderHelper.checkGLError(TAG, "after drawing plane");

    }

    static class SortablePlane implements Comparable<SortablePlane>{
        final float mDistance;
        final ARPlane mPlane;
        public SortablePlane(float distance, ARPlane plane) {
            this.mDistance = distance;
            this.mPlane = plane;
        }

        @Override
        public int compareTo(@NonNull SortablePlane o) {
            return Float.compare(mDistance, o.mDistance);
        }
    }

    public void drawPlanes(Collection<ARPlane> allPlanes, ARPose cameraPose,
                           float[] cameraPerspective) throws RemoteException {

        List<SortablePlane> sortedPlanes = new ArrayList<>();
        float[] normal = new float[3];
        float cameraX = cameraPose.tx();
        float cameraY = cameraPose.ty();
        float cameraZ = cameraPose.tz();
        for (ARPlane plane : allPlanes) {
            Log.d(TAG, "drawPlanes: "+plane.toString());
            if ((plane.getType() == ARPlane.PlaneType.UNKNOWN_FACING) ||
                    plane.getTrackingState() != ARTrackable.TrackingState.TRACKING || plane
                    .getSubsumedBy() != null) {
                continue;
            }
            ARPose center = plane.getCenterPose();

            center.getTransformedAxis(1, 1.0f, normal, 0);

            float distance = (cameraX - center.tx()) * normal[0] +
                    (cameraY - center.ty()) * normal[1] + (cameraZ - center.tz()) * normal[2];
            if (distance < 0) {
                continue;
            }
            sortedPlanes.add(new SortablePlane(distance, plane));
        }
        Collections.sort(sortedPlanes);


        float cameraView[] = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
        cameraPose.inverse().toMatrix(cameraView, 0);

        GLES20.glClearColor(1, 1, 1, 1);
        GLES20.glColorMask(false, false, false, true);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glColorMask(true, true, true, true);

        GLES20.glDepthMask(false);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_DST_ALPHA, GLES20.GL_ONE,
                GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glUseProgram(mPlaneProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
        GLES20.glUniform1i(mTextureUniform, 0);

        GLES20.glUniform4fv(mGridControlUniform, 1, GRID_CONTROL, 0);

        GLES20.glEnableVertexAttribArray(mPlaneXZPositionAlphaAttribute);

        ShaderHelper.checkGLError(TAG, "setting up to draw planes");


        for (SortablePlane sortedPlane : sortedPlanes) {
            ARPlane plane = sortedPlane.mPlane;
            float[] planeMatrix = new float[UtilsCommon.MAX_TRACKING_ANCHOR_NUM];
            plane.getCenterPose().toMatrix(planeMatrix, 0);

            updatePlaneParameters(planeMatrix, plane.getExtentX(),
                    plane.getExtentZ(), plane.getPlanePolygon());

            Integer planeIndex = mPlaneIndexMap.get(plane);
            if (planeIndex == null) {
                planeIndex = Integer.valueOf(mPlaneIndexMap.size());
                mPlaneIndexMap.put(plane, planeIndex);
            }



            int colorIndex = planeIndex % PLANE_COLORS_RGBA.length;
            colorRgbaToFloat(mPlaneColor, PLANE_COLORS_RGBA[colorIndex]);
            GLES20.glUniform4fv(mLineColorUniform, 1, mPlaneColor, 0);
            GLES20.glUniform4fv(mDotColorUniform, 1, mPlaneColor, 0);


            float angleRadians = planeIndex * 0.144f;
            float uScale = DOTS_PER_METER;
            float vScale = DOTS_PER_METER * EQUILATERAL_TRIANGLE_SCALE;
            mPlaneAngleUvMatrix[0] = +(float) Math.cos(angleRadians) * uScale;
            mPlaneAngleUvMatrix[1] = -(float) Math.sin(angleRadians) * uScale;
            mPlaneAngleUvMatrix[2] = +(float) Math.sin(angleRadians) * vScale;
            mPlaneAngleUvMatrix[3] = +(float) Math.cos(angleRadians) * vScale;
            GLES20.glUniformMatrix2fv(mPlaneUvMatrixUniform, 1, false, mPlaneAngleUvMatrix, 0);
            if (plane.getType() == ARPlane.PlaneType.VERTICAL_FACING) {
                GLES20.glUniform1f(mPlaneFacingUniform, 2.0f);
            } else if (plane.getType() == ARPlane.PlaneType.HORIZONTAL_UPWARD_FACING) {
                GLES20.glUniform1f(mPlaneFacingUniform, 1.0f);
            }

            draw(cameraView, cameraPerspective);
        }

        GLES20.glDisableVertexAttribArray(mPlaneXZPositionAlphaAttribute);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);

        ShaderHelper.checkGLError(TAG, "cleaning up after drawing planes");
    }

    // Calculate the normal distance to plane from cameraPose, the given planePose should have y axis
    // parallel to plane's normal, for example plane's center pose or hit test pose.
    public static float calculateDistanceToPlane(ARPose planePose, ARPose cameraPose) {
        float[] normal = new float[3];
        float cameraX = cameraPose.tx();
        float cameraY = cameraPose.ty();
        float cameraZ = cameraPose.tz();
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0f, normal, 0);
        // Compute dot product of plane's normal with vector from camera to plane center.
        return (cameraX - planePose.tx()) * normal[0]
                + (cameraY - planePose.ty()) * normal[1]
                + (cameraZ - planePose.tz()) * normal[2];
    }

    private static void colorRgbaToFloat(float[] planeColor, int colorRgba) {
        planeColor[0] = ((float) ((colorRgba >> 24) & 0xff)) / 255.0f;
        planeColor[1] = ((float) ((colorRgba >> 16) & 0xff)) / 255.0f;
        planeColor[2] = ((float) ((colorRgba >>  8) & 0xff)) / 255.0f;
        planeColor[3] = ((float) ((colorRgba >>  0) & 0xff)) / 255.0f;
    }

    private static final int[] PLANE_COLORS_RGBA = {
            0xffffffff,
            0xff1218ff,
            0x5288e5ff,
            0xe4c6bdff,
            0x56f4f8ff,
            0x1415dfff,
    };
}
