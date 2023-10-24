package lights.parts;

import arc.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import lights.*;
import lights.graphics.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.draw.*;

public class DrawGlowWrapper extends DrawBlock{
    DrawBlock source;

    public DrawGlowWrapper(DrawBlock source){
        this.source = source;
    }

    @Override
    public void getRegionsToOutline(Block block, Seq<TextureRegion> out){
        source.getRegionsToOutline(block, out);
        iconOverride = source.iconOverride;
    }

    @Override
    public void draw(Building build){
        if(Core.batch == AdvanceLighting.batch){
            AltLightBatch b = AdvanceLighting.batch;

            b.setGlow(true);
            source.draw(build);
            b.setGlow(false);
        }else{
            source.draw(build);
        }
    }

    @Override
    public void drawLight(Building build){
        source.drawLight(build);
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> list){
        source.drawPlan(block, plan, list);
    }

    @Override
    public void load(Block block){
        source.load(block);
    }

    @Override
    public TextureRegion[] icons(Block block){
        return source.icons(block);
    }

    @Override
    public GenericCrafter expectCrafter(Block block){
        return source.expectCrafter(block);
    }
}
