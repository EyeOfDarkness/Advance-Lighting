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
    public float intensity = 0.75f, threshold = 0f, flareLength = 3f, blurSize = 1f;
    public float blurFeedBack = 1f, flareFeedBack = 1f;
    public float saturation = 12f;
    public float flareDirection = 0f;
    public int blurDiffuseAmount = 0;
    public float blurDiffuseSize = 1.75f, blurDiffuseFeedBack = 1f;

    private FrameBuffer pingPong1, pingPong2, pingPong3;
    private Shader blurShader, renderShader, thresholdShader, diffuseShader;

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
        thresholdShader = createThresholdShader();
        diffuseShader = createDiffuseShader();

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
        Draw.blit(texture, thresholdShader);
        pingPong1.end();

        for(int i = 0; i < blurPasses; i++){
            float f = blurPasses > 1 ? Mathf.lerp(blurFeedBack, 1f, i / (blurPasses - 1f)) : 1f;

            pingPong2.begin();
            blurShader.bind();
            blurShader.setUniformf("u_feedBack", f);
            blurShader.setUniformf("dir", blurSize, 0f);
            pingPong1.blit(blurShader);
            pingPong2.end();

            pingPong1.begin();
            blurShader.bind();
            //blurShader.setUniformf("u_feedBack", f);
            blurShader.setUniformf("dir", 0f, blurSize);
            pingPong2.blit(blurShader);
            pingPong1.end();
        }

        if(blurDiffuseAmount > 0){
            pingPong3.begin(Color.black);
            //diffuseShader.bind();
            //diffuseShader.setUniformf("u_diffuse", 1f / (blurDiffuseAmount * 2));
            //diffuseShader.setUniformf("u_diffuse", 1f / (blurDiffuseAmount / (1f + (blurDiffuseSize - 1f) / 10f)));
            //diffuseShader.setUniformf("u_diffuse", 1f);
            pingPong1.blit(diffuseShader);
            pingPong3.end();

            //Blending.additive.apply();

            float scl = blurSize * blurDiffuseSize;
            for(int i = 0; i < blurDiffuseAmount; i++){
                float fb = blurDiffuseAmount > 1 ? Mathf.lerp(blurDiffuseFeedBack, 1f, i / (blurDiffuseAmount - 1f)) : 1f;
                
                pingPong2.begin();
                blurShader.bind();
                //blurShader.setUniformf("u_feedBack", 1f);
                blurShader.setUniformf("u_feedBack", fb);
                blurShader.setUniformf("dir", scl, 0f);
                pingPong3.blit(blurShader);
                /*
                if(i == 0){
                    pingPong3.blit(blurShader);
                }else{
                    pingPong1.blit(blurShader);
                }
                */
                pingPong2.end();

                pingPong1.begin();
                blurShader.bind();
                //blurShader.setUniformf("u_feedBack", 1);
                blurShader.setUniformf("dir", 0f, scl);
                pingPong2.blit(blurShader);
                pingPong1.end();

                //scl *= blurDiffuseSize;
                scl += blurSize * blurDiffuseSize;

                Gl.blendEquationSeparate(Gl.max, Gl.max);
                Blending.additive.apply();

                pingPong3.begin();
                pingPong1.blit(AdvanceLighting.screenShader);
                pingPong3.end();

                Gl.blendEquationSeparate(Gl.funcAdd, Gl.funcAdd);
                Gl.disable(Gl.blend);
            }

            pingPong1.begin(Color.black);
            pingPong3.blit(AdvanceLighting.screenShader);
            pingPong1.end();
        }
        
        if(flarePasses > 0){
            float sx = Mathf.cosDeg(flareDirection) * flareLength, sy = Mathf.sinDeg(flareDirection) * flareLength;

            pingPong3.begin(Color.black);
            Draw.blit(texture, thresholdShader);
            pingPong3.end();

            for(int i = 0; i < flarePasses; i++){
                float f = flarePasses > 1 ? Mathf.lerp(flareFeedBack, 1f, i / (flarePasses - 1f)) : 1f;

                pingPong2.begin();
                blurShader.bind();
                blurShader.setUniformf("u_feedBack", f);
                blurShader.setUniformf("dir", sx, sy);
                pingPong3.blit(blurShader);
                pingPong2.end();

                pingPong3.begin();
                blurShader.bind();
                blurShader.setUniformf("u_feedBack", f);
                blurShader.setUniformf("dir", sx, sy);
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
    public void setSaturation(float v){
        saturation = v;
        renderShader.bind();
        renderShader.setUniformf("u_saturation", saturation);
    }
    public void setThreshold(float v){
        threshold = v;
        thresholdShader.bind();
        thresholdShader.setUniformf("u_threshold", threshold);
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
                uniform float u_saturation;
                
                varying vec2 v_texCoords;
                
                void main(){
                    vec4 v = texture2D(u_texture, v_texCoords);
                    vec3 tvc = mix(vec3(0.0, 0.0, 0.0), v.rgb, v.a);
                    
                    float mx = max(tvc.r, max(tvc.g, tvc.b));
                    float mn = min(tvc.r, min(tvc.g, tvc.b));
                    
                    float power = (mx - mn) * u_saturation;
                    
                    float apow = power + 1.0;
                    tvc.r = pow(tvc.r, 1.0 + power) * apow;
                    tvc.g = pow(tvc.g, 1.0 + power) * apow;
                    tvc.b = pow(tvc.b, 1.0 + power) * apow;
                    
                    //v.a = v.a * ta;
                    if(u_intensity < 1.0){
                        float ta = 1.0 - pow(1.0 - u_intensity, 4.0);
                        float ip = max(u_intensity, 0.05);
                        float nmax = max(max(tvc.r, max(tvc.g, tvc.b)), 1.0);
                        
                        tvc.r = pow(tvc.r / nmax, 1.0 / ip) * ta * nmax;
                        tvc.g = pow(tvc.g / nmax, 1.0 / ip) * ta * nmax;
                        tvc.b = pow(tvc.b / nmax, 1.0 / ip) * ta * nmax;
                    }
                    
                    //gl_FragColor = v;
                    gl_FragColor = vec4(tvc, 1.0);
                }
                """);
    }
    private static Shader createThresholdShader(){
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
                uniform float u_threshold;
                
                varying vec2 v_texCoords;
                
                void main(){
                    vec4 v = texture2D(u_texture, v_texCoords);
                    vec3 tvc = mix(vec3(0.0, 0.0, 0.0), v.rgb, v.a);
                    
                    if(u_threshold > 0.0){
                        float ff = u_threshold;
                        tvc.r = max(0.0, (tvc.r - ff) / (1.0 - ff));
                        tvc.g = max(0.0, (tvc.g - ff) / (1.0 - ff));
                        tvc.b = max(0.0, (tvc.b - ff) / (1.0 - ff));
                    }
                
                    gl_FragColor = vec4(tvc, 1.0);
                }
                """);
    }
    private static Shader createDiffuseShader(){
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
                    
                    varying vec2 v_texCoords;
                    //uniform float u_diffuse;
                    
                    void main(){
                        vec4 c = texture2D(u_texture, v_texCoords);
                        //c.rgb = c.rgb * u_diffuse;
                        vec3 tc = mix(vec3(0.0, 0.0, 0.0), c.rgb, c.a);
                    	gl_FragColor = vec4(tc, 1.0);
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
