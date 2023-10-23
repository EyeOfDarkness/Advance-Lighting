uniform lowp sampler2D u_texture;
varying vec2 v_texCoords0;
varying vec2 v_texCoords1;
varying vec2 v_texCoords2;
varying vec2 v_texCoords3;
varying vec2 v_texCoords4;
const float center = 0.2270270270;
const float close = 0.3162162162;
const float far = 0.0702702703;

uniform float u_feedBack;

void main(){
    vec4 c = far * texture2D(u_texture, v_texCoords0)
    + close * texture2D(u_texture, v_texCoords1)
    + center * texture2D(u_texture, v_texCoords2)
    + close * texture2D(u_texture, v_texCoords3)
    + far * texture2D(u_texture, v_texCoords4);
    
    vec4 nc = vec4(mix(vec3(0.0, 0.0, 0.0), c.rgb, c.a), 1.0);

    if(abs(u_feedBack - 1.0) > 0.05){
        //c.rgb = pow(c.rgb, u_feedBack);
        
        //nc.r = pow(nc.r, u_feedBack);
        //nc.g = pow(nc.g, u_feedBack);
        //nc.b = pow(nc.b, u_feedBack);
        float fpow = u_feedBack / 5.0 + 0.8;
        
        nc.r = pow(nc.r, fpow) * u_feedBack;
        nc.g = pow(nc.g, fpow) * u_feedBack;
        nc.b = pow(nc.b, fpow) * u_feedBack;
    }

    gl_FragColor = nc;
}
