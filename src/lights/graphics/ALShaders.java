package lights.graphics;

import arc.*;
import arc.files.*;
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
            setUniformf("u_time", s.time);
            setUniformf("u_color", s.color);
            setUniformf("u_progress", s.progress);
            setUniformf("u_uv", s.region.u, s.region.v);
            setUniformf("u_uv2", s.region.u2, s.region.v2);
            setUniformf("u_texsize", s.region.texture.width, s.region.texture.height);
        }
    }
    static class ALBlockBuildShader extends Shader implements UnapplyableShader{
        ALBlockBuildShader(){
            super(intFile("default.vert"), file("alblockbuild.frag"));
        }

        @Override
        public void apply(){
            BlockBuildShader s = Shaders.blockbuild;
            setUniformf("u_progress", s.progress);
            setUniformf("u_uv", s.region.u, s.region.v);
            setUniformf("u_uv2", s.region.u2, s.region.v2);
            setUniformf("u_time", s.time);
            setUniformf("u_texsize", s.region.texture.width, s.region.texture.height);
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
