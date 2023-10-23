package lights.graphics;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.Pixmap.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import lights.*;
import mindustry.*;

public class AdditiveBloom{
    public int blurPasses = 2, flarePasses = 3;
    public float intensity = 0.75f, flareLength = 3f;
    public float blurFeedBack = 1f, flareFeedBack = 1f;
    private FrameBuffer pingPong1, pingPong2, pingPong3;
    private Shader blurShader, renderShader;

    private float lastIntens = 0.75f;

    public AdditiveBloom(int width, int height, int scale){
        init(width / scale, height / scale);
    }

    void init(int width, int height){
        Format format = Format.rgba8888;
        pingPong1 = new FrameBuffer(format, width, height, false);
        pingPong2 = new FrameBuffer(format, width, height, false);
        pingPong3 = new FrameBuffer(format, width, height, false);

        blurShader = createBlurShader();
        renderShader = createRenderShader();

        setSize(width, height);
        setIntensity(0.75f);
    }

    public void resize(int width, int height, int scale){
        boolean changed = (pingPong1.getWidth() != width / scale || pingPong1.getHeight() != height / scale);
        if(changed){
            pingPong1.resize(width / scale, height / scale);
            pingPong2.resize(width / scale, height / scale);
            pingPong3.resize(width / scale, height / scale);
            setSize(width / scale, height / scale);
        }
    }

    public void render(Texture texture){
        Gl.disable(Gl.blend);

        if(intensity != lastIntens){
            setIntensity(intensity);
            lastIntens = intensity;
        }

        pingPong1.begin(Color.black);
        //Draw.blit(texture, AdvanceLighting.screenShader);
        Draw.blit(texture, renderShader);
        pingPong1.end();

        for(int i = 0; i < blurPasses; i++){
            float f = blurPasses > 1 ? Mathf.lerp(blurFeedBack, 1f, i / (blurPasses - 1f)) : 1f;

            pingPong2.begin();
            blurShader.bind();
            blurShader.setUniformf("u_feedBack", f);
            blurShader.setUniformf("dir", 1f, 0f);
            pingPong1.blit(blurShader);
            pingPong2.end();

            pingPong1.begin();
            blurShader.bind();
            blurShader.setUniformf("u_feedBack", f);
            blurShader.setUniformf("dir", 0f, 1f);
            pingPong2.blit(blurShader);
            pingPong1.end();
        }
        
        if(flarePasses > 0){
            pingPong3.begin(Color.black);
            Draw.blit(texture, AdvanceLighting.screenShader);
            pingPong3.end();

            for(int i = 0; i < flarePasses; i++){
                float f = flarePasses > 1 ? Mathf.lerp(flareFeedBack, 1f, i / (flarePasses - 1f)) : 1f;

                pingPong2.begin();
                blurShader.bind();
                blurShader.setUniformf("u_feedBack", f);
                blurShader.setUniformf("dir", flareLength, 0f);
                pingPong3.blit(blurShader);
                pingPong2.end();

                pingPong3.begin();
                blurShader.bind();
                blurShader.setUniformf("u_feedBack", f);
                blurShader.setUniformf("dir", flareLength, 0f);
                pingPong2.blit(blurShader);
                pingPong3.end();
            }
        }

        //Gl.enable(Gl.blend);
        Blending.additive.apply();
        pingPong1.blit(renderShader);
        if(flarePasses > 0){
            pingPong3.blit(renderShader);
        }

        Blending.normal.apply();
    }

    private void setSize(int width, int height){
        blurShader.bind();
        blurShader.setUniformf("size", width, height);
    }
    public void setIntensity(float v){
        renderShader.bind();
        renderShader.setUniformf("u_intensity", v);
    }
    public void setBlurFeedBack(float v){
        blurFeedBack = v;
    }
    public void setFlareFeedBack(float v){
        flareFeedBack = v;
    }

    private static Shader createRenderShader(){
        return new Shader("""
                attribute vec4 a_position;
                attribute vec2 a_texCoord0;
                
                varying vec2 v_texCoords;
                
                void main(){
                    v_texCoords = a_texCoord0;
                    gl_Position = a_position;
                }
                """, """
                uniform sampler2D u_texture;
                
                uniform float u_intensity;
                
                varying vec2 v_texCoords;
                const float sat = 12.0;
                
                void main(){
                    vec4 v = texture2D(u_texture, v_texCoords);
                    
                    //float asat = sat * 0.25 + 1.0;
                    
                    float mx = max(v.r, max(v.g, v.b));
                    float mn = min(v.r, min(v.g, v.b));
                    
                    float power = (mx - mn) * sat;
                    //float power2 = power * 0.25 + 0.75;
                    
                    //v.r = pow(pow(v.r, power) * asat, 1.0 / power2);
                    //v.g = pow(pow(v.g, power) * asat, 1.0 / power2);
                    //v.b = pow(pow(v.b, power) * asat, 1.0 / power2);
                    
                    //v.r = pow(v.r, pow(power, 1f - (v.r - mn) * 0.5));
                    //v.g = pow(v.g, pow(power, 1f - (v.g - mn) * 0.5));
                    //v.b = pow(v.b, pow(power, 1f - (v.b - mn) * 0.5));
                    
                    v.r = pow(v.r, 1.5 / (1.0 + (v.r - mn) * power));
                    v.g = pow(v.g, 1.5 / (1.0 + (v.g - mn) * power));
                    v.b = pow(v.b, 1.5 / (1.0 + (v.b - mn) * power));
                    
                    v.a = v.a * u_intensity;
                    
                    gl_FragColor = v;
                }
                """);
    }

    private static Fi local(String name){
        return Vars.tree.get("shaders/" + name);
    }

    private static Shader createBlurShader(){
        //return new Shader(Core.files.internal("bloomshaders/blurspace.vert"), Core.files.internal("bloomshaders/alpha_gaussian.frag"));
        return new Shader(Core.files.internal("bloomshaders/blurspace.vert"), Vars.tree.get("shaders/feedbackblur.frag"));
    }
}
