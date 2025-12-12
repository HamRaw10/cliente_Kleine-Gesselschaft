package controles;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;

import entidades.Jugador;
import utilidades.Colisiones;

public class ControlDelJuego {

    private final Jugador jugador;
    private Colisiones colisiones;

    private OrthographicCamera cam;
    private Viewport viewport;

    private float destinoX, destinoY;
    private boolean tieneDestino = false;

    public ControlDelJuego(Colisiones colisiones) {
        this.colisiones = colisiones;
        this.jugador = new Jugador(colisiones);
        try {
            jugador.getDinero().setCantidad(50);
            jugador.getMochila().getItems().clear(); // sin ropa inicial
        } catch (Exception ignored) {}

        this.destinoX = jugador.getPersonajeX();
        this.destinoY = jugador.getPersonajeY();
        cancelarMovimiento(); // arranca quieto
    }

    public void actualizar(float delta) {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();

        // Click para fijar destino cuando no hay overlays bloqueando
        if (Gdx.input.justTouched()) {
            Vector3 w = new Vector3(mouseX, mouseY, 0f);
            if (viewport != null) viewport.unproject(w);
            else if (cam != null) cam.unproject(w);
            setDestino(w.x, w.y, true);
        }

        if (tieneDestino) {
            jugador.actualizar(delta, destinoX, destinoY);
            float dx = jugador.getPersonajeX() - destinoX;
            float dy = jugador.getPersonajeY() - destinoY;
            if (dx * dx + dy * dy < 0.25f) cancelarMovimiento(); // llegó
        }
    }

    public void cancelarMovimiento() {
        tieneDestino = false;
        try { jugador.cancelarMovimiento(); } catch (Exception ignored) {}
        try { jugador.estaEnMovimiento = false; } catch (Exception ignored) {}
    }

    public void setDestino(float x, float y, boolean activar) {
        destinoX = x; destinoY = y; tieneDestino = activar;
        if (!activar) cancelarMovimiento();
    }

    public Jugador getJugador() { return jugador; }
    public InputProcessor getInputProcessor() { return null; }
    public void render(SpriteBatch batch) { jugador.render(batch); }
    public void dispose() { jugador.dispose(); }

    public void setCamera(OrthographicCamera cam) { this.cam = cam; }
    public void setViewport(Viewport viewport) { this.viewport = viewport; }

    public void setColisiones(Colisiones nuevasColisiones) {
        this.colisiones = nuevasColisiones;
        try { jugador.setColisiones(nuevasColisiones); } catch (Exception ignored) {}
        Gdx.app.log("ControlDelJuego", "Colisiones actualizadas para nuevo mapa.");
    }
}
