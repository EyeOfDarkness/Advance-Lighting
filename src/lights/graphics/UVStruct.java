package lights.graphics;

import arc.graphics.*;

public class UVStruct{
    public static int uv(Texture tex, float u, float v){
        int iu = (int)(u * 4095);
        int iv = (int)(v * 4095);

        return iu | (iv << 12) | ((tex.getTextureObjectHandle() & 255) << 24);
    }
}
