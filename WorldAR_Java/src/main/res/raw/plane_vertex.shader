

uniform mat4 u_Model;
uniform mat4 u_ModelViewProjection;
uniform mat2 u_PlaneUvMatrix;
uniform float u_PlaneFacing;

attribute vec3 a_XZPositionAlpha;

varying vec3 v_TexCoordAlpha;

void main() {
    vec4 position = vec4(a_XZPositionAlpha.x, 0.0, a_XZPositionAlpha.y, 1.0);
   gl_Position = u_ModelViewProjection * position;
   if (u_PlaneFacing == 2.0) {
        // vertical plane
        position = vec4(a_XZPositionAlpha.x, a_XZPositionAlpha.y, 0.0, 1.0);
   }
   v_TexCoordAlpha = vec3(u_PlaneUvMatrix * (u_Model * position).xz, a_XZPositionAlpha.z);
}