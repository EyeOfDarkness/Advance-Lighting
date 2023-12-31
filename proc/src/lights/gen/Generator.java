package lights.gen;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import lights.gen.LightsAtlas.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;

import java.util.concurrent.*;

import static mindustry.Vars.*;
import static arc.Core.*;

public class Generator {
    public static LightsAtlas atlas;

    private final TaskQueue runs = new TaskQueue();

    public static void main(String[] args){
        new Generator(Fi.get(args[0]));
    }

    private Generator(Fi fetchedDir){
        try{
            ArcNativesLoader.load();
        }catch(Throwable ignored){}

        headless = true;
        app = new MockApplication(){
            @Override
            public void post(Runnable runnable){
                runs.post(runnable);
            }
        };
        files = new MockFiles();
        settings = new Settings();
        Core.atlas = atlas = new LightsAtlas();

        asyncCore = new AsyncCore();
        state = new GameState();
        mods = new Mods();

        content = new ContentLoader();
        content.createBaseContent();
        runs.run();

        var mask = IntSet.with(
            // [Neoplasm]:
            // Neoplasm orange.
            0x9e172cff, 0xe05438ff, 0xf98f4aff,
            // [Serpulo]:
            // Core yellow.
            0xd4816bff, 0xffd37fff,
            // Offense orange.
            0xd06b53ff, 0xffa665ff, 0xcf6a53ff, 0xffa566ff,
            // Support green.
            0x62ae7fff, 0x84f491ff,
            // Specialist purple.
            0x665c9fff, 0xbf92f9ff,
            // [Erekir]:
            // Defense light blue.
            0x8ca9e8ff, 0xd1efffff,
            // Offense brown.
            0xbc5452ff, 0xfeb380ff
        );

        var exec = Threads.executor("Vanilla-Sprite-Processor", OS.cores);
        Queue<Future<Runnable>> tasks = new Queue<>();
        Runnable wait = () -> {
            try{
                while(!tasks.isEmpty()){
                    Runnable result;
                    if((result = tasks.removeFirst().get()) != null) result.run();
                }
            }catch(InterruptedException | ExecutionException e){
                throw new RuntimeException(e);
            }
        };

        fetchedDir.walk(file -> {
            if(file.extEquals("png")){
                tasks.addLast(exec.submit(() -> {
                    var name = file.nameWithoutExtension();
                    var relative = file.parent().absolutePath().substring(fetchedDir.absolutePath().length());
                    if(relative.startsWith("/")) relative = relative.substring(1);
                    relative = "./" + relative + "/";

                    return new LightsRegion(name, relative, new Pixmap(file))::add;
                }));
            }
        });

        wait.run();

        content.init();
        runs.run();
        content.load();
        runs.run();

        Draw.scl = 1f / 4f;
        content.units().each(type -> !type.internal && !type.isHidden(), type -> tasks.addLast(exec.submit(() -> {
            try{
                Unit sample = type.constructor.get();
                Func<Pixmap, Pixmap> outline = i -> i.outline(type.outlineColor, type.outlineRadius);
                Cons2<Pixmap, Pixmap> drawCenter = (base, other) -> base.draw(
                    other,
                    base.width / 2 - other.width / 2,
                    base.height / 2 - other.height / 2,
                    true
                );

                var weapons = type.weapons;
                weapons.each(Weapon::load);
                weapons.removeAll(w -> !w.region.found());

                var image = type.segments > 0 ? conv(type.segmentRegions[0]).pixmap().copy() : outline.get(conv(type.previewRegion).pixmap());

                Func<Weapon, Pixmap> weaponRegion = weapon -> atlas.find(weapon.name + "-preview", weapon.region).pixmap();
                Cons2<Weapon, Pixmap> drawWeapon = (weapon, pixmap) -> {
                    var used = weapon.flipSprite ? pixmap.flipX() : pixmap;
                    image.draw(used,
                        (int)(weapon.x / Draw.scl + image.width / 2f - weapon.region.width / 2f),
                        (int)(-weapon.y / Draw.scl + image.height / 2f - weapon.region.height / 2f),
                        true
                    );

                    if(weapon.flipSprite) used.dispose();
                };

                boolean anyUnder = false;
                if(sample instanceof Crawlc){
                    for(int i = 1; i < type.segments; i++){
                        drawCenter.get(image, conv(type.segmentRegions[i]).pixmap());
                    }
                }

                for(Weapon weapon : weapons.select(w -> w.layerOffset < 0)){
                    var pixmap = outline.get(weaponRegion.get(weapon));
                    drawWeapon.get(weapon, pixmap);
                    pixmap.dispose();

                    anyUnder = true;
                }

                if(anyUnder){
                    var pixmap = outline.get(conv(type.previewRegion).pixmap());
                    image.draw(pixmap, true);
                    pixmap.dispose();
                }

                if(sample instanceof Tankc){
                    var treads = outline.get(conv(type.treadRegion).pixmap());
                    drawCenter.get(image, treads);
                    treads.dispose();

                    image.draw(conv(type.previewRegion).pixmap(), true);
                }

                if(sample instanceof Mechc){
                    drawCenter.get(image, conv(type.baseRegion).pixmap());
                    drawCenter.get(image, conv(type.legRegion).pixmap());

                    var flipped = conv(type.legRegion).pixmap().flipX();
                    drawCenter.get(image, flipped);
                    flipped.dispose();

                    image.draw(conv(type.previewRegion).pixmap(), true);
                }

                for(var weapon : weapons){
                    if(weapon.layerOffset < 0) continue;

                    var pixmap = outline.get(weaponRegion.get(weapon));
                    drawWeapon.get(weapon, pixmap);
                    pixmap.dispose();
                }

                if(type.drawCell){
                    image.draw(conv(type.previewRegion).pixmap(), true);

                    var cell = conv(type.cellRegion).pixmap();
                    /*cell.replace(in -> switch(in){
                        case 0xffffffff -> 0xffa664ff;
                        case 0xdcc6c6ff, 0xdcc5c5ff -> 0xd06b53ff;
                        default -> 0;
                    });*/
                    drawCenter.get(image, cell);
                }

                for(var weapon : weapons){
                    if(weapon.layerOffset < 0) continue;

                    var reg = weaponRegion.get(weapon);
                    var wepReg = weapon.top ? outline.get(reg) : reg;
                    drawWeapon.get(weapon, wepReg);
                    if(weapon.top) wepReg.dispose();

                    if(weapon.cellRegion.found()) {
                        var cell = conv(weapon.cellRegion).pixmap();
                        /*cell.replace(in -> switch(in){
                            case 0xffffffff -> 0xffa664ff;
                            case 0xdcc6c6ff, 0xdcc5c5ff -> 0xd06b53ff;
                            default -> 0;
                        });*/
                        drawWeapon.get(weapon, cell);
                    }
                }

                return new LightsRegion(type.name + "-full", conv(type.region).relative + "icons/", image)::add;
            }catch(Throwable err){
                Log.warn("Skipping unit '@': @", type.name, Strings.getFinalCause(err));
            }

            return null;
        })));

        wait.run();
        atlas.each(reg -> tasks.addLast(exec.submit(() -> {
            var image = reg.pixmap();
            boolean found = false;
            for(int x = 0, width = image.width; x < width; x++){
                for(int y = 0, height = image.height; y < height; y++){
                    int pixel = image.getRaw(x, y);
                    if(!mask.contains(pixel)){
                        if(reg.relative.endsWith("icons/")){
                            image.setRaw(x, y, switch(pixel){
                                case 0xffffffff -> 0xffa664ff;
                                case 0xdcc6c6ff, 0xdcc5c5ff -> 0xd06b53ff;
                                default -> Color.clearRgba;
                            });
                        }else{
                            image.setRaw(x, y, Color.clearRgba);
                        }
                    }else{
                        found = true;
                    }
                }
            }

            if(found) reg.save(false);
            return null;
        })));

        wait.run();
        atlas.dispose();
    }

    public static LightsRegion conv(TextureRegion reg){
        if(!reg.found() || !(reg instanceof LightsRegion r)) throw new IllegalArgumentException("Invalid region");
        return r;
    }
}
