package lights.parts;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import lights.*;
import lights.graphics.*;
import mindustry.entities.part.*;

public class GlowHeatPart extends DrawPart{
    TextureRegion[] regions;
    PartProgress heatProg;

    public GlowHeatPart(RegionPart part, TextureRegion right, TextureRegion left){
        if(left == null){
            regions = new TextureRegion[]{right};
        }else{
            regions = new TextureRegion[]{right, left};
        }
        heatProg = part.heatProgress;
    }
    public GlowHeatPart(PartProgress prog, TextureRegion right, TextureRegion left){
        if(left == null){
            regions = new TextureRegion[]{right};
        }else{
            regions = new TextureRegion[]{right, left};
        }
        heatProg = prog;
    }

    @Override
    public void draw(PartParams params){
        if(Core.batch == AdvanceLighting.batch){
            AltLightBatch b = AdvanceLighting.batch;
            b.setGlow(true);

            //Draw.rect(region, params.x, params.y, w, h, params.rotation - 90f);

            int i = (params.sideOverride == -1 || !turretShading || regions.length == 1) ? 0 : params.sideOverride;

            TextureRegion region = regions[Mathf.clamp(i, 0, 1)];

            float w = region.width * Draw.scl * Draw.xscl * (i == 0 ? 1 : -1);
            float h = region.height * Draw.scl * Draw.yscl;

            float hprog = heatProg.getClamp(params);
            Draw.color(Color.white, hprog);
            Draw.rect(region, params.x, params.y, w, h, params.rotation - 90f);
            Draw.color();

            b.setGlow(false);
        }
    }

    @Override
    public void load(String name){}
}
