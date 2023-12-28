package lights.gen;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import lights.gen.LightsAtlas.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.mod.*;

import java.util.concurrent.*;

import static mindustry.Vars.*;
import static arc.Core.*;

public class Generator {
    public static LightsAtlas atlas;

    private final TaskQueue runs = new TaskQueue();

    public static void main(String[] args) throws Exception{
        new Generator(Fi.get(args[0]));
    }

    private Generator(Fi fetchedDir) throws Exception{
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
            0xd06b53ff, 0xffa665ff,
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
        fetchedDir.walk(file -> {
            if(file.extEquals("png")){
                tasks.addLast(exec.submit(() -> {
                    var name = file.nameWithoutExtension();
                    var relative = file.parent().absolutePath().substring(fetchedDir.absolutePath().length());
                    if(relative.startsWith("/")) relative = relative.substring(1);

                    var image = new Pixmap(file);

                    boolean found = false;
                    for(int x = 0, width = image.width; x < width; x++){
                        for(int y = 0, height = image.height; y < height; y++){
                            int pixel = image.getRaw(x, y);
                            if(mask.contains(pixel)){
                                found = true;
                            }else{
                                image.setRaw(x, y, (Color.blackRgba & 0xffffff00) | Color.ai(pixel));
                            }
                        }
                    }

                    if(found){
                        var rel = relative;
                        return () -> atlas.addRegion(name, new LightsRegion(name, rel, image));
                    }else{
                        image.dispose();
                        return () -> {};
                    }
                }));
            }
        });

        while(!tasks.isEmpty()){
            var task = tasks.removeFirst();
            task.get().run();
        }

        content.init();
        runs.run();
        content.load();
        runs.run();

        atlas.each(reg -> {
            var image = reg.pixmap();
            for(int x = 0, width = image.width; x < width; x++){
                for(int y = 0, height = image.height; y < height; y++){
                    if(!mask.contains(image.getRaw(x, y))){
                        image.setRaw(x, y, Color.clearRgba);
                    }
                }
            }
        });
        atlas.save();
        atlas.dispose();
    }
}
