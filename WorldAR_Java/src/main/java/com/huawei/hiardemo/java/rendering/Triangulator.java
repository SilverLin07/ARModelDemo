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

import java.util.ArrayList;
import java.util.Collections;

import javax.vecmath.Vector2d;

public class Triangulator {
    private ArrayList<Vector2d> m_points;
    public Triangulator(Vector2d[] points) {
        m_points = new ArrayList<>();
        for (int i = 0; i < points.length; ++i) {
            m_points.add(points[i]);
        }
    }
    public Triangulator() {
        m_points = new ArrayList<>();
    }

    private int[] convertIntegers(ArrayList<Integer> integers) {
        int[] ret = new int[integers.size()];
        for (int i=0; i < ret.length; i++)
        {
            ret[i] = integers.get(i).intValue();
        }
        return ret;
    }
    public int[] Triangulate() {
        ArrayList<Integer> indices = new ArrayList<>();

        int n = m_points.size();
        if (n < 3) {
            return convertIntegers(indices);
        }

        int[] V = new int[n];
        if (Area() > 0) {
            for (int v = 0; v < n; v++)
                V[v] = v;
        }
        else {
            for (int v = 0; v < n; v++)
                V[v] = (n - 1) - v;
        }

        int nv = n;
        int count = 2 * nv;
        for (int m = 0, v = nv - 1; nv > 2; ) {
            if ((count--) <= 0) {
                return convertIntegers(indices);
            }

            int u = v;
            if (nv <= u)
                u = 0;
            v = u + 1;
            if (nv <= v)
                v = 0;
            int w = v + 1;
            if (nv <= w)
                w = 0;

            if (Snip(u, v, w, nv, V)) {
                int a, b, c, s, t;
                a = V[u];
                b = V[v];
                c = V[w];
                indices.add(a);
                indices.add(b);
                indices.add(c);
                m++;
                for (s = v, t = v + 1; t < nv; s++, t++)
                    V[s] = V[t];
                nv--;
                count = 2 * nv;
            }
        }

        Collections.reverse(indices);
        return convertIntegers(indices);
    }

    private float Area () {
        int n = m_points.size();
        float A = 0.0f;
        for (int p = n - 1, q = 0; q < n; p = q++) {
            Vector2d pval = m_points.get(p);
            Vector2d qval = m_points.get(q);
            A += pval.x * qval.y - qval.x * pval.y;
        }
        return (A * 0.5f);
    }

    private boolean Snip (int u, int v, int w, int n, int[] V) {
        int p;
        Vector2d A = m_points.get(V[u]);
        Vector2d B = m_points.get(V[v]);
        Vector2d C = m_points.get(V[w]);
        double epsilon = 0.000000001;
        if (epsilon > (((B.x - A.x) * (C.y - A.y)) - ((B.y - A.y) * (C.x - A.x))))
            return false;
        for (p = 0; p < n; p++) {
            if ((p == u) || (p == v) || (p == w))
                continue;
            Vector2d P = m_points.get(V[p]);
            if (InsideTriangle(A, B, C, P))
                return false;
        }
        return true;
    }

    private boolean InsideTriangle (Vector2d A, Vector2d B, Vector2d C, Vector2d P) {
        double ax, ay, bx, by, cx, cy, apx, apy, bpx, bpy, cpx, cpy;
        double cCROSSap, bCROSScp, aCROSSbp;

        ax = C.x - B.x; ay = C.y - B.y;
        bx = A.x - C.x; by = A.y - C.y;
        cx = B.x - A.x; cy = B.y - A.y;
        apx = P.x - A.x; apy = P.y - A.y;
        bpx = P.x - B.x; bpy = P.y - B.y;
        cpx = P.x - C.x; cpy = P.y - C.y;

        aCROSSbp = ax * bpy - ay * bpx;
        cCROSSap = cx * apy - cy * apx;
        bCROSScp = bx * cpy - by * cpx;

        return ((aCROSSbp >= 0.0f) && (bCROSScp >= 0.0f) && (cCROSSap >= 0.0f));
    }
}
