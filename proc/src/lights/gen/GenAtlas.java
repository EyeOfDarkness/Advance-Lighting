package lights.gen;

import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;

import static lights.gen.Generator.*;

public class GenAtlas extends TextureAtlas{
    public GenRegion clear;
    private final OrderedMap<String, GenRegion> regions = new OrderedMap<>();

    public GenAtlas(){
        regions.orderedKeys().ordered = false;
        clear = addRegion("clear", new GenRegion("clear", ".", Pixmaps.blankPixmap()));
    }

    @Override
    public GenRegion addRegion(String name, TextureRegion textureRegion){
        GenRegion reg = (GenRegion)textureRegion;

        regions.put(name, reg);
        return reg;
    }

    @Override
    public GenRegion addRegion(String name, Texture texture, int x, int y, int width, int height){
        Pixmap pixmap = texture.getTextureData().getPixmap();
        GenRegion reg = new GenRegion(name, ".", Pixmaps.crop(pixmap, x, y, width, height));

        regions.put(name, reg);
        return reg;
    }

    @Override
    public GenRegion find(String name){
        if(!regions.containsKey(name)){
            return new GenRegion(name, ".", null);
        }

        return regions.get(name);
    }

    @Override
    public GenRegion find(String name, String def){
        return regions.get(name, find(def));
    }

    @Override
    public GenRegion find(String name, TextureRegion def){
        return regions.get(name, (GenRegion)def);
    }

    @Override
    public boolean has(String s){
        return regions.containsKey(s);
    }

    @Override
    public void dispose(){
        for(GenRegion pix : regions.values()){
            if(pix.pixmap != null) pix.pixmap.dispose();
        }

        regions.clear();
    }

    public static class GenRegion extends AtlasRegion{
        private final String relative;
        private final Pixmap pixmap;

        public GenRegion(String name, String relative, Pixmap pixmap){
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
