package lights;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.graphics.gl.*;
import arc.struct.*;
import arc.util.*;
import lights.graphics.*;
import lights.parts.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;

public class AdvanceLighting extends Mod{
    public static AltLightBatch batch;
    public static ObjectMap<TextureRegion, TextureRegion> glowEquiv = new ObjectMap<>();
    public static ObjectSet<TextureRegion> autoGlowRegions = new ObjectSet<>();
    static FrameBuffer buffer;
    static Shader screenShader;

    public AdvanceLighting(){
        if(Vars.headless) return;
        Events.run(Trigger.drawOver, () -> Draw.draw(Layer.light + 5f, this::draw));
        Events.on(FileTreeInitEvent.class, e -> Core.app.post(() -> {
            batch = new AltLightBatch();
            buffer = new FrameBuffer();

            screenShader = new Shader("""
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
                                        
                    void main(){
                    	gl_FragColor = texture2D(u_texture, v_texCoords);
                    }
                    """);
        }));
        Events.on(ContentInitEvent.class, e -> Core.app.post(() -> Core.app.post(this::load)));
    }

    TextureRegion get(String name){
        return Core.atlas.find(name + "-advance-light", Core.atlas.find("advance-lighting-" + name));
    }

    void load(){
        for(UnitType unit : Vars.content.units()){
            if(unit.internal) continue;
            TextureRegion r;
            if((r = get(unit.name)).found()){
                //GlowPart g = new GlowPart(r);
                //Log.info(unit.name + "-advance-lighting");
                //unit.parts.insert(0, g);
                glowEquiv.put(unit.region, r);
            }
            if(unit.drawCell) autoGlowRegions.add(unit.cellRegion);

            if(unit.sample instanceof Legsc){
                TextureRegion rl1 = unit.legRegion, rl2 = unit.legBaseRegion;
                //Just in case
                if(rl1 instanceof AtlasRegion rla1){
                    if((r = get(rla1.name)).found()){
                        glowEquiv.put(rl1, r);
                    }
                }
                if(rl2 instanceof AtlasRegion rla2){
                    if((r = get(rla2.name)).found()){
                        glowEquiv.put(rl2, r);
                    }
                }
            }

            for(Weapon w : unit.weapons){
                TextureRegion r2;
                if((r2 = Core.atlas.find(w.name + "advance-light", Core.atlas.find("advance-lighting-" + w.name))).found()){
                    GlowPart g = new GlowPart(r2);
                    w.parts.insert(0, g);
                }
                if(w.cellRegion.found()){
                    autoGlowRegions.add(w.cellRegion);
                }
            }

            if(!unit.engines.isEmpty()){
                //May cause issues, hope no one tries to actively reference an engine in the old array using a fixed index number.
                GlowEngines ge = new GlowEngines();
                ge.engines.addAll(unit.engines);
                unit.engines.clear();
                unit.engines.add(ge);
            }

            //TODO testing purposes remove
            unit.lightRadius = 0f;
        }
    }

    void draw(){
        buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());

        buffer.begin(Color.clear);
        batch.begin();
        //Lines.useLegacyLine = true;

        batch.setGlow(false);
        batch.setAuto(Layer.bullet - 0.02f, true);
        batch.setAuto(Layer.effect + 0.02f, false);
        batch.setAuto(Layer.power - 0.0001f, true);
        batch.setAuto(Layer.power + 0.0001f, false);

        batch.addUncapture(Layer.floor, Layer.turretHeat + 1f);

        batch.addUncapture(Layer.shields - 1f, Layer.shields + 1f);
        batch.addUncapture(Layer.buildBeam - 1f, Layer.buildBeam + 1f);

        boolean light = Vars.state.rules.lighting;
        Vars.state.rules.lighting = false;

        Vars.renderer.blocks.drawBlocks();
        Groups.draw.draw(Drawc::draw);

        Vars.state.rules.lighting = light;

        batch.end();
        //Lines.useLegacyLine = false;
        buffer.end();
        buffer.blit(screenShader);
    }
}
