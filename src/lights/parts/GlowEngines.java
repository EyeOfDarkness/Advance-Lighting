package lights.parts;

import arc.*;
import arc.struct.*;
import lights.*;
import lights.graphics.*;
import mindustry.gen.*;
import mindustry.type.UnitType.*;

public class GlowEngines extends UnitEngine{
    public Seq<UnitEngine> engines = new Seq<>();

    @Override
    public void draw(Unit unit){
        if(Core.batch == AdvanceLighting.batch){
            AltLightBatch b = AdvanceLighting.batch;
            b.setGlow(true);
            for(UnitEngine e : engines){
                e.draw(unit);
            }
            b.setGlow(false);
        }else{
            for(UnitEngine e : engines){
                e.draw(unit);
            }
        }
    }
}
