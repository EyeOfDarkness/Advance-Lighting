package lights.gen;

import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import lights.gen.LightsAtlas.*;

import static lights.gen.Generator.*;

public class LightsAtlas extends TextureAtlas implements Eachable<LightsRegion>{
    public LightsRegion clear;
    private final OrderedMap<String, LightsRegion> regions = new OrderedMap<>();

    public LightsAtlas(){
        regions.orderedKeys().ordered = false;
        clear = addRegion("clear", new LightsRegion("clear", ".", Pixmaps.blankPixmap()));
    }

    @Override
    public LightsRegion addRegion(String name, TextureRegion textureRegion){
        if(!(textureRegion instanceof LightsRegion reg)){
            throw new IllegalArgumentException("Must be LightsRegion");
        }

        regions.put(name, reg);
        return reg;
    }

    @Override
    public LightsRegion addRegion(String name, Texture texture, int x, int y, int width, int height){
        Pixmap pixmap = texture.getTextureData().getPixmap();
        LightsRegion reg = new LightsRegion(name, ".", Pixmaps.crop(pixmap, x, y, width, height));

        regions.put(name, reg);
        return reg;
    }

    @Override
    public LightsRegion find(String name){
        if(!regions.containsKey(name)){
            return new LightsRegion(name, ".", null);
        }

        return regions.get(name);
    }

    @Override
    public LightsRegion find(String name, String def){
        return regions.get(name, find(def));
    }

    @Override
    public LightsRegion find(String name, TextureRegion def){
        if(!(def instanceof LightsRegion reg)){
            throw new IllegalArgumentException("Must be LightsRegion");
        }

        return regions.get(name, reg);
    }

    @Override
    public boolean has(String s){
        return regions.containsKey(s);
    }

    @Override
    public void dispose(){
        for(var reg : regions.values()){
            if(reg.pixmap != null) reg.pixmap.dispose();
        }

        regions.clear();
    }

    @Override
    public void each(Cons<? super LightsRegion> cons){
        for(var reg : regions.values()){
            cons.get(reg);
        }
    }

    public void save(){
        for(var reg : regions.values()){
            reg.save(false);
        }
    }

    public static class LightsRegion extends AtlasRegion{
        private final String relative;
        private final Pixmap pixmap;

        public LightsRegion(String name, String relative, Pixmap pixmap){
            this.name = name;
            this.pixmap = pixmap;
            this.relative = relative;

            if(pixmap != null){
                width = pixmap.width;
                height = pixmap.height;
            }

            u = v = 0f;
            u2 = v2 = 1f;
        }

        @Override
        public boolean found(){
            return pixmap != null;
        }

        public Pixmap pixmap(){
            if(!found()) throw new IllegalArgumentException("Region does not exist: " + name);
            return pixmap;
        }

        public void save(){
            save(true);
        }

        public void save(boolean add){
            if(!found()) throw new IllegalArgumentException("Cannot save an invalid region: " + name);
            Fi.get(relative).child(name + ".png").writePng(pixmap);

            if(add) atlas.addRegion(name, this);
        }
    }
}
