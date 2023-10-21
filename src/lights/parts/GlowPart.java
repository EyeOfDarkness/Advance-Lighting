package lights.parts;

import arc.*;
import arc.graphics.g2d.*;
import lights.*;
import lights.graphics.*;
import mindustry.entities.part.*;

public class GlowPart extends DrawPart{
    TextureRegion region;
    public boolean mirror = false;

    public GlowPart(TextureRegion region){
        this.region = region;
    }

    @Override
    public void draw(PartParams params){
        if(Core.batch == AdvanceLighting.batch){
            AltLightBatch b = AdvanceLighting.batch;
            b.setGlow(true);

            float w = region.width * Draw.scl * Draw.xscl * (mirror ? -1f : 1f);
            float h = region.height * Draw.scl * Draw.yscl;

            Draw.rect(region, params.x, params.y, w, h, params.rotation - 90f);

            b.setGlow(false);
        }
    }

    @Override
    public void load(String name){}
}
