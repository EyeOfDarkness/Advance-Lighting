package lights.graphics;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import lights.*;

public class AltCacheSpriteBatch extends Batch{
    public AltCacheSprites activeCache;
    static boolean glowing = false;

    @Override
    protected void draw(Texture texture, float[] spriteVertices, int offset, int count){

    }

    @Override
    protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
        activeCache.add(region, x, y, originX, originY, width, height, rotation);
    }

    @Override
    protected void flush(){

    }

    void setGlowing(boolean glow){
        glowing = glow;
    }

    public static class AltCacheSprites{
        Seq<CacheData> data = new Seq<>();
        CacheData activeData;
        Texture lastTexture;

        void add(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
            if(region.texture != lastTexture || activeData == null){
                updateCache(region.texture);
            }
            activeData.add(region, x, y, originX, originY, width, height, rotation);
        }
        void updateCache(Texture texture){
            if(activeData != null){
                activeData.end();
            }
            activeData = new CacheData();
            activeData.texture = texture;
            data.add(activeData);
        }

        public void render(){
            for(CacheData cd : data){
                AdvanceLighting.batch.superDraw(cd.texture, cd.vertices, cd.idx);
            }
        }
    }

    static class CacheData{
        int idx = 0;
        float[] vertices = new float[320 * SpriteBatch.SPRITE_SIZE];
        Texture texture;

        void end(){
            float[] nr = new float[idx];
            System.arraycopy(vertices, 0, nr, 0, idx);
            vertices = nr;
        }

        void add(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
            if(idx >= vertices.length) return;
            texture = region.texture;

            float[] vertices = this.vertices;
            int idx = this.idx;
            this.idx += SpriteBatch.SPRITE_SIZE;

            float color = glowing ? Color.whiteFloatBits : Color.blackFloatBits;
            float mixColor = Color.clearFloatBits;

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

                vertices[idx] = x1;
                vertices[idx + 1] = y1;
                vertices[idx + 2] = color;
                vertices[idx + 3] = u;
                vertices[idx + 4] = v;
                vertices[idx + 5] = mixColor;

                vertices[idx + 6] = x2;
                vertices[idx + 7] = y2;
                vertices[idx + 8] = color;
                vertices[idx + 9] = u;
                vertices[idx + 10] = v2;
                vertices[idx + 11] = mixColor;

                vertices[idx + 12] = x3;
                vertices[idx + 13] = y3;
                vertices[idx + 14] = color;
                vertices[idx + 15] = u2;
                vertices[idx + 16] = v2;
                vertices[idx + 17] = mixColor;

                vertices[idx + 18] = x4;
                vertices[idx + 19] = y4;
                vertices[idx + 20] = color;
                vertices[idx + 21] = u2;
                vertices[idx + 22] = v;
                vertices[idx + 23] = mixColor;
            }else{
                float fx2 = x + width;
                float fy2 = y + height;
                float u = region.u;
                float v = region.v2;
                float u2 = region.u2;
                float v2 = region.v;

                vertices[idx] = x;
                vertices[idx + 1] = y;
                vertices[idx + 2] = color;
                vertices[idx + 3] = u;
                vertices[idx + 4] = v;
                vertices[idx + 5] = mixColor;

                vertices[idx + 6] = x;
                vertices[idx + 7] = fy2;
                vertices[idx + 8] = color;
                vertices[idx + 9] = u;
                vertices[idx + 10] = v2;
                vertices[idx + 11] = mixColor;

                vertices[idx + 12] = fx2;
                vertices[idx + 13] = fy2;
                vertices[idx + 14] = color;
                vertices[idx + 15] = u2;
                vertices[idx + 16] = v2;
                vertices[idx + 17] = mixColor;

                vertices[idx + 18] = fx2;
                vertices[idx + 19] = y;
                vertices[idx + 20] = color;
                vertices[idx + 21] = u2;
                vertices[idx + 22] = v;
                vertices[idx + 23] = mixColor;
            }
        }
    }
}
