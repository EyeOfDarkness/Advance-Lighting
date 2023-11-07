package lights.graphics;

import arc.graphics.*;

public class ALStructs{
    public static int uv(Texture tex, float u, float v){
        int iu = (int)(u * 4095);
        int iv = (int)(v * 4095);

        return iu | (iv << 12) | ((tex.getTextureObjectHandle() & 255) << 24);
    }
    public static int rgb(Color color){
        return (int)(color.r * 255) | ((int)(color.g * 255) << 8) | ((int)(color.b * 255) << 16);
    }
}
