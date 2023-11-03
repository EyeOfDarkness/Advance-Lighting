package lights.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import lights.*;
import lights.graphics.AltCacheSpriteBatch.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

public class StaticBlockRenderer{
    FrameBuffer buffer = new FrameBuffer();
    Seq<Tile> edges = new Seq<>(), glowing = new Seq<>(), glowingFloor = new Seq<>();

    AltCacheSpriteBatch cbatch = new AltCacheSpriteBatch();
    AltCacheSprites[][] caches, floorCaches;

    static Rect tmpRect = new Rect();

    //TODO draw walls and floors if it has a glow region.

    public void begin(){
        buffer.resize(Vars.world.width(), Vars.world.height());
        buffer.getTexture().setFilter(TextureFilter.nearest);

        int rw = Vars.world.width() / 16 + 1;
        int rh = Vars.world.height() / 16 + 1;
        caches = new AltCacheSprites[rw][rh];
        floorCaches = new AltCacheSprites[rw][rh];

        buffer.begin(Color.clear);
        edges.clear();
        glowing.clear();
        glowingFloor.clear();

        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
    }

    public void handleTile(Tile tile){
        if(tile.block() != Blocks.air && tile.block().isStatic()){
            Draw.color(Color.black);
            Fill.rect(tile.x + 0.5f, tile.y + 0.5f, 1f, 1f);
        }
        if(tile.block() instanceof StaticTree){
            boolean edge = false;
            boolean on = AdvanceLighting.glowLiquid.contains(tile.floor().cacheLayer.id);
            for(Point2 d : Geometry.d8){
                int tx = tile.x + d.x, ty = tile.y + d.y;
                Tile t = Vars.world.tile(tx, ty);
                if(t != null && t.block() == Blocks.air && (on || AdvanceLighting.glowLiquid.contains(t.floor().cacheLayer.id))){
                    edge = true;
                    break;
                }
            }
            if(edge){
                edges.add(tile);
            }
        }
        EnviroGlow eg;
        if((eg = AdvanceLighting.glowingEnvTiles.get(tile.block().id)) != null){
            if(!eg.floor){
                //glowingFloor.add(tile);
                glowing.add(tile);
            }
        }
        if((eg = AdvanceLighting.glowingEnvTiles.get(tile.floor().id)) != null){
            if(eg.floor){
                glowingFloor.add(tile);
            }
        }
    }

    public void end(){
        buffer.end();

        Batch lb = Core.batch;
        Core.batch = cbatch;

        cbatch.setGlowing(false);
        for(Tile t : edges){
            insertTile(t);
        }
        cbatch.setGlowing(true);
        for(Tile t : glowing){
            insertTile(t);
        }
        for(Tile t : glowingFloor){
            insertFloor(t);
        }
        cbatch.setGlowing(false);

        Core.batch = lb;
    }

    void insertFloor(Tile tile){
        int rx = Mathf.clamp(tile.x / 16, 0, floorCaches.length - 1);
        int ry = Mathf.clamp(tile.y / 16, 0, floorCaches[0].length - 1);

        AltCacheSprites cache = floorCaches[rx][ry];
        if(cache == null){
            cache = new AltCacheSprites();
            floorCaches[rx][ry] = cache;
        }

        cbatch.activeCache = cache;

        EnviroGlow g = AdvanceLighting.glowingEnvTiles.get(tile.floor().id);
        drawGlowFloor(tile, g);
    }

    void insertTile(Tile tile){
        int rx = Mathf.clamp(tile.x / 16, 0, caches.length - 1);
        int ry = Mathf.clamp(tile.y / 16, 0, caches[0].length - 1);

        AltCacheSprites cache = caches[rx][ry];
        if(cache == null){
            cache = new AltCacheSprites();
            caches[rx][ry] = cache;
        }

        cbatch.activeCache = cache;

        if(!AltCacheSpriteBatch.glowing){
            tile.block().drawBase(tile);
        }else{
            EnviroGlow g = AdvanceLighting.glowingEnvTiles.get(tile.block().id);
            drawGlowWall(tile, g);
        }
    }
    void drawGlowFloor(Tile tile, EnviroGlow glow){
        Mathf.rand.setSeed(tile.pos());
        //variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))]
        TextureRegion r = glow.variants[Mathf.randomSeed(tile.pos(), 0, Math.max(0, glow.variants.length - 1))];
        if(r != null){
            Draw.rect(r, tile.worldx(), tile.worldy());
        }
    }

    public void drawGlowWall(Tile tile, EnviroGlow glow){
        int rx = tile.x / 2 * 2;
        int ry = tile.y / 2 * 2;

        if(glow.largeFound && eq2(rx, ry, tile.block())){
            //Draw.rect(split[tile.x % 2][1 - tile.y % 2], tile.worldx(), tile.worldy());
            if(tile.x == rx && tile.y == ry && glow.large != null){
                float offset = Vars.tilesize / 2f;
                Draw.rect(glow.large, tile.worldx() + offset, tile.worldy() + offset);
            }
        }else if(glow.variants != null && glow.variants.length > 0){
            //Draw.rect(variantRegions[Mathf.randomSeed(tile.pos(), 0, Math.max(0, variantRegions.length - 1))], tile.worldx(), tile.worldy());
            TextureRegion r = glow.variants[Mathf.randomSeed(tile.pos(), 0, Math.max(0, glow.variants.length - 1))];
            if(r != null){
                Draw.rect(r, tile.worldx(), tile.worldy());
            }
        }else{
            //Draw.rect(region, tile.worldx(), tile.worldy());
            if(glow.region != null){
                Draw.rect(glow.region, tile.worldx(), tile.worldy());
            }
        }
    }
    boolean eq2(int rx, int ry, Block block){
        return eq(rx, ry, block) && Mathf.randomSeed(Point2.pack(rx, ry)) < 0.5;
    }
    boolean eq(int rx, int ry, Block block){
        World world = Vars.world;
        return rx < world.width() - 1 && ry < world.height() - 1
                && world.tile(rx + 1, ry).block() == block
                && world.tile(rx, ry + 1).block() == block
                && world.tile(rx, ry).block() == block
                && world.tile(rx + 1, ry + 1).block() == block;
    }

    public void drawWalls(){
        World world = Vars.world;
        Draw.fbo(buffer.getTexture(), world.width(), world.height(), Vars.tilesize, Vars.tilesize / 2f);

        Rect r = Core.camera.bounds(tmpRect);

        int minX = Mathf.clamp((int)((r.x / Vars.tilesize) / 16f), 0, caches.length), maxX = Mathf.clamp(Mathf.ceilPositive(((r.x + r.width) / Vars.tilesize) / 16f), 0, caches.length);
        int minY = Mathf.clamp((int)((r.y / Vars.tilesize) / 16f), 0, caches[0].length), maxY = Mathf.clamp(Mathf.ceilPositive(((r.y + r.height) / Vars.tilesize) / 16f), 0, caches[0].length);

        for(int x = minX; x < maxX; x++){
            for(int y = minY; y < maxY; y++){
                AltCacheSprites cache = caches[x][y];
                if(cache != null){
                    cache.render();
                }
            }
        }
    }
    public void drawFloors(){
        Rect r = Core.camera.bounds(tmpRect);

        int minX = Mathf.clamp((int)((r.x / Vars.tilesize) / 16f), 0, floorCaches.length), maxX = Mathf.clamp(Mathf.ceilPositive(((r.x + r.width) / Vars.tilesize) / 16f), 0, floorCaches.length);
        int minY = Mathf.clamp((int)((r.y / Vars.tilesize) / 16f), 0, floorCaches[0].length), maxY = Mathf.clamp(Mathf.ceilPositive(((r.y + r.height) / Vars.tilesize) / 16f), 0, floorCaches[0].length);

        for(int x = minX; x < maxX; x++){
            for(int y = minY; y < maxY; y++){
                AltCacheSprites cache = floorCaches[x][y];
                if(cache != null){
                    cache.render();
                }
            }
        }
    }

    public static class EnviroGlow{
        public TextureRegion region, large;
        public TextureRegion[] variants;
        public boolean floor = false, largeFound = false;
    }
}
