package lights.graphics;

import arc.*;
import arc.files.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import lights.*;
import mindustry.*;
import mindustry.graphics.*;
import mindustry.graphics.Shaders.*;

public class ALShaders{
    public static Shader build, block;

    public static void load(){
        build = new ALUnitBuildShader();
        block = new ALBlockBuildShader();
    }

    public static Fi file(String path){
        return Vars.tree.get("shaders/" + path);
    }
    public static Fi intFile(String path){
        return Core.files.internal("shaders/" + path);
    }

    static class ALUnitBuildShader extends Shader{
        ALUnitBuildShader(){
            super(intFile("default.vert"), file("alunitbuild.frag"));
        }

        @Override
        public void apply(){
            UnitBuildShader s = Shaders.build;
            TextureRegion region = s.region, r2;
            if((r2 = AdvanceLighting.shaderReplace.get(region)) != null){
                region = r2;
            }

            setUniformf("u_time", s.time);
            setUniformf("u_color", s.color);
            setUniformf("u_progress", s.progress);
            setUniformf("u_uv", region.u, region.v);
            setUniformf("u_uv2", region.u2, region.v2);
            setUniformf("u_texsize", region.texture.width, region.texture.height);
            
            setUniformi("u_glowing", r2 == null ? 0 : 2);
        }
    }
    static class ALBlockBuildShader extends Shader implements UnapplyableShader{
        ALBlockBuildShader(){
            super(intFile("default.vert"), file("alblockbuild.frag"));
        }

        @Override
        public void apply(){
            BlockBuildShader s = Shaders.blockbuild;
            TextureRegion region = s.region, r2;
            if((r2 = AdvanceLighting.shaderReplace.get(region)) != null){
                region = r2;
            }

            setUniformf("u_progress", s.progress);
            setUniformf("u_uv", region.u, region.v);
            setUniformf("u_uv2", region.u2, region.v2);
            setUniformf("u_time", s.time);
            setUniformf("u_texsize", region.texture.width, region.texture.height);

            setUniformi("u_glowing", r2 == null ? 0 : 2);
        }

        @Override
        public void preapply(){
            AdvanceLighting.batch.setGlow(true);
        }

        @Override
        public void unapply(){
            AdvanceLighting.batch.setGlow(false);
        }
    }

    public interface UnapplyableShader{
        void preapply();
        void unapply();
    }
}
