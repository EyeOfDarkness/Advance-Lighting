package lights.graphics;

import arc.graphics.*;
import arc.math.*;

public class LightRequest implements Comparable<LightRequest>{
    float z;
    float color, mixColor;
    float[] vertices = new float[24];
    boolean manual = false;
    Blending blend = Blending.normal;
    Texture texture;
    Runnable run;
    /**
     * 1b action active
     * 2b set auto
     */
    byte action = 0;

    final static Color tc = new Color();

    void convertGlow(){
        for(int i = 2; i < 24; i += 6){
            vertices[i] = color;
        }
    }
    void convertAutoColor(){
        //float color = this.color;

        tc.abgr8888(color);
        //tc.mul(light);

        //float lum = Math.max((((tc.r + tc.g + tc.b) / 3f) - 0.7f) / 0.3f, 0f);
        float lum = Mathf.curve((tc.r + tc.g + tc.b) / 3f, 0.65f, 0.85f);
        float sat = Mathf.curve(Math.max(tc.r, Math.max(tc.g, tc.b)) - Math.min(tc.r, Math.min(tc.g, tc.b)), 0.15f, 0.4f);
        //float sat = Mathf.clamp(Math.max((((Math.max(tc.r, Math.max(tc.g, tc.b)) - Math.min(tc.r, Math.min(tc.g, tc.b))) - 0.5f) / 0.5f) * 1.2f, 0f) + lum);
        float v = Mathf.clamp(sat + lum);
        //tc.mul(sat);
        tc.r *= v;
        tc.g *= v;
        tc.b *= v;

        float c2 = tc.toFloatBits();

        for(int i = 2; i < 24; i += 6){
            vertices[i] = c2;
        }
    }

    @Override
    public int compareTo(LightRequest o){
        return Float.compare(z, o.z);
    }
}
