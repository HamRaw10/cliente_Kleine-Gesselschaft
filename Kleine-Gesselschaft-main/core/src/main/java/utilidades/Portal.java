package utilidades;

import com.badlogic.gdx.math.Rectangle;

/**
 * Portal sencillo para usar con libGDX + Tiled.
 * Se instancia vacío y se completan los campos desde el loader.
 */
public class Portal {
    public Rectangle rect;
    public String targetMap;   // Opción A: salto a otro TMX
    public String targetArea;  // Opción B: alternar grupos dentro del mismo TMX
    public String transicion;

    public boolean isToMap()  { return targetMap  != null && !targetMap.isEmpty(); }
    public boolean isToArea() { return targetArea != null && !targetArea.isEmpty(); }
}
