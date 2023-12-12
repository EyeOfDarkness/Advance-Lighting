package lights.gen;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.mock.*;
import arc.util.*;
import arc.util.Log.*;
import lights.gen.GenAtlas.*;
import mindustry.async.*;
import mindustry.core.*;
import mindustry.mod.*;

import static mindustry.Vars.*;
import static arc.Core.*;

public class Generator {
    public static GenAtlas atlas;

    private final TaskQueue runs = new TaskQueue();
    private final Fi fetchedDir;

    public static void main(String[] args){
        new Generator(Fi.get(args[0]));
    }

    private Generator(Fi fetchedDir){
        this.fetchedDir = fetchedDir;

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
        Core.atlas = atlas = new GenAtlas();

        asyncCore = new AsyncCore();
        state = new GameState();
        mods = new Mods();

        content = new ContentLoader();
        content.createBaseContent();

        runs.run();
        addFetched();

        content.init();
        runs.run();
        content.load();
        runs.run();

        atlas.dispose();
    }

    private void addFetched(){
        fetchedDir.walk(file -> {
            if(file.extEquals("png")){
                var name = file.nameWithoutExtension();
                var relative = file.parent().absolutePath().substring(fetchedDir.absolutePath().length());
                if(relative.startsWith("/")) relative = relative.substring(1);

                atlas.addRegion(name, new GenRegion(name, relative, new Pixmap(file)));
            }
        });
    }
}
