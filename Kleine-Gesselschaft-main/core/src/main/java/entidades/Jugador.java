package entidades;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

import java.util.Map;

import utilidades.items.ClothingItem;
import utilidades.items.VisualOutfit;
import entidades.EquipamentSlot;
import utilidades.Animacion;
import utilidades.Colisiones;
import utilidades.Moneda;
import utilidades.items.ClothingAnimationSet;
import utilidades.items.Item;


public class Jugador extends Personaje {

    private Colisiones colisiones;

    // Hitbox de “pies”: valores base (se escalan junto al sprite)
    private final float baseHitW = 12f;
    private final float baseHitH = 15f;
    private final float offsetFactorX = -0.7f; // relativo al ancho
    private final float offsetFactorY = -0.8f; // relativo al alto
    private final Rectangle hitbox;

    private float velPx = 160f;
    public boolean estaEnMovimiento = false;
    private boolean bloqueado = false;

    private Animation<TextureRegion> animacionIdle;
    private Animation<TextureRegion> animacionAdelante;
    private Animation<TextureRegion> animacionAtras;
    private Animation<TextureRegion> animacionDerecha;
    private Animation<TextureRegion> animacionIzquierda;
    private float tiempoAnimacion;
    private float velocidadX, velocidadY;

    private Direccion direccionActual = Direccion.ABAJO;

    // ⬇️ NUEVO: dinero del jugador
    private final Moneda dinero;
    private final VisualOutfit outfit = new VisualOutfit();


    private enum Direccion {ARRIBA, ABAJO, DERECHA, IZQUIERDA}
    private float baseSpeed = 160f;           // era tu velPx
    private final Mochila mochila = new Mochila();
    private final EquippedItems equipped = new EquippedItems();




    // Constructor clásico -> inicia con 0 monedas
    public Jugador(Colisiones colisiones) {
        this(colisiones, 0);
    }

    // ⬇️ NUEVO: constructor con monedas iniciales
    public Jugador(Colisiones colisiones, int monedasIniciales) {
        super("personaje/adelante/001.png", 300, 150, 1f);
        this.colisiones = colisiones;
        this.dinero = new Moneda(monedasIniciales);

        // Si tu Personaje tiene getWidth()/getHeight(), usalos:
        float spriteW = getWidth();
        float spriteH = getHeight();

        float hitW = baseHitW * escala;
        float hitH = baseHitH * escala;
        float hitOffsetX = hitW * offsetFactorX;
        float hitOffsetY = hitH * offsetFactorY;
        this.hitbox = new Rectangle(personajeX + hitOffsetX, personajeY + hitOffsetY, hitW, hitH);

        this.animacionIdle = Animacion.crearAnimacionDesdeCarpeta("personaje/adelante", 5, 0.2f);
        this.animacionAdelante = Animacion.crearAnimacionDesdeCarpeta("personaje/adelante", 6, 0.08f);
        this.animacionAtras = Animacion.crearAnimacionDesdeCarpeta("personaje/atras", 6, 0.08f);
        this.animacionDerecha = Animacion.crearAnimacionDesdeCarpeta("personaje/derecha", 6, 0.08f);
        this.animacionIzquierda = Animacion.crearAnimacionDesdeCarpeta("personaje/izquierda", 6, 0.08f);
        // Ejemplo de carga (3 frames por dir, ajustá a lo tuyo)
        /*this.pantalones = ClothingAnimationSet.loadFromFolder(
            "Pantalones",      // carpeta que mostraste
            "pantalon",        // baseName
            3,                 // frames por dirección
            0.08f              // duración por frame
        ).setOffset(0f, 0f);       // corrés si necesitás alinear*/
        Gdx.app.log("ASSET", "P1=" + Gdx.files.internal("Ropa/Pantalones/pantalon1.png").exists());
        Gdx.app.log("ASSET", "R1=" + Gdx.files.internal("Ropa/Remeras/remera1.png").exists());


    }

    // === DINERO (helpers) ===
    public Moneda getDinero() {
        return dinero;
    }

    public void ganarMonedas(int cant) {
        dinero.sumar(cant);
    }

    public boolean gastarMonedas(int costo) {
        return dinero.restar(costo);
    }

    public Mochila getMochila(){ return mochila; }
    public EquippedItems getEquipped(){ return equipped; }

    public float getBaseSpeed(){ return baseSpeed; }
    public void setBaseSpeed(float s){ baseSpeed = s; }

    public void equipVisual(EquipamentSlot slot, String folder, String baseName,
                            float frameDur, float ox, float oy) {
        // frames = 1 porque cada modelo tiene 1 imagen por dirección
        ClothingAnimationSet set = ClothingAnimationSet
            .loadFromFolder(folder, baseName, 1, frameDur)
            .setOffset(ox, oy);
        outfit.set(slot, set);
    }

    // Suma los bonus de todos los ítems equipados (ROPA)
    public float getVelocidadEfectiva(){
        float bonus = 0f;
        for (Map.Entry<EquipamentSlot, utilidades.items.Item> e : equipped.all().entrySet()){
            utilidades.items.Item it = e.getValue();
            if (it != null) {
                // ver punto 2
            }
        }
        return baseSpeed * (1f + bonus);
    }

    // Equipar por slot (validación simple por id)
    public boolean equip(String itemId, EquipamentSlot slot){
        for (Item it : mochila.getItems()){
            if (!it.getId().equals(itemId)) continue;

            switch (slot){
                case CABEZA:
                    if(!(itemId.contains("gorro") || itemId.contains("anteojos"))) return false;
                    break;
                case TORSO:
                    if(!(itemId.contains("remera") || itemId.contains("campera"))) return false;
                    break;
                case PIERNAS:
                    if(!itemId.contains("pantalon")) return false;
                    break;
                case PIES:
                    if(!itemId.contains("zapatillas")) return false;
                    break;
                case ACCESORIO:
                    // libre
                    break;
            }
            equipped.set(slot, it);
            return true;
        }
        return false;
    }

    // === HITBOX ===
    public Rectangle getHitbox() {
        return hitbox;
    }

    private void syncHitbox() {
        float hitW = baseHitW * escala;
        float hitH = baseHitH * escala;
        float hitOffsetX = hitW * offsetFactorX;
        float hitOffsetY = hitH * offsetFactorY;
        hitbox.setSize(hitW, hitH);
        hitbox.setPosition(personajeX + hitOffsetX, personajeY + hitOffsetY);
    }

    @Override
    public void setEscala(float escala) {
        super.setEscala(escala);
        syncHitbox();
    }

    public void setPos(float x, float y) {
        this.personajeX = x;
        this.personajeY = y;
        syncHitbox();
    }

    // === MOVIMIENTO con resolución por ejes ===
    private void moverConColision(float dx, float dy) {
        if (dx != 0f) {
            float oldX = personajeX;
            personajeX += dx;
            syncHitbox();
            if (colisiones.colisiona(hitbox)) {
                personajeX = oldX;
                syncHitbox();
                dx = 0f;   // ← ANTES: 12f (mal)
            }
        }
        if (dy != 0f) {
            float oldY = personajeY;
            personajeY += dy;
            syncHitbox();
            if (colisiones.colisiona(hitbox)) {
                personajeY = oldY;
                syncHitbox();
                dy = 0f;   // ← ANTES: 15f (mal)
            }
        }
        velocidadX = dx;
        velocidadY = dy;
    }

    private ClothingAnimationSet.Dir mapDir(Direccion d){
        switch (d){
            case ARRIBA:    return ClothingAnimationSet.Dir.ARRIBA;
            case ABAJO:     return ClothingAnimationSet.Dir.ABAJO;   // <– antes estaba mal
            case DERECHA:   return ClothingAnimationSet.Dir.DERECHA;
            case IZQUIERDA: return ClothingAnimationSet.Dir.IZQUIERDA;
            default:        return ClothingAnimationSet.Dir.ABAJO;
        }
    }







    public float getVelPx() {
        return getVelocidadEfectiva(); // devuelve la velocidad con bonus por ropa
    }

    public void setVelPx(float v) {
        setBaseSpeed(v); // actualiza la velocidad base
    }


    public void cancelarMovimiento() {
        velocidadX = 0f;
        velocidadY = 0f;
        estaEnMovimiento = false;
    }

    @Override
    public void actualizar(float delta, float targetX, float targetY) {
        if (bloqueado) {
            velocidadX = 0;
            velocidadY = 0;
            return;
        }

        float cx = personajeX + getWidth() * 0.01f;
        float cy = personajeY + getHeight() * 0.01f;

        float dx = targetX - cx;
        float dy = targetY - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        float movX = 0, movY = 0;
        if (len > 1f) {
            dx /= len;
            dy /= len;
            float speed = getVelocidadEfectiva();   // ← usa bonus de ropa
            movX = dx * speed * delta;
            movY = dy * speed * delta;


            if (Math.abs(movX) > Math.abs(movY)) {
                direccionActual = movX > 0 ? Direccion.DERECHA : Direccion.IZQUIERDA;
            } else {
                direccionActual = movY > 0 ? Direccion.ARRIBA : Direccion.ABAJO;
            }
        }

        moverConColision(movX, movY);

        estaEnMovimiento = Math.abs(velocidadX) > 0.001f || Math.abs(velocidadY) > 0.001f;
        tiempoAnimacion += delta;
    }

    @Override
    public void render(SpriteBatch batch) {
        TextureRegion frame;
        if (!estaEnMovimiento) {
            frame = animacionIdle.getKeyFrame(tiempoAnimacion, true);
        } else {
            switch (direccionActual) {
                case ARRIBA:
                    frame = animacionAtras.getKeyFrame(tiempoAnimacion, true);
                    break;
                case ABAJO:
                    frame = animacionAdelante.getKeyFrame(tiempoAnimacion, true);
                    break;
                case DERECHA:
                    frame = animacionDerecha.getKeyFrame(tiempoAnimacion, true);
                    break;
                case IZQUIERDA:
                    frame = animacionIzquierda.getKeyFrame(tiempoAnimacion, true);
                    break;
                default:
                    frame = animacionIdle.getKeyFrame(tiempoAnimacion, true);
            }
        }
        float w = getWidth() * escala;
        float h = getHeight() * escala;
        float x = getPersonajeX();
        float y = getPersonajeY();
        batch.draw(frame, getPersonajeX(), getPersonajeY(), w, h);

        ClothingAnimationSet.Dir dir = mapDir(direccionActual);
        outfit.draw(batch, dir, tiempoAnimacion, getPersonajeX(), getPersonajeY(), getWidth() * escala, getHeight() * escala);

    }

    public float getAncho() {
        return hitbox.width;
    }

    public float getAlto()  {
        return hitbox.height;
    }


    public void setBloqueado(boolean b) {
        bloqueado = b;
        velocidadX = 0;
        velocidadY = 0;
    }

    public void setColisiones(Colisiones nuevasColisiones) {
        if (nuevasColisiones != null) {
            this.colisiones = nuevasColisiones;
        }
    }

    /**
     * Equipa automáticamente la primera prenda disponible de cada slot que esté en la mochila.
     * Sirve para arrancar sin ropa hardcodeada y mantener synced el estado visual/equipado.
     */
    public void equiparPrimeraRopaDisponible() {
        for (Item item : mochila.getItems()) {
            if (!(item instanceof ClothingItem)) continue;
            ClothingItem ci = (ClothingItem) item;
            if (equipped.get(ci.getSlot()) != null) continue; // ya hay algo en ese slot

            try {
                equipClothing(ci);
            } catch (Exception e) {
                Gdx.app.error("EQUIP", "No se pudo auto-equipar " + ci.getId(), e);
            }
        }
    }


    public boolean equipClothing(ClothingItem ci) {
        Gdx.app.log("EQUIP", "Intentando equipar " + ci.getId() + " en slot " + ci.getSlot());
        try {
            // Forzar unequip del slot actual
            unequipClothing(ci.getSlot());

            // Verificar si el path base existe (ej. "Ropa/Torso/hippie_adelante.png")
            String basePath = ci.getFolder() + "/" + ci.getBaseName() + "_adelante.png";
            if (!Gdx.files.internal(basePath).exists()) {
                Gdx.app.error("EQUIP", "Archivo no encontrado: " + basePath);
                return false;
            }

            // Cargar el set de animación
            ClothingAnimationSet set = ClothingAnimationSet.loadFromFolder4(
                ci.getFolder(),
                ci.getBaseName(),
                1,  // frames por dirección
                0.08f,  // duración
                "_adelante", "_atras", "_derecha", "_izquierda"
            ).setOffset(0f, 0f);

            // Aplicar al outfit y equipped
            outfit.set(ci.getSlot(), set);
            equipped.set(ci.getSlot(), ci);
            Gdx.app.log("EQUIP", "Éxito - Prenda equipada: " + ci.getId());
            logEstadoRopa();
            return true;
        } catch (Exception e) {
            Gdx.app.error("EQUIP", "No se pudo equipar " + ci.getId(), e);
            return false;
        }
    }



    public void unequipClothing(entidades.EquipamentSlot slot) {
        outfit.set(slot, null);
        equipped.set(slot, null);
        Gdx.app.log("EQUIP", "Slot " + slot + " liberado.");
        logEstadoRopa();
    }

    private void logEstadoRopa() {
        for (EquipamentSlot s : EquipamentSlot.values()) {
            Item it = equipped.get(s);
            Gdx.app.log("EQUIP", " - " + s + ": " + (it == null ? "vacío" : it.getId()));
        }
    }



}
