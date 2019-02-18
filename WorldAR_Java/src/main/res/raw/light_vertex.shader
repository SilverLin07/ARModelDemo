

uniform mat4 u_Model;
uniform mat4 u_ModelViewProjection;
uniform mat4 u_ModelView;
uniform vec3 u_LightPos;

attribute vec4 a_Position;
attribute vec4 a_Color;
attribute vec3 a_Normal;

varying vec4 v_Color;
varying vec3 v_Grid;

void main() {
   v_Grid = vec3(u_Model * a_Position);

   vec3 modelViewVertex = vec3(u_ModelView * a_Position);
   vec3 modelViewNormal = vec3(u_ModelView * vec4(a_Normal, 0.0));

   float distance = length(u_LightPos - modelViewVertex);
   vec3 lightVector = normalize(u_LightPos - modelViewVertex);
   float diffuse = max(dot(modelViewNormal, lightVector), 0.5);

   diffuse = diffuse * (1.0 / (1.0 + (0.00001 * distance * distance)));
   v_Color = vec4(a_Color.rgb * diffuse, a_Color.a);
   gl_Position = u_ModelViewProjection * a_Position;
}