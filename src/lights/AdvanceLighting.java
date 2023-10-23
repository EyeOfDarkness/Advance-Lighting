package lights;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.graphics.gl.*;
import arc.struct.*;
import lights.graphics.*;
import lights.parts.*;
import mindustry.*;
import mindustry.entities.part.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.type.weapons.*;

public class AdvanceLighting extends Mod{
    public static AltLightBatch batch;
    public static ObjectMap<TextureRegion, TextureRegion> glowEquiv = new ObjectMap<>();
    public static ObjectSet<TextureRegion> autoGlowRegions = new ObjectSet<>();
    public static IntMap<TextureRegion> uvGlowRegions = new IntMap<>();
    public static IntSet uvAutoGlowRegions = new IntSet();
    public static Shader screenShader;
    public static AdditiveBloom bloom;
    public static boolean bloomActive;
    static FrameBuffer buffer;

    static int bloomQuality = 4;

    public AdvanceLighting(){
        if(Vars.headless) return;
        Events.run(Trigger.drawOver, () -> Draw.draw(Layer.light + 5f, this::draw));
        Events.on(FileTreeInitEvent.class, e -> Core.app.post(() -> {
            batch = new AltLightBatch();
            buffer = new FrameBuffer();
            setBloom(true);

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
        //Events.on(ContentInitEvent.class, e -> Core.app.post(() -> Core.app.post(this::load)));
        Events.on(ClientLoadEvent.class, e -> {
            loadSettings();
            Core.app.post(this::load);
        });
    }

    void loadSettings(){
        bloomQuality = Core.settings.getInt("al-bloom-quality", 4);
        setBloom(Core.settings.getBool("al-bloom-enabled", false));

        Vars.ui.settings.addCategory("advance-lighting", st -> {
            st.checkPref("al-bloom-enabled", false, this::setBloom);
            st.sliderPref("al-bloom-quality", 4, 1, 6, s -> {
                bloomQuality = s;
                if(s > 1){
                    return "1/" + s;
                }
                return "1";
            });
            st.sliderPref("al-bloom-intensity", 75, 0, 100, s -> {
                if(bloom != null){
                    bloom.intensity = s / 100f;
                }
                return s + "%";
            });
            st.sliderPref("al-bloom-blur-amount", 2, 0, 25, s -> {
                if(bloom != null){
                    bloom.blurPasses = s;
                }
                return s + "";
            });
            st.sliderPref("al-bloom-flare-amount", 3, 0, 25, s -> {
                if(bloom != null){
                    bloom.flarePasses = s;
                }
                return s + "";
            });
            st.sliderPref("al-bloom-flare-length", 30, 0, 100, s -> {
                if(bloom != null){
                    bloom.flareLength = s / 10f;
                }
                return (s / 10f) + "";
            });
        });
    }

    public void setBloom(boolean on){
        if(on && bloom == null){
            bloom = new AdditiveBloom(Core.graphics.getWidth(), Core.graphics.getHeight(), bloomQuality);

            bloom.blurPasses = Core.settings.getInt("al-bloom-blur-amount", 2);
            bloom.flarePasses = Core.settings.getInt("al-bloom-flare-amount", 3);
            bloom.intensity = Core.settings.getInt("al-bloom-intensity", 75) / 100f;
            bloom.flareLength = Core.settings.getInt("al-bloom-flare-length", 30) / 10f;
        }
        bloomActive = on;
    }

    TextureRegion get(String name){
        return Core.atlas.find(name + "-advance-light", Core.atlas.find("advance-lighting-" + name));
    }

    void load(){
        /*
        for(Texture tex : Core.atlas.getTextures()){
            Log.info(tex.getTextureObjectHandle());
        }
        */

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
                        //glowEquiv.put(rl1, r);
                        uvGlowRegions.put(UVStruct.uv(rl1.texture, rl1.u, rl1.v), r);
                    }
                }
                if(rl2 instanceof AtlasRegion rla2){
                    if((r = get(rla2.name)).found()){
                        //glowEquiv.put(rl2, r);
                        uvGlowRegions.put(UVStruct.uv(rl2.texture, rl2.u, rl2.v), r);
                    }
                }
            }

            if(unit.sample instanceof Crawlc){
                TextureRegion[] seg = unit.segmentRegions;
                for(TextureRegion sr : seg){
                    if(sr instanceof AtlasRegion sar && (r = get(sar.name)).found()){
                        glowEquiv.put(sr, r);
                    }
                }
            }

            for(Weapon w : unit.weapons){
                TextureRegion r2;
                if(!w.parts.isEmpty()){
                    for(DrawPart part : w.parts){
                        if(part instanceof RegionPart p){
                            for(TextureRegion region : p.regions){
                                if(region instanceof AtlasRegion ar && (r2 = get(ar.name)).found()){
                                    glowEquiv.put(region, r2);
                                }
                            }
                        }
                    }
                }
                if(w.region.found() && (r2 = get(w.name)).found()){
                    //TODO double flipping bug
                    /*
                    GlowPart g = new GlowPart(r2);
                    g.mirror = w.flipSprite;
                    w.parts.insert(0, g);
                    */
                    glowEquiv.put(w.region, r2);
                }
                if(w.cellRegion.found()){
                    autoGlowRegions.add(w.cellRegion);
                }
                if(w instanceof RepairBeamWeapon rw){
                    autoGlowRegions.add(rw.laserEnd);
                    uvAutoGlowRegions.add(UVStruct.uv(rw.laser.texture, rw.laser.u, rw.laser.v));

                    autoGlowRegions.add(rw.laserTopEnd);
                    uvAutoGlowRegions.add(UVStruct.uv(rw.laserTop.texture, rw.laserTop.u, rw.laserTop.v));
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

        batch.addUncapture(Layer.floor, Layer.turret - 1f);

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
        Draw.flush();
        Gl.blendEquationSeparate(Gl.max, Gl.max);
        Blending.additive.apply();

        buffer.blit(screenShader);

        Gl.blendEquationSeparate(Gl.funcAdd, Gl.funcAdd);
        Blending.normal.apply();

        if(bloomActive && bloom != null){
            bloom.resize(Core.graphics.getWidth(), Core.graphics.getHeight(), bloomQuality);
            bloom.render(buffer.getTexture());
        }
    }
}
