package lights.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import lights.*;
import lights.graphics.ALShaders.*;
import mindustry.graphics.*;

import java.util.*;
import java.util.concurrent.*;

public class AltLightBatch extends SpriteBatch{
    static ForkJoinHolder commonPool;

    LightRequest[] requests = new LightRequest[maxRequests];
    float[] requestZ = new float[maxRequests];
    Seq<CacheRequest> cacheRequests = new Seq<>(false, 2048, CacheRequest.class);
    FloatSeq uncapture = new FloatSeq();
    int calls = 0;
    int cacheCalls = 0;
    boolean flushing;
    boolean auto, layerGlow;
    boolean liquidMode = false;
    float liquidGlow = -1f;

    boolean glow = false;
    boolean glowTexture = false;
    float glowAlpha = 1f;
    Color blackAlpha = new Color();
    float blackAlphaBits = Color.blackFloatBits;

    public boolean cacheMode = false;
    public float cacheLayer = Layer.blockUnder;

    float excludeMinZ, excludeMaxZ;

    Batch lastBatch;

    final static int maxRequests = 4096;
    final static float[] tmpVert = new float[24];
    final static Color tmpColor = new Color();

    public AltLightBatch(){
        super(maxRequests, createShaderL());

        for(int i = 0; i < maxRequests; i++){
            //requests.add(new LightRequest());
            requests[i] = new LightRequest();
        }
        for(int i = 0; i < 2048; i++){
            //cacheRequests
            cacheRequests.add(new CacheRequest());
        }

        if(multithreaded){
            try{
                commonPool = new ForkJoinHolder();
            }catch(Throwable th){
                Log.err(th);
                multithreaded = false;
            }
        }
    }

    public void setLiquidGlow(float amount){
        liquidGlow = amount;
    }
    public void setLiquidMode(boolean liq){
        liquidMode = liq;
    }
    public void setGlow(boolean glow){
        this.glow = glow;
    }
    public boolean getGlow(){
        return glow;
    }

    public void begin(){
        lastBatch = Core.batch;
        Draw.flush();
        Mat proj = Draw.proj(), trans = Draw.trans();
        Core.batch = this;
        Draw.proj(proj);
        Draw.trans(trans);

        calls = 0;
    }
    public void end(){
        uncapture.clear();

        Draw.reset();
        Draw.flush();
        
        calls = 0;

        Core.batch = lastBatch;
    }

    public void setExcludeLayer(float min, float max){
        excludeMinZ = min;
        excludeMaxZ = max;
    }
    public void setExcludeLayer(){
        excludeMinZ = excludeMaxZ = -100f;
    }

    public void setAuto(float z, boolean auto){
        z(z);
        LightRequest rq = obtain();
        //rq.z = z;
        //rq.texture = null;
        rq.action = (byte)(1 | ((auto ? 1 : 0) << 1));
    }
    public void setLayerGlow(float z, boolean auto){
        z(z);
        LightRequest rq = obtain();
        //rq.z = z;
        rq.action = (byte)(1 | ((auto ? 1 : 0) << 2));
    }

    public void addUncapture(float lowZ, float highZ){
        uncapture.add(lowZ, highZ);
    }
    boolean invalid(){
        if(flushing) return false;
        if(z >= excludeMinZ && z <= excludeMaxZ){
            return true;
        }

        int s = uncapture.size;
        float[] uc = uncapture.items;
        for(int i = 0; i < s; i += 2){
            if(z >= uc[i] && z <= uc[i + 1]) return true;
        }

        return false;
    }

    void updateGlowAlpha(){
        Color c = color;

        float lum = Mathf.curve((c.r + c.g + c.b) / 3f, 0.2f, 0.6f);
        float sat = Mathf.curve(Math.max(c.r, Math.max(c.g, c.b)) - Math.min(c.r, Math.min(c.g, c.b)), 0.1f, 0.3f);
        float v = Mathf.clamp(sat + lum);

        glowAlpha = Mathf.curve(c.a, 0.7f * (1f - v), 1f);
    }

    @Override
    protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
        if(flushing){
            float c = colorPacked;
            colorPacked = glow ? colorPacked : blackAlphaBits;

            super.draw(region, x, y, originX, originY, width, height, rotation);

            colorPacked = c;
            return;
        }

        if(cacheMode && color.a >= 0.9f && !glow && z >= excludeMinZ && z <= excludeMaxZ){
            //boolean autoGlow = AdvanceLighting.autoGlowRegions.contains(region);
            CacheRequest cr = obtainCache();
            cr.set(region, x, y, originX, originY, width, height, rotation);
        }
        if(invalid() || (glowAlpha <= 0f && !glow && !liquidMode)) return;

        LightRequest rq = obtain();
        float[] vertices = rq.vertices;

        float color = (glow || AdvanceLighting.autoGlowRegions.contains(region)) ? this.colorPacked : blackAlphaBits;
        float mixColor = this.mixColorPacked;

        if(!glow && (liquidMode || AdvanceLighting.liquidRegions.contains(region))){
            float v = liquidGlow < 0 ? AdvanceLighting.glowingLiquidColorsFunc.get(this.color) : liquidGlow;
            tmpColor.set(blackAlpha).lerp(this.color, v);
            color = tmpColor.toFloatBits();
        }

        TextureRegion rep;
        if((rep = AdvanceLighting.replace.get(region)) != null){
            region = rep;
        }

        rq.texture = region.texture;
        rq.color = colorPacked;
        rq.mixColor = mixColorPacked;
        //rq.z = z;
        //rq.blend = blending;

        if(!Mathf.zero(rotation)){
            float worldOriginX = x + originX;
            float worldOriginY = y + originY;
            float fx = -originX;
            float fy = -originY;
            float fx2 = width - originX;
            float fy2 = height - originY;

            float cos = Mathf.cosDeg(rotation);
            float sin = Mathf.sinDeg(rotation);

            float x1 = cos * fx - sin * fy + worldOriginX;
            float y1 = sin * fx + cos * fy + worldOriginY;
            float x2 = cos * fx - sin * fy2 + worldOriginX;
            float y2 = sin * fx + cos * fy2 + worldOriginY;
            float x3 = cos * fx2 - sin * fy2 + worldOriginX;
            float y3 = sin * fx2 + cos * fy2 + worldOriginY;
            float x4 = x1 + (x3 - x2);
            float y4 = y3 - (y2 - y1);

            float u = region.u;
            float v = region.v2;
            float u2 = region.u2;
            float v2 = region.v;

            vertices[0] = x1;
            vertices[1] = y1;
            vertices[2] = color;
            vertices[3] = u;
            vertices[4] = v;
            vertices[5] = mixColor;

            vertices[6] = x2;
            vertices[7] = y2;
            vertices[8] = color;
            vertices[9] = u;
            vertices[10] = v2;
            vertices[11] = mixColor;

            vertices[12] = x3;
            vertices[13] = y3;
            vertices[14] = color;
            vertices[15] = u2;
            vertices[16] = v2;
            vertices[17] = mixColor;

            vertices[18] = x4;
            vertices[19] = y4;
            vertices[20] = color;
            vertices[21] = u2;
            vertices[22] = v;
            vertices[23] = mixColor;
        }else{
            float fx2 = x + width;
            float fy2 = y + height;
            float u = region.u;
            float v = region.v2;
            float u2 = region.u2;
            float v2 = region.v;

            vertices[0] = x;
            vertices[1] = y;
            vertices[2] = color;
            vertices[3] = u;
            vertices[4] = v;
            vertices[5] = mixColor;

            vertices[6] = x;
            vertices[7] = fy2;
            vertices[8] = color;
            vertices[9] = u;
            vertices[10] = v2;
            vertices[11] = mixColor;

            vertices[12] = fx2;
            vertices[13] = fy2;
            vertices[14] = color;
            vertices[15] = u2;
            vertices[16] = v2;
            vertices[17] = mixColor;

            vertices[18] = fx2;
            vertices[19] = y;
            vertices[20] = color;
            vertices[21] = u2;
            vertices[22] = v;
            vertices[23] = mixColor;
        }

        //Pain and Misery
        if(!glowTexture){
            glowTexture = true;
            TextureRegion gc;
            boolean lg = glow;
            glow = true;
            if((gc = AdvanceLighting.glowEquiv.get(region)) != null){
                float dw = (width / region.width) * gc.width;
                float dh = (height / region.height) * gc.height;
                
                float dox = (dw - width) / 2f;
                float doy = (dh - height) / 2f;

                draw(gc, x - dox, y - doy, originX + dox, originY + doy, dw, dh, rotation);
            }
            glow = lg;
            glowTexture = false;
        }
    }
    
    public void superDraw(Texture texture, float[] spriteVertices, int count){
        super.draw(texture, spriteVertices, 0, count);
    }

    @Override
    protected void draw(Texture texture, float[] spriteVertices, int offset, int count){
        /*
        if(flushing){
            super.draw(texture, spriteVertices, offset, count);
            return;
        }
        */
        if(flushing){
            float[] tmp = tmpVert;
            System.arraycopy(spriteVertices, offset, tmp, 0, 24);

            if(!glow){
                for(int i = 2; i < 24; i += 6){
                    tmp[i] = blackAlphaBits;
                }
            }
            superDraw(texture, tmp, 24);
            return;
        }
        if(invalid() || (glowAlpha <= 0f && !glow)) return;

        LightRequest rq = obtain();
        rq.texture = texture;
        rq.color = colorPacked;
        rq.mixColor = mixColorPacked;
        //rq.z = z;
        //rq.blend = blending;
        float[] vertices = rq.vertices;

        //System.arraycopy(spriteVertices, 0, rq.vertices, 0, 24);
        System.arraycopy(spriteVertices, offset, rq.vertices, 0, 24);

        /*
        if(!glow){
            float black = Color.blackFloatBits;
            for(int i = 3; i < 24; i += 6){
                vertices[i] = black;
            }
        }
         */
        float color = (glow || AdvanceLighting.uvAutoGlowRegions.contains(ALStructs.uv(texture, vertices[3], vertices[4]))) ? colorPacked : blackAlphaBits;
        for(int i = 2; i < 24; i += 6){
            vertices[i] = color;
        }

        if(!glowTexture){
            TextureRegion r;
            float su = spriteVertices[3], sv = spriteVertices[4];
            if((r = AdvanceLighting.uvGlowRegions.get(ALStructs.uv(texture, su, sv))) != null){
                boolean lg = glow;
                glowTexture = true;
                glow = true;

                System.arraycopy(spriteVertices, 0, tmpVert, 0, 24);
                float u = r.u, v = r.v, u2 = r.u2, v2 = r.v2;

                tmpVert[3] = u;
                tmpVert[4] = v;
                tmpVert[9] = u;
                tmpVert[10] = v2;
                tmpVert[15] = u2;
                tmpVert[16] = v2;
                tmpVert[21] = u2;
                tmpVert[22] = v;

                draw(r.texture, tmpVert, 0, 24);

                glow = lg;
                glowTexture = false;
            }
        }
    }

    @Override
    protected void draw(Runnable request){
        if(flushing){
            request.run();
            return;
        }
        LightRequest r = obtain();
        r.run = request;
        //r.z = z;
    }

    @Override
    protected void flush(){
        if(!flushing){
            if(cacheCalls > 0){
                Draw.draw(cacheLayer, () -> {
                    for(int i = 0; i < cacheCalls; i++){
                        CacheRequest cr = cacheRequests.items[i];
                        //draw(cr.texture, cr.vertices, 0, 24);
                        superDraw(cr.texture, cr.vertices, 24);
                    }
                    cacheCalls = 0;
                });
            }

            flushing = true;

            sortRequestsMain();

            //int size = requests.size;

            //requests.sort();

            //requests2
            for(int i = 0; i < calls; i++){
                LightRequest r = requests[i];
                if(blending != r.blend && r.action == 0) setBlending(r.blend);

                if(r.texture != null){
                    if(auto) r.convertAutoColor();
                    if(layerGlow) r.convertGlow();
                    //if(blending != r.blend) setBlending(r.blend);
                    superDraw(r.texture, r.vertices, 24);
                }else if(r.run != null){
                    r.run.run();
                }else{
                    byte action = r.action;
                    if(action != 0){
                        //auto = action != 1;
                        auto = (action & 2) != 0;
                        layerGlow = (action & 4) != 0;
                    }
                }
            }
            super.flush();
            if(blending != Blending.normal){
                setBlending(Blending.normal);
            }

            flushing = false;
        }else{
            super.flush();
        }
    }

    @Override
    protected void setShader(Shader shader, boolean apply){
        if(!flushing) return;

        Shader alt;
        if(shader != null && (alt = AdvanceLighting.validShaders.get(shader)) != null){
            if(alt instanceof UnapplyableShader us) us.preapply();
            super.setShader(alt, apply);
        }else if(customShader != null){
            Shader last = customShader;
            super.setShader(null, apply);
            if(last instanceof UnapplyableShader us){
                us.unapply();
            }
        }
    }

    @Override
    protected void setColor(Color tint){
        super.setColor(tint);

        blackAlpha.set(0f, 0f, 0f, tint.a);
        blackAlphaBits = blackAlpha.toFloatBits();

        updateGlowAlpha();
    }

    @Override
    protected void setColor(float r, float g, float b, float a){
        super.setColor(r, g, b, a);
        blackAlpha.set(0f, 0f, 0f, a);
        blackAlphaBits = blackAlpha.toFloatBits();

        updateGlowAlpha();
    }

    @Override
    protected void setPackedColor(float packedColor){
        super.setPackedColor(packedColor);
        blackAlpha.abgr8888(packedColor);
        blackAlpha.set(0f, 0f, 0f);
        blackAlphaBits = blackAlpha.toFloatBits();

        updateGlowAlpha();
    }

    @Override
    protected void setBlending(Blending blending){
        if(flushing){
            if(blending != this.blending) super.flush();
        }else{
            glow = blending == Blending.additive;
        }
        this.blending = blending;
    }

    LightRequest obtain(){
        if(calls >= requests.length){
            expandRequests();
        }
        //calls++;
        //requests.size = calls;
        LightRequest r = requests[calls];
        r.texture = null;
        r.run = null;
        r.action = 0;
        requestZ[calls] = r.z = z;
        r.blend = blending;
        calls++;
        return r;
    }

    void expandRequests(){
        //final DrawRequest[] requests = this.requests, newRequests = new DrawRequest[requests.length * 7 / 4];
        //System.arraycopy(requests, 0, newRequests, 0, Math.min(newRequests.length, requests.length));
        final LightRequest[] requests = this.requests, newRequests = new LightRequest[requests.length * 7 / 4];
        System.arraycopy(requests, 0, newRequests, 0, Math.min(newRequests.length, requests.length));
        for(int i = requests.length; i < newRequests.length; i++){
            newRequests[i] = new LightRequest();
        }
        this.requests = newRequests;
        requestZ = Arrays.copyOf(requestZ, newRequests.length);
    }

    CacheRequest obtainCache(){
        if(cacheCalls >= cacheRequests.size){
            CacheRequest cr = new CacheRequest();
            cacheRequests.add(cr);
            cacheCalls++;
            return cr;
        }

        CacheRequest cr = cacheRequests.get(cacheCalls);
        cacheCalls++;
        return cr;
    }

    static Shader createShaderL(){
        return new Shader("""
                attribute vec4 a_position;
                attribute vec4 a_color;
                attribute vec2 a_texCoord0;
                attribute vec4 a_mix_color;
                uniform mat4 u_projTrans;
                varying vec4 v_color;
                varying vec4 v_mix_color;
                varying vec2 v_texCoords;
                
                void main(){
                    v_color = a_color;
                    v_color.a = v_color.a * (255.0/254.0);
                    v_mix_color = a_mix_color;
                    v_mix_color.a *= (255.0/254.0);
                    v_texCoords = a_texCoord0;
                    gl_Position = u_projTrans * a_position;
                }
                """, """
                varying lowp vec4 v_color;
                varying lowp vec4 v_mix_color;
                varying vec2 v_texCoords;
                uniform sampler2D u_texture;
                
                float curve(float f, float from, float to){
                    if(f < from){
                        return 0.0;
                    }else if(f > to){
                        return 1.0;
                    }
                    return (f - from) / (to - from);
                }
                float clamp(float value){
                    return max(min(value, 1.0), 0.0);
                }
                
                void main(){
                    vec4 c = texture2D(u_texture, v_texCoords);
                    //vec4 fc = c * v_color;
                    vec3 fc = mix(c.rgb, v_mix_color.rgb, v_mix_color.a) * v_color.rgb;
                    
                    float lum = curve((fc.r + fc.g + fc.b) / 3.0, 0.2, 0.6);
                    float sat = curve(max(fc.r, max(fc.g, fc.g)) - min(fc.r, min(fc.g, fc.b)), 0.1, 0.3);
                    float v = clamp(sat + lum);
                    
                    float ca = c.a * v_color.a;
                    
                    float alpha = curve(ca, 0.7 * (1.0 - v), 1.0);
                    gl_FragColor = vec4(mix(c.rgb, v_mix_color.rgb, v_mix_color.a) * v_color.rgb, alpha);
                }
                """);
    }

    boolean multithreaded = (Core.app.getVersion() >= 21 && !Core.app.isIOS()) || Core.app.isDesktop();
    LightRequest[] copy = new LightRequest[0];
    int[] contiguous = new int[2048], contiguousCopy = new int[2048];
    int[] locs = new int[contiguous.length];
    void sortRequestsMain(){
        if(multithreaded){
            sortRequestsThreaded();
        }else{
            sortRequests();
        }
    }

    void sortRequestsThreaded(){
        final int numRequests = calls;
        if(copy.length < numRequests) copy = new LightRequest[numRequests + (numRequests >> 3)];
        final LightRequest[] items = requests, itemCopy = copy;
        final float[] itemZ = requestZ;
        final Future<?> initTask = commonPool.pool.submit(() -> System.arraycopy(items, 0, itemCopy, 0, numRequests));

        int[] contiguous = this.contiguous;
        int ci = 0, cl = contiguous.length;
        float z = itemZ[0];
        int startI = 0;
        // Point3: <z, index, length>
        for(int i = 1; i < numRequests; i++){
            if(itemZ[i] != z){ // if contiguous section should end
                contiguous[ci] = Float.floatToRawIntBits(z + 16f);
                contiguous[ci + 1] = startI;
                contiguous[ci + 2] = i - startI;
                ci += 3;
                if(ci + 3 > cl){
                    contiguous = Arrays.copyOf(contiguous, cl <<= 1);
                }
                z = itemZ[startI = i];
            }
        }
        contiguous[ci] = Float.floatToRawIntBits(z + 16f);
        contiguous[ci + 1] = startI;
        contiguous[ci + 2] = numRequests - startI;
        this.contiguous = contiguous;

        final int L = (ci / 3) + 1;

        if(contiguousCopy.length < contiguous.length) this.contiguousCopy = new int[contiguous.length];

        final int[] sorted = BasicCountingSort.countingSortMapMT(contiguous, contiguousCopy, L);

        if(locs.length < L + 1) locs = new int[L + L / 10];
        final int[] locs = this.locs;
        for(int i = 0; i < L; i++){
            locs[i + 1] = locs[i] + sorted[i * 3 + 2];
        }
        try{
            initTask.get();
        }catch(Exception ignored){
            System.arraycopy(items, 0, itemCopy, 0, numRequests);
        }
        PopulateTask.tasks = sorted;
        PopulateTask.src = itemCopy;
        PopulateTask.dest = items;
        PopulateTask.locs = locs;
        commonPool.pool.invoke(new PopulateTask(0, L));
    }

    void sortRequests(){
        final int numRequests = calls;
        if(copy.length < numRequests) copy = new LightRequest[numRequests + (numRequests >> 3)];
        final LightRequest[] items = copy;
        //final LightRequest[] itemR = requests;
        final float[] itemZ = requestZ;
        System.arraycopy(requests, 0, items, 0, numRequests);
        int[] contiguous = this.contiguous;
        int ci = 0, cl = contiguous.length;
        float z = itemZ[0];
        int startI = 0;

        for(int i = 1; i < numRequests; i++){
            //if(itemR[i] == null) break;
            if(itemZ[i] != z){
                contiguous[ci] = Float.floatToRawIntBits(z + 16f);
                contiguous[ci + 1] = startI;
                contiguous[ci + 2] = i - startI;
                ci += 3;
                if(ci + 3 > cl){
                    contiguous = Arrays.copyOf(contiguous, cl <<= 1);
                }
                //z = itemZ[startI = i];
                z = (itemZ[startI = i]);
            }
        }
        contiguous[ci] = Float.floatToRawIntBits(z + 16f);
        contiguous[ci + 1] = startI;
        contiguous[ci + 2] = numRequests - startI;
        this.contiguous = contiguous;

        final int L = (ci / 3) + 1;

        if(contiguousCopy.length < contiguous.length) contiguousCopy = new int[contiguous.length];

        final int[] sorted = BasicCountingSort.countingSortMap(contiguous, contiguousCopy, L);

        int ptr = 0;
        final LightRequest[] dest = requests;
        for(int i = 0; i < L * 3; i += 3){
            final int pos = sorted[i + 1], length = sorted[i + 2];
            if(length < 10){
                final int end = pos + length;
                for(int sj = pos, dj = ptr; sj < end; sj++, dj++){
                    dest[dj] = items[sj];
                }
            }else System.arraycopy(items, pos, dest, ptr, Math.min(length, dest.length - ptr));
            ptr += length;
        }
    }

    static class BasicCountingSort{
        private static final int processors = Runtime.getRuntime().availableProcessors() * 8;

        static int[] locs = new int[100];
        static final int[][] locses = new int[processors][100];

        static final IntIntMap[] countses = new IntIntMap[processors];
        private static Point2[] entries = new Point2[100];

        private static int[] entries3 = new int[300], entries3a = new int[300];
        private static Integer[] entriesBacking = new Integer[100];

        private static final CountingSortTask[] tasks = new CountingSortTask[processors];
        private static final CountingSortTask2[] task2s = new CountingSortTask2[processors];
        private static final Future<?>[] futures = new Future<?>[processors];

        static{
            for(int i = 0; i < countses.length; i++) countses[i] = new IntIntMap();
            for(int i = 0; i < entries.length; i++) entries[i] = new Point2();

            for(int i = 0; i < processors; i++){
                tasks[i] = new CountingSortTask();
                task2s[i] = new CountingSortTask2();
            }
        }

        static class CountingSortTask implements Runnable{
            static int[] arr;
            int start, end, id;

            public void set(int start, int end, int id){
                this.start = start;
                this.end = end;
                this.id = id;
            }

            @Override
            public void run(){
                final int id = this.id, start = this.start, end = this.end;
                int[] locs = locses[id];
                final int[] arr = CountingSortTask.arr;
                final IntIntMap counts = countses[id];
                counts.clear();
                int unique = 0;
                for(int i = start; i < end; i++){
                    int loc = counts.getOrPut(arr[i * 3], unique);
                    arr[i * 3] = loc;
                    if(loc == unique){
                        if(unique >= locs.length){
                            locs = Arrays.copyOf(locs, unique * 3 / 2);
                        }
                        locs[unique++] = 1;
                    }else{
                        locs[loc]++;
                    }
                }
                locses[id] = locs;
            }
        }

        static class CountingSortTask2 implements Runnable{
            static int[] src, dest;
            int start, end, id;

            public void set(int start, int end, int id){
                this.start = start;
                this.end = end;
                this.id = id;
            }

            @Override
            public void run(){
                final int start = this.start, end = this.end;
                final int[] locs = locses[id];
                final int[] src = CountingSortTask2.src, dest = CountingSortTask2.dest;
                for(int i = end - 1, i3 = i * 3; i >= start; i--, i3 -= 3){
                    final int destPos = --locs[src[i3]] * 3;
                    dest[destPos] = src[i3];
                    dest[destPos + 1] = src[i3 + 1];
                    dest[destPos + 2] = src[i3 + 2];
                }
            }
        }

        static int[] countingSortMapMT(final int[] arr, final int[] swap, final int end){
            final IntIntMap[] countses = BasicCountingSort.countses;
            final int[][] locs = BasicCountingSort.locses;
            final int threads = Math.min(processors, (end + 4095) / 4096); // 4096 Point3s to process per thread
            final int thread_size = end / threads + 1;
            final CountingSortTask[] tasks = BasicCountingSort.tasks;
            final CountingSortTask2[] task2s = BasicCountingSort.task2s;
            final Future<?>[] futures = BasicCountingSort.futures;
            CountingSortTask.arr = CountingSortTask2.src = arr;
            CountingSortTask2.dest = swap;

            for(int s = 0, thread = 0; thread < threads; thread++, s += thread_size){
                CountingSortTask task = tasks[thread];
                final int stop = Math.min(s + thread_size, end);
                task.set(s, stop, thread);
                task2s[thread].set(s, stop, thread);
                futures[thread] = commonPool.pool.submit(task);
            }

            int unique = 0;
            for(int i = 0; i < threads; i++){
                try{
                    futures[i].get();
                }catch(ExecutionException | InterruptedException e){
                    commonPool.pool.execute(tasks[i]);
                }
                unique += countses[i].size;
            }

            final int L = unique;
            if(entriesBacking.length < L){
                entriesBacking = new Integer[L * 3 / 2];
                entries3 = new int[L * 3 * 3 / 2];
                entries3a = new int[L * 3 * 3 / 2];
            }
            final int[] entries = BasicCountingSort.entries3, entries3a = BasicCountingSort.entries3a;
            final Integer[] entriesBacking = BasicCountingSort.entriesBacking;
            int j = 0;
            for(int i = 0; i < threads; i++){
                if(countses[i].size == 0) continue;
                final IntIntMap.Entries countEntries = countses[i].entries();
                final IntIntMap.Entry entry = countEntries.next();
                entries[j] = entry.key;
                entries[j + 1] = entry.value;
                entries[j + 2] = i;
                j += 3;
                while(countEntries.hasNext){
                    countEntries.next();
                    entries[j] = entry.key;
                    entries[j + 1] = entry.value;
                    entries[j + 2] = i;
                    j += 3;
                }
            }

            for(int i = 0; i < L; i++){
                entriesBacking[i] = i;
            }
            Arrays.sort(entriesBacking, 0, L, Structs.comparingInt(i -> entries[i * 3]));
            for(int i = 0; i < L; i++){
                int from = entriesBacking[i] * 3, to = i * 3;
                entries3a[to] = entries[from];
                entries3a[to + 1] = entries[from + 1];
                entries3a[to + 2] = entries[from + 2];
            }

            for(int i = 0, pos = 0; i < L * 3; i += 3){
                pos = (locs[entries3a[i + 2]][entries3a[i + 1]] += pos);
            }

            for(int thread = 0; thread < threads; thread++){
                futures[thread] = commonPool.pool.submit(task2s[thread]);
            }
            for(int i = 0; i < threads; i++){
                try{
                    futures[i].get();
                }catch(ExecutionException | InterruptedException e){
                    commonPool.pool.execute(task2s[i]);
                }
            }

            return swap;
        }

        static int[] countingSortMap(final int[] arr, final int[] swap, final int end){
            //int[] locs = CountingSort.locs;
            int[] locs = BasicCountingSort.locs;
            final IntIntMap counts = BasicCountingSort.countses[0];
            counts.clear();

            int unique = 0;
            final int end3 = end * 3;
            for(int i = 0; i < end3; i += 3){
                int loc = counts.getOrPut(arr[i], unique);
                arr[i] = loc;
                if(loc == unique){
                    if(unique >= locs.length){
                        locs = Arrays.copyOf(locs, unique * 3 / 2);
                    }
                    locs[unique++] = 1;
                }else{
                    locs[loc]++;
                }
            }
            BasicCountingSort.locs = locs;

            if(entries.length < unique){
                final int prevLength = entries.length;
                entries = Arrays.copyOf(entries, unique * 3 / 2);
                final Point2[] entries = BasicCountingSort.entries;
                for(int i = prevLength; i < entries.length; i++) entries[i] = new Point2();
            }
            final Point2[] entries = BasicCountingSort.entries;

            final IntIntMap.Entries countEntries = counts.entries();
            final IntIntMap.Entry entry = countEntries.next();
            entries[0].set(entry.key, entry.value);
            int j = 1;
            while(countEntries.hasNext){
                countEntries.next();
                entries[j++].set(entry.key, entry.value);
            }
            Arrays.sort(entries, 0, unique, Structs.comparingInt(p -> p.x));

            int prev = entries[0].y, next;
            for(int i = 1; i < unique; i++){
                locs[next = entries[i].y] += locs[prev];
                prev = next;
            }
            for(int i = end - 1, i3 = i * 3; i >= 0; i--, i3 -= 3){
                final int destPos = --locs[arr[i3]] * 3;
                swap[destPos] = arr[i3];
                swap[destPos + 1] = arr[i3 + 1];
                swap[destPos + 2] = arr[i3 + 2];
            }

            return swap;
        }
    }

    static class PopulateTask extends RecursiveAction{
        int from, to;
        static int[] tasks;
        static LightRequest[] src;
        static LightRequest[] dest;
        static int[] locs;

        //private static final int threshold = 256;
        PopulateTask(int from, int to){
            this.from = from;
            this.to = to;
        }

        @Override
        protected void compute(){
            final int[] locs = PopulateTask.locs;
            if(to - from > 1 && locs[to] - locs[from] > 2048){
                final int half = (locs[to] + locs[from]) >> 1;
                int mid = Arrays.binarySearch(locs, from, to, half);
                if(mid < 0) mid = -mid - 1;
                if(mid != from && mid != to){
                    invokeAll(new PopulateTask(from, mid), new PopulateTask(mid, to));
                    return;
                }
            }
            final LightRequest[] src = PopulateTask.src, dest = PopulateTask.dest;
            final int[] tasks = PopulateTask.tasks;
            for(int i = from; i < to; i++){
                final int point = i * 3, pos = tasks[point + 1], length = tasks[point + 2];
                if(length < 10){
                    final int end = pos + length;
                    for(int sj = pos, dj = locs[i]; sj < end; sj++, dj++){
                        dest[dj] = src[sj];
                    }
                }else{
                    System.arraycopy(src, pos, dest, locs[i], Math.min(length, dest.length - locs[i]));
                }
            }
        }
    }
}
