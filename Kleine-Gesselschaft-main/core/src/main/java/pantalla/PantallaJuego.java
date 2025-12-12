package pantalla;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;

import java.util.Iterator;
import java.lang.reflect.Method;

import controles.ControlDelJuego;
import entidades.Jugador;
import entidades.EquipamentSlot; // *** NUEVO (para seed de ropa)
import utilidades.Chat;
import utilidades.Colisiones;
import utilidades.Inventario;
import utilidades.Portal;
import utilidades.Render;
import utilidades.DebugOverlay;
import utilidades.Tienda;
import utilidades.MenuMinijuegos;
import utilidades.items.ClothingItem; // *** NUEVO (para seed de ropa)

public class PantallaJuego extends ScreenAdapter {

    private OrthographicCamera camara;
    private ScreenViewport screenViewport;
    private DebugOverlay debugOverlay;
    private OrthographicCamera uiCamera;

    private TiledMap mapaTiled;
    private OrthogonalTiledMapRenderer mapRenderer;
    private int MAP_WIDTH, MAP_HEIGHT, TILE_SIZE_W, TILE_SIZE_H;
    private static final float UNIT_SCALE = 1f;
    private String mapaActualPath = null;

    private final Array<Portal> portales = new Array<>();
    private static final String MAPA_INICIAL = "exteriores/compras.tmx";
    // Spawns fijos para evitar depender de propiedades de Tiled
    private static final float SPAWN_EXTERIOR_X = 400f;
    private static final float SPAWN_EXTERIOR_Y = 220f;
    private static final float SPAWN_INTERIOR_X = 180f;
    private static final float SPAWN_INTERIOR_Y = 25f;
    private final Array<Rectangle> zonasTienda = new Array<>();
    private final Array<Rectangle> zonasMinijuegos = new Array<>();

    private Jugador jugador;
    private ControlDelJuego manejo;
    private Colisiones colisiones;
    private Chat chat;
    private Inventario inventario;
    private Music musicaFondo;
    private Stage hud;
    private Label lblMonedas;
    private Skin skinUI;
    private boolean dineroInicializado = false;
    private Stage stageInventario; // Stage propio del inventario
    private Tienda tiendaHippie;
    private boolean tiendaAbierta = false;
    private Dialog dialogoTienda;
    private MenuMinijuegos menuMinijuegos;
    private boolean menuMinijuegosAbierto = false;
    private ScreenAdapter minijuegoActivo;


    // Transiciones
    private enum TransitionState { NONE, FADING_OUT, SWITCHING, FADING_IN }
    private TransitionState transitionState = TransitionState.NONE;
    private float fadeAlpha = 0f, fadeSpeed = 2.5f;
    private String pendingMap = null;
    private String pendingTrans = "none";

    private ShapeRenderer shape;
    private boolean spawnInicialHecho = false;

    // *** NUEVO: estado del inventario
    private boolean inventarioAbierto = false;

    @SuppressWarnings("unused")
    private final Game juego;

    public PantallaJuego(Game juego) {
        this.juego = juego;
        camara = new OrthographicCamera();
        screenViewport = new ScreenViewport(camara);
        screenViewport.apply(true);

        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiCamera.update();

        if (Render.batch == null) Render.batch = new SpriteBatch();
        shape = new ShapeRenderer();
    }

    // ==== Helpers ====
    private boolean existeMapa(String path){ return path != null && Gdx.files.internal(path).exists(); }

    private String canonicalizarDestino(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace('\\', '/');
        if (s.equalsIgnoreCase("exteriores/eduactivo.tmx") ||
            s.equalsIgnoreCase("exteriores/educativo.tmx") ||
            s.equalsIgnoreCase("exteriores/eduactiva.tmx")) {
            s = "exteriores/Eduactivo.tmx";
        }
        if (!s.contains("/") && mapaActualPath != null && mapaActualPath.contains("/")) {
            String dir = mapaActualPath.substring(0, mapaActualPath.lastIndexOf('/') + 1);
            s = dir + s;
        }
        return s;
    }

    private MapLayer getLayerIgnoreCase(TiledMap map, String name) {
        MapLayer exact = map.getLayers().get(name);
        if (exact != null) return exact;
        for (MapLayer l : map.getLayers()) {
            if (l.getName() != null && l.getName().equalsIgnoreCase(name)) return l;
        }
        return null;
    }

    private boolean esMapaExterior(String path) {
        return path != null && path.toLowerCase().contains("exteriores/");
    }

    private boolean esMapaInterior(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        return lower.contains("arcade") ||
            lower.contains("bibloteca") ||
            lower.contains("cine") ||
            lower.contains("coffeshop") ||
            lower.contains("communitycenter") ||
            lower.contains("herramientas") ||
            lower.contains("hippie_house") ||
            lower.contains("pub") ||
            lower.contains("supermercado");
    }

    private String getPropStr(MapObject obj, String... keys) {
        for (String k : keys) {
            Object v = obj.getProperties().get(k);
            if (v != null) return v.toString();
        }
        for (String want : keys) {
            Iterator<String> it = obj.getProperties().getKeys();
            while (it.hasNext()) {
                String k = it.next();
                if (k != null && k.equalsIgnoreCase(want)) {
                    Object v = obj.getProperties().get(k);
                    if (v != null) return v.toString();
                }
            }
        }
        return null;
    }

    private void recalcularZoomParaNoSalirDelMapa(float worldWidth, float worldHeight) {
        float vw = camara.viewportWidth, vh = camara.viewportHeight;
        float maxZoomPorAncho = worldWidth / vw;
        float maxZoomPorAlto = worldHeight / vh;
        float zoomSeguro = Math.min(maxZoomPorAncho, maxZoomPorAlto);
        camara.zoom = (zoomSeguro > 0 && !Float.isNaN(zoomSeguro) && !Float.isInfinite(zoomSeguro)) ? zoomSeguro : 1f;
    }

    // ==== Portales ====
    private void cargarPortalesDesdeTiled(TiledMap map) {
        portales.clear();
        MapLayer interacciones = getLayerIgnoreCase(map, "interacciones");
        if (interacciones == null) return;

        for (MapObject obj : interacciones.getObjects()) {
            if (!(obj instanceof RectangleMapObject)) continue;

            String tipo = getPropStr(obj, "tipo", "class", "Tipo");
            if (tipo == null || !tipo.equalsIgnoreCase("portal")) continue;

            Rectangle r = ((RectangleMapObject) obj).getRectangle();
            Portal p = new Portal();
            p.rect = new Rectangle(r);

            String tm = canonicalizarDestino(getPropStr(obj, "targetMap","targetmap","destino","map"));
            String ta = getPropStr(obj, "targetArea","area","targetarea");
            p.targetMap = (tm != null && !tm.isEmpty()) ? tm : null;
            p.targetArea = (ta != null && !ta.isEmpty()) ? ta : null;

            String tr = getPropStr(obj, "transicion","transition");
            p.transicion = (tr != null) ? tr : "none";

            portales.add(p);
        }
    }

    // ==== Tiendas ====
    private void cargarTiendasDesdeTiled(TiledMap map) {
        zonasTienda.clear();
        if (map == null) return;
        MapLayer capaTienda = getLayerIgnoreCase(map, "tienda");
        if (capaTienda == null) return;
        for (MapObject obj : capaTienda.getObjects()) {
            if (obj instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject) obj).getRectangle();
                zonasTienda.add(new Rectangle(r));
            }
        }
    }

    private void cargarMinijuegosDesdeTiled(TiledMap map) {
        zonasMinijuegos.clear();
        if (map == null) return;
        MapLayer capa = getLayerIgnoreCase(map, "minijuegos");
        if (capa == null) return;
        for (MapObject obj : capa.getObjects()) {
            if (obj instanceof RectangleMapObject) {
                Rectangle r = ((RectangleMapObject) obj).getRectangle();
                zonasMinijuegos.add(new Rectangle(r));
            }
        }
    }

    private boolean esMapaHippieHouse() {
        return mapaActualPath != null && mapaActualPath.toLowerCase().contains("hippie_house");
    }

    private Vector2 obtenerSpawnBase(String targetPath) {
        String canon = canonicalizarDestino(targetPath);
        boolean exterior = esMapaExterior(canon);
        float x = exterior ? SPAWN_EXTERIOR_X : SPAWN_INTERIOR_X;
        float y = exterior ? SPAWN_EXTERIOR_Y : SPAWN_INTERIOR_Y;
        return new Vector2(x, y);
    }

    // ==== Carga de mapas ====
    private void notificarControlColisionesActualizadas() {
        if (manejo == null) return;
        try {
            Method m = manejo.getClass().getMethod("setColisiones", Colisiones.class);
            m.invoke(manejo, colisiones);
        } catch (Exception ignored) { }
    }

    private void cargarMapaPorRuta(String tmxPath) {
        String canon = canonicalizarDestino(tmxPath);
        if (!existeMapa(canon)) { Gdx.app.error("MAP","Mapa inexistente: "+canon); return; }

        if (mapRenderer != null) { mapRenderer.dispose(); mapRenderer = null; }
        if (mapaTiled != null) { mapaTiled.dispose(); mapaTiled = null; }

        mapaTiled = new TmxMapLoader().load(canon);
        mapRenderer = new OrthogonalTiledMapRenderer(mapaTiled, UNIT_SCALE);
        mapaActualPath = canon;

        MapProperties props = mapaTiled.getProperties();
        MAP_WIDTH = props.get("width", Integer.class);
        MAP_HEIGHT = props.get("height", Integer.class);
        TILE_SIZE_W = props.get("tilewidth", Integer.class);
        TILE_SIZE_H = props.get("tileheight", Integer.class);

        if (colisiones == null) colisiones = new Colisiones();
        colisiones.cargarDesdeMapa(mapaTiled, "colisiones", UNIT_SCALE);
        cargarPortalesDesdeTiled(mapaTiled);
        cargarTiendasDesdeTiled(mapaTiled);
        cargarMinijuegosDesdeTiled(mapaTiled);
        cerrarTienda(); // por si había una abierta en otro mapa

        float worldW = MAP_WIDTH * TILE_SIZE_W * UNIT_SCALE;
        float worldH = MAP_HEIGHT * TILE_SIZE_H * UNIT_SCALE;
        recalcularZoomParaNoSalirDelMapa(worldW, worldH);

        notificarControlColisionesActualizadas();
        spawnInicialHecho = false;
    }

    // ==== Ciclo de vida ====
    @Override
    public void show() {
        debugOverlay = new DebugOverlay();
        cargarMapaPorRuta(MAPA_INICIAL);

        float worldWidth = MAP_WIDTH * TILE_SIZE_W * UNIT_SCALE;
        float worldHeight = MAP_HEIGHT * TILE_SIZE_H * UNIT_SCALE;
        camara.position.set(worldWidth / 2f, worldHeight / 2f, 0f);
        camara.update();

        manejo = new ControlDelJuego(colisiones);
        manejo.setCamera(camara);
        manejo.setViewport(screenViewport);
        jugador = manejo.getJugador();

        // === INVENTARIO Y CHAT ===
        // Creamos ambos stages
        hud = new Stage(new ScreenViewport());
        stageInventario = new Stage(new ScreenViewport()); // ← nuevo stage solo para el inventario

        // Skin de UI
        if (Gdx.files.internal("uiskin.json").exists()) {
            skinUI = new Skin(Gdx.files.internal("uiskin.json"));
        }

        // Chat e Inventario
        if (jugador != null && skinUI != null) {
            chat = new Chat(skinUI, jugador, camara);
            inventario = new Inventario(stageInventario, skinUI, jugador); // ← firma correcta
            tiendaHippie = Tienda.crearTiendaHippie();
        }

        if (skinUI != null) {
            menuMinijuegos = new MenuMinijuegos(skinUI, new MenuMinijuegos.SeleccionListener() {
                @Override public void onElegir(MenuMinijuegos.Opcion opcion) {
                    cerrarMenuMinijuegos();
                    lanzarMinijuego(opcion);
                }
                @Override public void onCerrar() { cerrarMenuMinijuegos(); }
            });
        }

        // === HUD (Monedas) ===
        if (skinUI != null) {
            lblMonedas = new Label("Monedas: 0", skinUI);
            lblMonedas.setPosition(10, Gdx.graphics.getHeight() - 30);
            hud.addActor(lblMonedas);
            if (jugador != null) lblMonedas.setText("Monedas: " + jugador.getDinero().getCantidad());
        }

        // === Música de fondo ===
        if (Gdx.files.internal("musica1.mp3").exists()) {
            musicaFondo = Gdx.audio.newMusic(Gdx.files.internal("musica1.mp3"));
            musicaFondo.setLooping(true);
            musicaFondo.setVolume(0.5f);
            musicaFondo.play();
        }

        // Spawn inicial y movimiento bloqueado
        try { manejo.cancelarMovimiento(); } catch (Exception ignored) {}
        if (jugador != null) jugador.cancelarMovimiento();
        spawnInicialHecho = false;
    }


    // ==== Spawn seguro ====
    private float getPlayerW() {
        try { return jugador != null ? jugador.getAncho() : TILE_SIZE_W * 0.6f; }
        catch (Exception e) { return TILE_SIZE_W * 0.6f; }
    }
    private float getPlayerH() {
        try { return jugador != null ? jugador.getAlto() : TILE_SIZE_H * 0.9f; }
        catch (Exception e) { return TILE_SIZE_H * 0.9f; }
    }
    private boolean colisionaJugadorCentradoEn(float cx, float cy) {
        float w = Math.max(8f, getPlayerW());
        float h = Math.max(8f, getPlayerH());
        float x = cx - w * 0.5f;
        float y = cy - h * 0.5f;
        return colisiones != null && colisiones.colisionaAABB(x, y, w, h);
    }

    private Vector2 buscarSpawnSeguro(Vector2 centro) {
        if (!colisionaJugadorCentradoEn(centro.x, centro.y)) return new Vector2(centro);
        Vector2 offset = new Vector2(1, 0);
        for (float radio = Math.max(6f, TILE_SIZE_W * 0.25f); radio <= Math.max(48f, TILE_SIZE_W * 3f); radio += Math.max(6f, TILE_SIZE_W * 0.25f)) {
            for (int i = 0; i < 24; i++) {
                offset.setLength(radio).setAngleDeg(i * (360f / 24f));
                float px = centro.x + offset.x;
                float py = centro.y + offset.y;
                if (!colisionaJugadorCentradoEn(px, py)) return new Vector2(px, py);
            }
        }
        return new Vector2(centro.x, centro.y + Math.max(16f, TILE_SIZE_H));
    }

    // ==== Transiciones ====
    private void prepararTransicionMapa(String target, String tr) {
        // Si no se especifica targetMap en Tiled, interpretamos que es el mismo mapa actual
        String effectiveTarget = (target == null || target.isEmpty()) ? mapaActualPath : target;

        pendingMap = effectiveTarget;
        pendingTrans = (tr != null) ? tr : "none";
        transitionState = TransitionState.FADING_OUT;
        if (jugador != null) jugador.setBloqueado(true);
    }

    private void realizarCambioDeMapa() {
        if (pendingMap == null) { transitionState = TransitionState.FADING_IN; return; }

        String canon = canonicalizarDestino(pendingMap);
        if (!existeMapa(canon)) { Gdx.app.error("PORTAL","Destino inexistente: "+canon); transitionState = TransitionState.FADING_IN; return; }
        if (mapaActualPath != null && mapaActualPath.equals(canon)) {
            posicionarJugadorEnSpawnBase(canon);
            transitionState = TransitionState.FADING_IN; return;
        }

        cargarMapaPorRuta(canon);

        boolean esInterior = esMapaInterior(canon);

        posicionarJugadorEnSpawnBase(canon);

        if (jugador != null) {
            jugador.setEscala(esInterior ? 0.7f : 1f);
        }

        spawnInicialHecho = true; // evitar re-ubicación automática al centro en el siguiente render

        transitionState = TransitionState.FADING_IN;
    }

    private void posicionarJugadorEnSpawnBase(String targetPath) {
        Vector2 base = obtenerSpawnBase(targetPath != null ? targetPath : mapaActualPath);
        clampSnapYAplicarSpawn(base.x, base.y, false);
        if (jugador != null) jugador.cancelarMovimiento();
        try { manejo.cancelarMovimiento(); manejo.setDestino(base.x, base.y, false); } catch (Exception ignored) {}
        spawnInicialHecho = true; // ya fijamos spawn manualmente
    }

    private void clampSnapYAplicarSpawn(float x, float y, boolean snapToGrid) {
        float worldW = MAP_WIDTH * TILE_SIZE_W * UNIT_SCALE;
        float worldH = MAP_HEIGHT * TILE_SIZE_H * UNIT_SCALE;

        float margen = 4f;

        float sx = MathUtils.clamp(x, margen, Math.max(margen, worldW - margen));
        float sy = MathUtils.clamp(y, margen, Math.max(margen, worldH - margen));

        if (snapToGrid) {
            if (TILE_SIZE_W > 0) sx = Math.round(sx / TILE_SIZE_W) * TILE_SIZE_W;
            if (TILE_SIZE_H > 0) sy = Math.round(sy / TILE_SIZE_H) * TILE_SIZE_H;
        }

        if (jugador != null) jugador.setPos(sx, sy);
        camara.position.set(sx, sy, 0f);
        camara.update();
    }

    // ==== Input de portales ====
    private void procesarClickPortalesSiCorresponde() {
        if ((chat != null && chat.isChatVisible()) || (inventario != null && inventario.isVisible())) return;
        if (menuMinijuegosAbierto || minijuegoActivo != null || tiendaAbierta) return;
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 s = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
            screenViewport.unproject(s);
            for (Portal p : portales) {
                if (p.rect.contains(s.x, s.y)) {
                    prepararTransicionMapa(p.targetMap, p.transicion);
                    return;
                }
            }
        }
    }

    private void procesarClickTiendasSiCorresponde() {
        if (tiendaAbierta || menuMinijuegosAbierto || minijuegoActivo != null) return;
        if (!esMapaHippieHouse()) return;
        if (zonasTienda.size == 0) return;
        if ((chat != null && chat.isChatVisible()) || (inventario != null && inventario.isVisible())) return;
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 s = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
            screenViewport.unproject(s);
            for (Rectangle r : zonasTienda) {
                if (r.contains(s.x, s.y)) {
                    abrirTienda();
                    return;
                }
            }
        }
    }

    private void procesarClickMinijuegosSiCorresponde() {
        if (menuMinijuegosAbierto || minijuegoActivo != null) return;
        if (zonasMinijuegos.size == 0) return;
        if ((chat != null && chat.isChatVisible()) || (inventario != null && inventario.isVisible()) || tiendaAbierta) return;
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 s = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
            screenViewport.unproject(s);
            for (Rectangle r : zonasMinijuegos) {
                if (r.contains(s.x, s.y)) {
                    abrirMenuMinijuegos();
                    return;
                }
            }
        }
    }

    private void abrirTienda() {
        if (tiendaHippie == null || jugador == null || skinUI == null || stageInventario == null) return;
        dialogoTienda = construirDialogoTienda();
        dialogoTienda.show(stageInventario);
        tiendaAbierta = true;
        if (jugador != null) jugador.setBloqueado(true);
        Gdx.input.setInputProcessor(stageInventario);
    }

    private void cerrarTienda() {
        if (dialogoTienda != null) {
            dialogoTienda.remove();
            dialogoTienda = null;
        }
        if (tiendaAbierta && jugador != null && !inventarioAbierto && transitionState == TransitionState.NONE) {
            jugador.setBloqueado(false);
        }
        tiendaAbierta = false;
    }

    private Dialog construirDialogoTienda() {
        Dialog dlg = new Dialog("tienda hippie", skinUI);
        Table tabla = new Table(skinUI);
        for (final Tienda.Oferta o : tiendaHippie.getOfertas()) {
            final boolean yaTiene = tiendaHippie.yaPosee(jugador, o.getId());
            Label lbl = new Label(o.getNombreMinuscula() + " - " + o.getPrecio() + " monedas", skinUI);
            final TextButton btn = new TextButton(yaTiene ? "en inventario" : "comprar", skinUI);
            if (yaTiene || jugador.getDinero().getCantidad() < o.getPrecio()) {
                btn.setDisabled(true);
            }
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (btn.isDisabled()) return;
                    boolean ok = tiendaHippie.comprar(jugador, o);
                    if (ok) {
                        btn.setText("comprado");
                        btn.setDisabled(true);
                        if (inventario != null) inventario.actualizarSlots();
                    } else {
                        btn.setDisabled(true);
                    }
                }
            });
            tabla.add(lbl).left().pad(6f);
            tabla.add(btn).pad(6f);
            tabla.row();
        }
        dlg.getContentTable().add(tabla).pad(10f);
        TextButton cerrar = new TextButton("cerrar", skinUI);
        cerrar.addListener(new ClickListener(){
            @Override public void clicked(InputEvent event, float x, float y) { cerrarTienda(); }
        });
        dlg.getButtonTable().add(cerrar).pad(6f);
        dlg.setMovable(false);
        dlg.setModal(true);
        return dlg;
    }

    private void abrirMenuMinijuegos() {
        if (menuMinijuegos == null || menuMinijuegosAbierto) return;
        menuMinijuegos.mostrar();
        menuMinijuegosAbierto = true;
        if (jugador != null) jugador.setBloqueado(true);
        if (manejo != null) manejo.cancelarMovimiento();
    }

    private void cerrarMenuMinijuegos() {
        if (!menuMinijuegosAbierto) return;
        menuMinijuegosAbierto = false;
        if (menuMinijuegos != null) menuMinijuegos.ocultar();
        if (jugador != null && transitionState == TransitionState.NONE && minijuegoActivo == null && !inventarioAbierto && !tiendaAbierta) {
            jugador.setBloqueado(false);
        }
    }

    private void lanzarMinijuego(MenuMinijuegos.Opcion opcion) {
        if (jugador == null || skinUI == null || minijuegoActivo != null) return;
        MenuMinijuegos.MinijuegoFinListener fin = this::terminarMinijuego;
        switch (opcion) {
            case CARA_O_CRUZ:
                minijuegoActivo = new MinijuegoCaraCruz(jugador, skinUI, fin);
                break;
            case CRAPS:
                minijuegoActivo = new MinijuegoCraps(jugador, skinUI, fin);
                break;
            default:
                break;
        }
        if (minijuegoActivo != null && jugador != null) jugador.setBloqueado(true);
    }

    private void terminarMinijuego(int puntosGanados) {
        if (minijuegoActivo != null) {
            try { minijuegoActivo.dispose(); } catch (Exception ignored) {}
            minijuegoActivo = null;
        }
        if (jugador != null && puntosGanados > 0) {
            jugador.ganarMonedas(puntosGanados); // 1:1 puntos -> monedas
            Gdx.app.log("MINIJUEGO", "Puntos: " + puntosGanados + " => Monedas +" + puntosGanados);
        }
        if (jugador != null && transitionState == TransitionState.NONE && !menuMinijuegosAbierto && !inventarioAbierto && !tiendaAbierta) {
            jugador.setBloqueado(false);
        }
    }

    // ==== INVENTARIO: helpers ====
    private void abrirInventario() {
        if (inventario == null) return;
        if (!inventario.isVisible()) {
            inventario.setVisible(true);     // requiere que tu clase tenga este setter
            if (jugador != null) jugador.setBloqueado(true);
            inventarioAbierto = true;
        }
        if (inventario != null) {
            inventario.setVisible(true);
            inventario.actualizarSlots();  // Añade esto para actualizar slots al abrir
            Gdx.app.log("PANTALLA", "Inventario abierto y slots actualizados.");
        }
    }

    private void cerrarInventario() {
        if (inventario == null) return;
        if (inventario.isVisible()) {
            inventario.setVisible(false);
            if (jugador != null && transitionState == TransitionState.NONE) jugador.setBloqueado(false);
            inventarioAbierto = false;
        }
    }

    // ==== Render ====
    @Override public void render(float delta) {
        Gdx.gl.glClearColor(0,0,0,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (minijuegoActivo != null) {
            minijuegoActivo.render(delta);
            return;
        }

        switch (transitionState) {
            case FADING_OUT:
                fadeAlpha = Math.min(1f, fadeAlpha + fadeSpeed * delta);
                if (fadeAlpha >= 1f) { transitionState = TransitionState.SWITCHING; realizarCambioDeMapa(); }
                break;
            case FADING_IN:
                fadeAlpha = Math.max(0f, fadeAlpha - fadeSpeed * delta);
                if (fadeAlpha <= 0f) { transitionState = TransitionState.NONE; if (jugador != null && !inventarioAbierto) jugador.setBloqueado(false); }
                break;
            default: break;
        }

        // Spawn inicial (quieto) si el jugador ya existe antes del update
        if (jugador != null && !spawnInicialHecho && mapaTiled != null) {
            posicionarJugadorEnSpawnBase(mapaActualPath);
        }

        manejo.actualizar(delta);

        // Si el jugador aparece post-update, hacer spawn ahora
        if (jugador == null) {
            jugador = manejo.getJugador();
            if (jugador != null && chat == null && skinUI != null) {
                chat = new Chat(skinUI, jugador, camara);
                inventario = new Inventario(stageInventario, skinUI, jugador);
                tiendaHippie = Tienda.crearTiendaHippie();
            }
        }
        if (jugador != null && !spawnInicialHecho && mapaTiled != null) {
            posicionarJugadorEnSpawnBase(mapaActualPath);
        }

        // Cámara sigue al jugador (clamp)
        float jugadorX = (jugador != null) ? jugador.getPersonajeX() : (MAP_WIDTH*TILE_SIZE_W*UNIT_SCALE)*0.5f;
        float jugadorY = (jugador != null) ? jugador.getPersonajeY() : (MAP_HEIGHT*TILE_SIZE_H*UNIT_SCALE)*0.5f;
        camara.position.set(jugadorX, jugadorY, 0f);

        float worldWidth = MAP_WIDTH * TILE_SIZE_W * UNIT_SCALE;
        float worldHeight = MAP_HEIGHT * TILE_SIZE_H * UNIT_SCALE;
        float halfW = (camara.viewportWidth * camara.zoom) / 2f;
        float halfH = (camara.viewportHeight * camara.zoom) / 2f;
        camara.position.x = MathUtils.clamp(camara.position.x, halfW, Math.max(halfW, worldWidth - halfW));
        camara.position.y = MathUtils.clamp(camara.position.y, halfH, Math.max(halfH, worldHeight - halfH));
        camara.update();

        // Render por capas (fondo -> entidades -> techo)
        screenViewport.apply();
        mapRenderer.setView(camara);

        Array<Integer> abajo = new Array<>();
        Array<Integer> arriba = new Array<>();
        for (int i = 0; i < mapaTiled.getLayers().size(); i++) {
            String ln = mapaTiled.getLayers().get(i).getName();
            boolean esArriba = false;
            if (ln != null) {
                String l = ln.toLowerCase();
                esArriba = l.contains("sobre") || l.contains("foreground") || l.startsWith("z_");
            }
            if (esArriba) arriba.add(i); else abajo.add(i);
        }

        mapRenderer.render(toIntArray(abajo)); // fondo

        Render.batch.setProjectionMatrix(camara.combined);
        Render.batch.begin();
        manejo.render(Render.batch);
        Render.batch.end();

        mapRenderer.render(toIntArray(arriba)); // techos / carteles

        // (opcional) debug de portales
        shape.setProjectionMatrix(camara.combined);
        shape.begin(ShapeRenderer.ShapeType.Line);
        for (Portal p : portales) shape.rect(p.rect.x, p.rect.y, p.rect.width, p.rect.height);
        shape.end();

        // UI: chat & inventario (tu flujo original)
        if (chat != null) { chat.actualizar(delta); chat.render(); }
        boolean inventarioVisible = inventario != null && inventario.isVisible();
        if (inventarioVisible) { inventario.actualizar(delta); inventario.render(); }
        else if (tiendaAbierta && stageInventario != null) {
            stageInventario.act(delta);
            stageInventario.draw();
        }

        // Input processors (tu flujo original)
        if (chat != null && chat.isChatVisible()) chat.setInputProcessor();
        else if (inventario != null && inventario.isVisible()) Gdx.input.setInputProcessor(stageInventario);
        else if (menuMinijuegosAbierto && menuMinijuegos != null) Gdx.input.setInputProcessor(menuMinijuegos.getStage());
        else if (tiendaAbierta && stageInventario != null) Gdx.input.setInputProcessor(stageInventario);
        else Gdx.input.setInputProcessor(manejo.getInputProcessor());


        // Monedas HUD
        if (jugador != null && lblMonedas != null) lblMonedas.setText("Monedas: " + jugador.getDinero().getCantidad());
        if (hud != null) { hud.act(delta); hud.draw(); }
        if (menuMinijuegosAbierto && menuMinijuegos != null) {
            menuMinijuegos.render(delta);
        }

        debugOverlay.pollToggleKey();
        debugOverlay.render(shape, Render.batch, jugador, colisiones, camara, uiCamera, TILE_SIZE_W, TILE_SIZE_H, mapaActualPath);

        if (transitionState == TransitionState.NONE) {
            procesarClickPortalesSiCorresponde();
            procesarClickTiendasSiCorresponde();
            procesarClickMinijuegosSiCorresponde();
        }

        if (fadeAlpha > 0f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            uiCamera.update();
            shape.setProjectionMatrix(uiCamera.combined);
            shape.begin(ShapeRenderer.ShapeType.Filled);
            shape.setColor(0f,0f,0f, MathUtils.clamp(fadeAlpha,0f,1f));
            shape.rect(0,0, uiCamera.viewportWidth, uiCamera.viewportHeight);
            shape.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            if (inventario != null) {
                if (inventario.isVisible()) {
                    cerrarInventario();
                    Gdx.app.log("PANTALLA", "Cerrando inventario...");
                } else {
                    abrirInventario();
                }
            }
        }
    }


    private int[] toIntArray(Array<Integer> arr) {
        int[] out = new int[arr.size];
        for (int i=0;i<arr.size;i++) out[i] = arr.get(i);
        return out;
    }

    @Override public void resize(int width, int height) {
        if (screenViewport != null) {
            screenViewport.update(width, height, true);
            float worldWidth = MAP_WIDTH * TILE_SIZE_W * UNIT_SCALE;
            float worldHeight = MAP_HEIGHT * TILE_SIZE_H * UNIT_SCALE;
            recalcularZoomParaNoSalirDelMapa(worldWidth, worldHeight);
            camara.update();
        }
        uiCamera.setToOrtho(false, width, height);
        uiCamera.update();

        if (chat != null) chat.resize(width, height);
        if (inventario != null) inventario.resize(width, height);
        if (menuMinijuegos != null) menuMinijuegos.resize(width, height);
        if (hud != null) hud.getViewport().update(width, height, true);
        if (minijuegoActivo != null) minijuegoActivo.resize(width, height);
    }

    @Override public void dispose() {
        if (mapRenderer != null) mapRenderer.dispose();
        if (mapaTiled != null) mapaTiled.dispose();
        if (manejo != null) manejo.dispose();
        if (chat != null) chat.dispose();
        if (inventario != null) inventario.dispose();
        if (musicaFondo != null) musicaFondo.dispose();
        if (shape != null) shape.dispose();
        if (hud != null) hud.dispose();
        if (debugOverlay != null) debugOverlay.dispose();
        if (menuMinijuegos != null) menuMinijuegos.dispose();
        if (minijuegoActivo != null) minijuegoActivo.dispose();
    }

    private String areaActual = null;
    private void cambiarAreaPorClick(String targetArea, String transicion) {
        if (mapaTiled == null || targetArea == null) return;
        if (targetArea.equals(areaActual)) return;

        for (MapLayer layer : mapaTiled.getLayers()) {
            if (layer.getName() != null && layer.getName().startsWith("AREA_")) {
                layer.setVisible(layer.getName().equals(targetArea));
            }
        }
        areaActual = targetArea;

        String capaColision = "colisiones_" + targetArea.substring("AREA_".length());
        if (mapaTiled.getLayers().get(capaColision) != null) colisiones.cargarDesdeMapa(mapaTiled, capaColision, UNIT_SCALE);
        else colisiones.cargarDesdeMapa(mapaTiled, "colisiones", UNIT_SCALE);
        notificarControlColisionesActualizadas();

        Vector2 base = obtenerSpawnBase(mapaActualPath);
        clampSnapYAplicarSpawn(base.x, base.y, false);
        if (jugador != null) jugador.cancelarMovimiento();
        try { manejo.cancelarMovimiento(); manejo.setDestino(base.x, base.y, false); } catch (Exception ignored) {}
    }

    // *** NUEVO: semillas de ropa usando tus nombres de archivos reales
    // *** NUEVO: semillas de ropa usando tus nombres de archivos reales
    private void seedRopaBasica(Jugador j) {
        Gdx.app.log("SEED", "Iniciando seed de ropa básica...");
        // Pantalones
        j.getMochila().add(new ClothingItem("pant-hippie", "Pantalón Hippie",
            EquipamentSlot.PIERNAS, "Ropa/Pantalones", "pantalon_hippie", 1));
        j.getMochila().add(new ClothingItem("pant-jean", "Pantalón Jean",
            EquipamentSlot.PIERNAS, "Ropa/Pantalones", "pantalon_jean", 1));
        j.getMochila().add(new ClothingItem("pant-marron", "Pantalón Marrón",
            EquipamentSlot.PIERNAS, "Ropa/Pantalones", "pantalon_marron", 1));
        // Remeras
        j.getMochila().add(new ClothingItem("remera-hippie", "Remera Hippie",
            EquipamentSlot.TORSO, "Ropa/Remeras", "remera_hippie", 1));
        j.getMochila().add(new ClothingItem("remera-boca", "Remera Boca",
            EquipamentSlot.TORSO, "Ropa/Remeras", "remera_boca", 1));
        j.getMochila().add(new ClothingItem("remera-river", "Remera River",
            EquipamentSlot.TORSO, "Ropa/Remeras", "remera_river", 1));
        j.equiparPrimeraRopaDisponible(); // usa la ropa de la mochila en vez de hardcodear
        Gdx.app.log("SEED", "Mochila poblada con " + j.getMochila().count() + " ítems.");
    }
}
