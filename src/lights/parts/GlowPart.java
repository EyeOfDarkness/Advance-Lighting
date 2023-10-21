package lights.parts;

import arc.*;
import arc.graphics.g2d.*;
import lights.*;
import lights.graphics.*;
import mindustry.entities.part.*;

public class GlowPart extends DrawPart{
    TextureRegion region;

    public GlowPart(TextureRegion region){
        this.region = region;
    }

    @Override
    public void draw(PartParams params){
        if(Core.batch == AdvanceLighting.batch){
            AltLightBatch b = AdvanceLighting.batch;
            b.setGlow(true);

            Draw.rect(region, params.x, params.y, params.rotation - 90f);

            b.setGlow(false);
        }
    }

    @Override
    public void load(String name){}
}
