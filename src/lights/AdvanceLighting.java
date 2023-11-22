package lights;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import lights.graphics.*;
import lights.graphics.StaticBlockRenderer.*;
import lights.parts.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.part.*;
import mindustry.entities.part.DrawPart.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.type.weapons.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.draw.*;

import java.lang.reflect.*;

public class AdvanceLighting extends Mod{
    public static AltLightBatch batch;
    public static ObjectMap<TextureRegion, TextureRegion> glowEquiv = new ObjectMap<>();
    public static ObjectSet<TextureRegion> autoGlowRegions = new ObjectSet<>(), liquidRegions = new ObjectSet<>();
    public static IntMap<TextureRegion> uvGlowRegions = new IntMap<>();
    public static IntMap<EnviroGlow> glowingEnvTiles = new IntMap<>();
    public static IntSet uvAutoGlowRegions = new IntSet(), validBlocks = new IntSet();
    public static Shader screenShader, smoothAlphaCutShader;
    public static AdditiveBloom bloom;
    public static boolean bloomActive;
    static FrameBuffer buffer, subBuffer, subBuffer2;
    static Seq<TextureRegion> tmpRegions = new Seq<>(2);

    static Seq<Tile> tileView;
    static Rect view = new Rect();

    static int bloomQuality = 4;
    static boolean hideVanillaLights = false, renderEnvironment = true;
    static boolean test = false;
    static IntSet onGlowingTile = new IntSet();
    static StaticBlockRenderer staticRenderer;
    public static IntFloatMap glowingLiquidColors = new IntFloatMap();
    public static IntSet glowLiquidFloors = IntSet.with(CacheLayer.slag.id, CacheLayer.cryofluid.id, CacheLayer.space.id),
            glowingLiquids = new IntSet(), liquidBlocks = new IntSet();
    public static Floatf<Color> glowingLiquidColorsFunc = c -> {
        float v;
        if((v = glowingLiquidColors.get(ALStructs.rgb(c), -1f)) != -1f){
            return v;
        }

        return 0f;
    };

    private static Seq<Runnable> lights;
    private static Field circleCount;

    public AdvanceLighting(){
        if(Vars.headless) return;
        Events.run(Trigger.drawOver, () -> Draw.draw(Layer.fogOfWar + 2f, this::draw));
        Events.run(Trigger.draw, () -> {
            if(hideVanillaLights && Vars.enableLight && Vars.renderer.lights.enabled() && Vars.state.rules.infiniteResources){
                Draw.draw(Layer.light - 0.01f, this::hideLights);
            }
        });
        Events.on(FileTreeInitEvent.class, e -> Core.app.post(() -> {
            batch = new AltLightBatch();
            buffer = new FrameBuffer();
            subBuffer = new FrameBuffer();
            subBuffer2 = new FrameBuffer();
            staticRenderer = new StaticBlockRenderer();
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
            smoothAlphaCutShader = new Shader("""
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
                        vec4 c = texture2D(u_texture, v_texCoords);
                        c.a = (c.a * c.a) * 0.5 + c.a * 0.5;
                    
                    	gl_FragColor = c;
                    }
                    """);
        }));
        //Events.on(ContentInitEvent.class, e -> Core.app.post(() -> Core.app.post(this::load)));
        Events.on(ClientLoadEvent.class, e -> {
            loadSettings();
            Core.app.post(() -> {
                load();
                loadTileView();
            });
        });
        Events.on(ResetEvent.class, e -> {
            onGlowingTile.clear();
        });
        Events.on(TileChangeEvent.class, e -> {
            Tile tile = e.tile;
            handleLiquidTile(tile);
        });
        Events.on(TilePreChangeEvent.class, e -> {
            onGlowingTile.remove(e.tile.pos());
        });
        Events.on(WorldLoadEvent.class, e -> {
            if(renderEnvironment){
                staticRenderer.begin();
                for(Tile tile : Vars.world.tiles){
                    handleLiquidTile(tile);
                    staticRenderer.handleTile(tile);
                }
                staticRenderer.end();
            }
        });
    }

    void handleLiquidTile(Tile tile){
        if(!renderEnvironment) return;

        if(glowLiquidFloors.contains(tile.floor().cacheLayer.id) && tile.block().isAir()){
            onGlowingTile.remove(tile.pos());
        }
        Block block = tile.block();
        if(block == Blocks.air || block.isStatic()) return;

        int offset = -(block.size - 1) / 2;

        //boolean shouldAdd = glowLiquid.contains(tile.floor().cacheLayer.id);
        boolean shouldAdd = false;

        scan:
        for(int dx = 0; dx < block.size; dx++){
            for(int dy = 0; dy < block.size; dy++){
                int worldx = dx + offset + tile.x;
                int worldy = dy + offset + tile.y;

                Tile other1 = Vars.world.tile(worldx, worldy);
                if(other1 != null && (glowLiquidFloors.contains(other1.floor().cacheLayer.id) || (glowingEnvTiles.get(other1.floor().id) != null))){
                    shouldAdd = true;
                    break scan;
                }

                //inefficient
                for(Point2 d : Geometry.d8){
                    int ox = worldx + d.x, oy = worldy + d.y;

                    Tile other2 = Vars.world.tile(ox, oy);
                    if(other2 != null && (glowLiquidFloors.contains(other2.floor().cacheLayer.id))){
                        shouldAdd = true;
                        break scan;
                    }
                }
            }
        }
        if(shouldAdd){
            onGlowingTile.add(tile.pos());
        }
    }

    @SuppressWarnings("unchecked")
    void loadTileView(){
        try{
            Field f = BlockRenderer.class.getDeclaredField("tileview");
            f.setAccessible(true);

            tileView = (Seq<Tile>)f.get(Vars.renderer.blocks);
        }catch(Exception ex){
            tileView = null;
            Log.err(ex);
        }
        try{
            Field fr = LightRenderer.class.getDeclaredField("lights");
            fr.setAccessible(true);
            Field r = LightRenderer.class.getDeclaredField("circleIndex");
            r.setAccessible(true);

            lights = (Seq<Runnable>)fr.get(Vars.renderer.lights);
            circleCount = r;
        }catch(Exception ex){
            Log.err(ex);
        }
    }

    void loadSettings(){
        bloomQuality = Core.settings.getInt("al-bloom-quality", 4);
        hideVanillaLights = Core.settings.getBool("al-hide-lights", false);
        setBloom(Core.settings.getBool("al-bloom-enabled", false));
        renderEnvironment = Core.settings.getBool("al-environment-enabled", true);

        Vars.ui.settings.addCategory("@advance-lighting", "advance-lighting-setting-icon", st -> {
            st.checkPref("al-bloom-enabled", false, this::setBloom);
            st.checkPref("al-environment-enabled", renderEnvironment);

            st.row();

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
            st.sliderPref("al-bloom-threshold", 0, 0, 100, s -> {
                if(bloom != null){
                    bloom.threshold = s / 100f;
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
            st.sliderPref("al-bloom-blur-feedback", 100, 100, 200, s -> {
                if(bloom != null){
                    bloom.setBlurFeedBack(s / 100f);
                }
                return (s / 100f) + "";
            });
            st.sliderPref("al-bloom-flare-feedback", 100, 100, 200, s -> {
                if(bloom != null){
                    bloom.setFlareFeedBack(s / 100f);
                }
                return (s / 100f) + "";
            });

            st.row();

            st.checkPref("al-hide-lights", false, b -> hideVanillaLights = b);
        });
    }

    public void setBloom(boolean on){
        if(on && bloom == null){
            bloom = new AdditiveBloom(Core.graphics.getWidth(), Core.graphics.getHeight(), bloomQuality);

            bloom.blurPasses = Core.settings.getInt("al-bloom-blur-amount", 2);
            bloom.flarePasses = Core.settings.getInt("al-bloom-flare-amount", 3);
            bloom.intensity = Core.settings.getInt("al-bloom-intensity", 75) / 100f;
            bloom.flareLength = Core.settings.getInt("al-bloom-flare-length", 30) / 10f;
            bloom.threshold = Core.settings.getInt("al-bloom-threshold", 0) / 100f;

            bloom.setBlurFeedBack(Core.settings.getInt("al-bloom-blur-feedback", 100) / 100f);
            bloom.setFlareFeedBack(Core.settings.getInt("al-bloom-flare-feedback", 100) / 100f);
        }
        bloomActive = on;
    }

    TextureRegion get(String name){
        return Core.atlas.find(name + "-advance-light", Core.atlas.find("advance-lighting-" + name));
    }

    Seq<TextureRegion> getHeat(String name){
        tmpRegions.clear();
        //TextureRegion right = Core.atlas.find(name + "-advance-light-heat");
        TextureRegion right = find(name + "-advance-light-heat", name + "-advance-light-heat-r", "advance-lighting-" + name + "-heat", "advance-lighting-" + name + "-r-heat");
        TextureRegion left = find(name + "-advance-light-heat-l", "advance-lighting-" + name + "-l-heat");

        if(right.found()){
            tmpRegions.add(right);
            if(left.found()){
                tmpRegions.add(left);
            }
        }

        return tmpRegions;
    }
    TextureRegion find(String... name){
        TextureRegion nil = Core.atlas.find(name[0]);

        for(String s : name){
            nil = Core.atlas.find(s);
            if(nil.found()) return nil;
        }

        return nil;
    }

    boolean loadParts(Seq<DrawPart> parts, String name){
        TextureRegion r;
        boolean found = false;
        for(DrawPart part : parts){
            if(part instanceof RegionPart rp){
                String realName = rp.name == null ? name + rp.suffix : rp.name;
                if(rp.drawRegion && (r = get(realName)).found()){
                    for(TextureRegion r2 : rp.regions){
                        glowEquiv.put(r2, r);
                        found = true;
                    }
                }
                if(rp.heat.found()){
                    found = true;
                }
                if(!rp.children.isEmpty()){
                    found |= loadParts(rp.children, name);
                }

                Seq<TextureRegion> st;
                if(!(st = getHeat(realName)).isEmpty()){
                    GlowHeatPart hp;
                    if(st.size > 1){
                        hp = new GlowHeatPart(rp, st.get(0), st.get(1));
                    }else{
                        hp = new GlowHeatPart(rp, st.get(0), null);
                    }
                    hp.turretShading = rp.turretShading;
                    rp.children.add(hp);
                }
            }
        }
        return found;
    }

    boolean loadDraws(DrawBlock db){
        boolean found = false;

        if(db instanceof DrawWarmupRegion dw){
            autoGlowRegions.add(dw.region);
            found = true;
        }
        if(db instanceof DrawFlame df && (df.top != null && df.top.found())){
            autoGlowRegions.add(df.top);
            found = true;

            if(test) df.lightRadius = 0f;
        }
        if(db instanceof DrawPlasma dp){
            for(TextureRegion r : dp.regions){
                if(r.found()){
                    autoGlowRegions.add(r);
                    found = true;
                }
            }
        }
        if(db instanceof DrawWeave dw){
            if(dw.weave.found()){
                autoGlowRegions.add(dw.weave);
                found = true;
            }
        }
        if(db instanceof DrawMultiWeave dmw){
            if(dmw.weave.found()){
                autoGlowRegions.add(dmw.weave);
                found = true;
            }
            if(dmw.glow.found()){
                autoGlowRegions.add(dmw.glow);
                found = true;
            }
        }
        if(db instanceof DrawHeatInput || db instanceof DrawHeatOutput || db instanceof DrawHeatRegion){
            found = true;
        }
        if(db instanceof DrawMulti dm){
            for(DrawBlock drawer : dm.drawers){
                found |= loadDraws(drawer);
            }

            found |= handleDrawers(dm.drawers, dm.drawers.length);
        }

        return found;
    }
    boolean handleDrawers(DrawBlock[] draws, int size){
        //Might be a bad Idea.
        boolean found = false;
        for(int i = 0; i < size; i++){
            DrawBlock d = draws[i];
            if(drawOverride(d)){
                DrawGlowWrapper ndw = new DrawGlowWrapper(d);
                ndw.liquid = drawBlockLiquid(d);
                draws[i] = ndw;
                found = true;
            }
        }
        return found;
    }
    boolean drawOverride(DrawBlock draw){
        return (draw instanceof DrawCrucibleFlame || draw instanceof DrawWeave || draw instanceof DrawSpikes || draw instanceof DrawShape || draw instanceof DrawFlame || draw instanceof DrawPulseShape || draw instanceof DrawArcSmelt) || drawBlockLiquid(draw);
    }
    boolean drawBlockLiquid(DrawBlock draw){
        return draw instanceof DrawLiquidRegion || draw instanceof DrawLiquidOutputs || draw instanceof DrawLiquidTile;
    }

    void load(){
        /*
        for(Texture tex : Core.atlas.getTextures()){
            Log.info(tex.getTextureObjectHandle());
        }
        */
        EnviroGlow env = new EnviroGlow();
        TextureRegion[] tmpVariants = new TextureRegion[16];
        int tmpVariantsSize;

        for(Liquid liquid : Vars.content.liquids()){
            if(liquid.lightColor.a >= 0.1f){
                glowingLiquidColors.put(ALStructs.rgb(liquid.color), Mathf.clamp(liquid.lightColor.a / 0.4f));
                glowingLiquids.add(liquid.id);
            }
        }

        for(Block block : Vars.content.blocks()){
            boolean found = false;

            if(renderEnvironment){
                tmpVariantsSize = -1;
                if(block instanceof Floor fr && fr.cacheLayer == CacheLayer.normal){
                    boolean envf = false;
                    boolean hasVariants = fr.variantRegions != null;

                    //env.variants = new TextureRegion[fr.variantRegions.length];

                    if(hasVariants){
                        for(int i = 0; i < fr.variantRegions.length; i++){
                            AtlasRegion ar = fr.variantRegions[i].asAtlas();
                            TextureRegion gr;
                            if((gr = get(ar.name)).found()){
                                envf = true;
                                tmpVariants[i] = gr;
                            }else{
                                tmpVariants[i] = null;
                            }
                        }
                    }
                    if(envf){
                        env.variants = new TextureRegion[fr.variantRegions.length];
                        System.arraycopy(tmpVariants, 0, env.variants, 0, env.variants.length);
                        env.floor = true;

                        glowingEnvTiles.put(block.id, env);
                        env = new EnviroGlow();
                    }
                }
                if(block instanceof StaticWall sw){
                    boolean envf = false;

                    if(block.variants > 0 && block.variantRegions != null){
                        int i = 0;
                        for(TextureRegion r : block.variantRegions){
                            AtlasRegion ar = r.asAtlas();
                            TextureRegion gr;
                            if((gr = get(ar.name)).found()){
                                envf = true;
                                tmpVariants[i] = gr;
                            }else{
                                tmpVariants[i] = null;
                            }
                            i++;
                        }
                        if(envf){
                            tmpVariantsSize = block.variants;
                        }
                    }else{
                        AtlasRegion ar = block.region.asAtlas();
                        TextureRegion gr;
                        if((gr = get(ar.name)).found()){
                            envf = true;
                            env.region = gr;
                        }
                    }
                    if(sw.large.found()){
                        TextureRegion gr;
                        if((gr = get(sw.large.asAtlas().name)).found()){
                            envf = true;
                            env.large = gr;
                        }
                    }

                    if(envf){
                        env.floor = false;
                        env.largeFound = sw.large.found();
                        if(tmpVariantsSize > 0){
                            env.variants = new TextureRegion[tmpVariantsSize];
                            System.arraycopy(tmpVariants, 0, env.variants, 0, env.variants.length);
                        }

                        glowingEnvTiles.put(block.id, env);
                        env = new EnviroGlow();
                    }
                }
            }
            
            if(block instanceof TreeBlock || block instanceof TallBlock){
                validBlocks.add(block.id);
            }

            if(!block.hasBuilding()){
                continue;
            }

            if(test) block.lightRadius = 0f;

            if(block instanceof LiquidBlock || block instanceof LiquidSource){
                liquidBlocks.add(block.id);
            }

            TextureRegion tmr;
            if(!(block instanceof Turret) && (tmr = get(block.name)).found()){
                glowEquiv.put(block.region, tmr);
            }

            if(block instanceof Turret tr && tr.drawer instanceof DrawTurret dtr){
                TextureRegion r;
                Seq<TextureRegion> sr;
                //Assume all turret parts go outside the block
                //found = true;
                if(block.region.found() && (r = get(block.name)).found()){
                    glowEquiv.put(block.region, r);
                    //found = true;
                }
                if(dtr.liquid.found()){
                    liquidRegions.add(dtr.liquid);
                }

                loadParts(dtr.parts, block.name);

                if(!(sr = getHeat(block.name)).isEmpty()){
                    GlowHeatPart hp = new GlowHeatPart(PartProgress.heat, sr.get(0), null);
                    /*
                    if(sr.size > 1){
                        hp = new GlowHeatPart(PartProgress.heat, sr.get(0), sr.get(1));
                    }else{
                        hp = new GlowHeatPart(PartProgress.heat, sr.get(0), null);
                    }
                     */
                    hp.turretShading = false;
                    dtr.parts.insert(0, hp);
                }
            }
            if(block instanceof GenericCrafter gc){
                found = loadDraws(gc.drawer);

                if(drawOverride(gc.drawer)){
                    gc.drawer = new DrawGlowWrapper(gc.drawer);
                    found = true;
                }
            }
            if(block instanceof Separator sr){
                found = loadDraws(sr.drawer);
                
                if(drawOverride(sr.drawer)){
                    sr.drawer = new DrawGlowWrapper(sr.drawer);
                    found = true;
                }
            }
            if(block instanceof PowerGenerator pg){
                found = loadDraws(pg.drawer);

                if(drawOverride(pg.drawer)){
                    pg.drawer = new DrawGlowWrapper(pg.drawer);
                    found = true;
                }
            }
            if(block instanceof PowerNode pn){
                autoGlowRegions.add(pn.laserEnd);
                uvAutoGlowRegions.add(ALStructs.uv(pn.laser.texture, pn.laser.u, pn.laser.v));
            }

            /*
            if(block instanceof HeatConductor || block instanceof HeatProducer || block instanceof PowerBlock){
                if(!found){
                    exludeRegions.add(block.region);
                }
                found = true;
            }
            */

            if(found){
                validBlocks.add(block.id);
            }
        }

        autoGlowRegions.add(Core.atlas.find("minelaser-end"));
        TextureRegion mineLaser = Core.atlas.find("minelaser");
        uvAutoGlowRegions.add(ALStructs.uv(mineLaser.texture, mineLaser.u, mineLaser.v));

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
                        uvGlowRegions.put(ALStructs.uv(rl1.texture, rl1.u, rl1.v), r);
                    }
                }
                if(rl2 instanceof AtlasRegion rla2){
                    if((r = get(rla2.name)).found()){
                        //glowEquiv.put(rl2, r);
                        uvGlowRegions.put(ALStructs.uv(rl2.texture, rl2.u, rl2.v), r);
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

            if(!unit.parts.isEmpty()){
                loadParts(unit.parts, unit.name);
            }
            for(Weapon w : unit.weapons){
                TextureRegion r2;
                if(!w.parts.isEmpty()){
                    loadParts(w.parts, w.name);
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
                    uvAutoGlowRegions.add(ALStructs.uv(rw.laser.texture, rw.laser.u, rw.laser.v));

                    autoGlowRegions.add(rw.laserTopEnd);
                    uvAutoGlowRegions.add(ALStructs.uv(rw.laserTop.texture, rw.laserTop.u, rw.laserTop.v));
                }
            }

            if(!unit.engines.isEmpty()){
                //May cause issues, hope no one tries to actively reference an engine in the old array using a fixed index number.
                GlowEngines ge = new GlowEngines();
                ge.engines.addAll(unit.engines);
                unit.engines.clear();
                unit.engines.add(ge);
            }
        }
    }

    void drawTiles(){
        Team pteam = Vars.player.team();
        
        float bridge = Renderer.bridgeOpacity;
        Renderer.bridgeOpacity = 0.7f + (bridge * (1f - 0.7f));
        
        for(Tile tile : tileView){
            Block block = tile.block();
            Building build = tile.build;

            Draw.z(Layer.block);

            boolean visible = (build == null || !build.inFogTo(pteam));

            if(block != Blocks.air && (visible || build.wasVisible)){
                Liquid lc;
                boolean setLiquid = (build != null && liquidBlocks.contains(block.id) && ((lc = build.liquids.current()) != null && build.liquids.currentAmount() > 0.001f && glowingLiquids.contains(lc.id)));
                boolean valid = validBlocks.contains(block.id) || onGlowingTile.contains(tile.pos()) || setLiquid;

                batch.setExcludeLayer(-100f, valid ? -100f : Layer.blockAfterCracks);

                if(setLiquid) batch.setLiquidMode(true);
                block.drawBase(tile);
                Draw.reset();
                Draw.z(Layer.block);

                if(setLiquid) batch.setLiquidMode(false);

                if(build != null){
                    if(build.damaged()){
                        Draw.z(Layer.blockCracks);
                        build.drawCracks();
                        Draw.z(Layer.block);
                    }
                }
            }
        }
        batch.setExcludeLayer();
        Renderer.bridgeOpacity = bridge;
    }

    void draw(){
        buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        subBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        subBuffer2.resize(Core.graphics.getWidth(), Core.graphics.getHeight());

        buffer.begin(Color.clear);
        batch.begin();
        //Lines.useLegacyLine = true;

        if(renderEnvironment){
            Draw.draw(Layer.floor, () -> {
                staticRenderer.drawFloors();
                //Draw.flush();

                FloorRenderer fr = Vars.renderer.blocks.floor;

                fr.beginDraw();
                fr.drawLayer(CacheLayer.slag);
                fr.drawLayer(CacheLayer.cryofluid);
                fr.drawLayer(CacheLayer.space);
                fr.endDraw();
            });
            Draw.draw(Layer.block - 0.09f, () -> staticRenderer.drawWalls());
        }

        batch.setGlow(false);
        batch.setAuto(Layer.bullet - 0.02f, true);
        batch.setAuto(Layer.effect + 0.02f, false);

        if(tileView == null) batch.addUncapture(Layer.floor, Layer.turret - 1f);

        //batch.addUncapture(Layer.shields - 1f, Layer.shields + 1f);

        batch.setLayerGlow(Layer.shields - 1f, true);
        batch.setLayerGlow(Layer.shields + 1f, false);

        batch.setLayerGlow(Layer.buildBeam - 1f, true);
        batch.setLayerGlow(Layer.buildBeam + 1f, false);

        Draw.draw(Layer.plans, () -> Vars.renderer.overlays.drawBottom());
        Draw.draw(Layer.overlayUI, () -> {
            float lt = Time.delta;
            Time.delta = 0f;
            Vars.renderer.overlays.drawTop();
            Time.delta = lt;
        });

        //batch.addUncapture(Layer.buildBeam - 1f, Layer.buildBeam + 1f);

        boolean light = Vars.state.rules.lighting;
        Vars.state.rules.lighting = false;

        /*
        if(Vars.renderer.animateShields && Shaders.shield != null){
            FrameBuffer effectBuffer = Vars.renderer.effectBuffer;
            Draw.drawRange(Layer.shields, () -> effectBuffer.begin(Color.clear), () -> {
                effectBuffer.end();
                effectBuffer.blit(Shaders.shield);
            });
        }
         */
        Draw.drawRange(Layer.shields, 1f, () -> subBuffer.begin(Color.clear), () -> {
            subBuffer.end();

            subBuffer2.begin(Color.clear);
            subBuffer.blit((Vars.renderer.animateShields && Shaders.shield != null) ? Shaders.shield : screenShader);
            subBuffer2.end();

            subBuffer2.blit(smoothAlphaCutShader);
        });
        if(Vars.renderer.animateShields && Shaders.shield != null){
            Draw.drawRange(Layer.buildBeam, 1f, () -> subBuffer.begin(Color.clear), () -> {
                subBuffer.end();

                //subBuffer2.begin(Color.clear);
                subBuffer.blit(Shaders.buildBeam);
                //subBuffer2.end();

                //subBuffer2.blit(smoothAlphaCutShader);
            });
        }

        if(tileView == null){
            Vars.renderer.blocks.drawBlocks();
        }else{
            drawTiles();
        }
        Groups.draw.draw(Drawc::draw);
        //Redraws puddles, in hopes it doesn't slowdown regular draw with 'instanceof' that only contains 1% puddles
        Core.camera.bounds(view);
        for(Puddle p : Groups.puddle){
            if(p.liquid != null && glowingLiquids.contains(p.liquid.id)){
                float size = p.clipSize();
                //batch.setGlow(true);
                batch.setLiquidMode(true);
                batch.setLiquidGlow(p.liquid.lightColor.a / 0.4f);
                if(view.overlaps(p.x - size/2f, p.y - size/2f, size, size)){
                    p.draw();
                }
                //batch.setGlow(false);
                batch.setLiquidMode(false);
                batch.setLiquidGlow(-1f);
            }
        }

        Vars.state.rules.lighting = light;

        batch.end();
        //Lines.useLegacyLine = false;
        if(Vars.state.rules.fog && Vars.state.rules.staticFog){
            Draw.shader(Shaders.fog);
            Draw.color(Color.black);
            Draw.fbo(Vars.renderer.fog.getStaticTexture(), Vars.world.width(), Vars.world.height(), Vars.tilesize, Vars.tilesize/2f);

            Draw.shader();
        }

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

    void hideLights(){
        try{
            lights.clear();
            circleCount.setInt(Vars.renderer.lights, 0);
        }catch(Exception e){
            Log.err(e);
            hideVanillaLights = false;
        }
    }
}
