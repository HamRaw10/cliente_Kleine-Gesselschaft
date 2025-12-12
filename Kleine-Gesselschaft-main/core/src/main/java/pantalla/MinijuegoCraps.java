package pantalla;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import entidades.Jugador;
import utilidades.Moneda;
import utilidades.MenuMinijuegos;

public class MinijuegoCraps extends ScreenAdapter {
    private Stage stage;
    private Skin skin;
    private Jugador jugador;
    private SpriteBatch batch;
    private Texture[] dados = new Texture[6]; // dado1.png a dado6.png
    private int dado1, dado2, punto = 0;
    private boolean primeraTirada = true;
    private Label lblEstado, lblDados;
    private Label lblPuntos;
    private int puntosPendientes = 0;
    private boolean finalizado = false;
    private final MenuMinijuegos.MinijuegoFinListener finListener;
    private int rachaGanadas = 0;
    private boolean partidaActiva = false;
    private int apuestaActual = 0;
    private BitmapFont fontTitulo;
    private BitmapFont fontCuerpo;
    private TextureRegion[] dadosRegion = new TextureRegion[6];

    public MinijuegoCraps(Jugador jugador, Skin skin, MenuMinijuegos.MinijuegoFinListener finListener) {
        this.jugador = jugador;
        this.skin = skin;
        this.finListener = finListener;
        this.batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        // Cargar dados (assets/images_craps/dado1.png ... dado6.png)
        String basePath = "images_craps/";
        for (int i = 0; i < 6; i++) {
            dados[i] = new Texture(basePath + "dado" + (i + 1) + ".png");
            dadosRegion[i] = new TextureRegion(dados[i]);
        }

        // Fuentes personalizadas (usa OpenSans si está en assets/fonts, si no escala la default)
        fontTitulo = crearFuente(26);
        fontCuerpo = crearFuente(18);
        LabelStyle estiloTitulo = new LabelStyle(fontTitulo, Color.BLACK);
        LabelStyle estiloCuerpo = new LabelStyle(fontCuerpo, Color.BLACK);

        lblEstado = new Label("Primera tirada. Apuesta: 10 monedas", estiloCuerpo);
        lblEstado.setWrap(true);
        lblEstado.setAlignment(Align.center);
        lblDados = new Label("", estiloCuerpo);
        lblDados.setWrap(true);
        lblDados.setAlignment(Align.center);
        lblPuntos = new Label("Puntos acumulados: 0", estiloCuerpo);
        lblPuntos.setAlignment(Align.center);
        TextButton btnJugar = crearBoton("Jugar (apostar 10)");
        TextButton btnTirar = crearBoton("Tirar Dados");
        TextButton btnSalir = crearBoton("Salir");
        btnJugar.addListener(event -> {
            if (btnJugar.isPressed()) iniciarPartida();
            return true;
        });
        btnTirar.addListener(event -> {
            if (btnTirar.isPressed()) tirarDados();
            return true;
        });
        btnSalir.addListener(event -> {
            if (btnSalir.isPressed()) salir();
            return true;
        });

        Table table = new Table(skin);
        table.setFillParent(true);
        table.center();
        table.add(new Label("Craps", estiloTitulo)).width(460f).padBottom(10f).row();
        table.add(lblEstado).width(460f).pad(6f).row();
        table.add(lblDados).width(460f).pad(4f).row();
        table.add(lblPuntos).width(460f).pad(4f).row();
        table.add(btnJugar).size(220, 48).padTop(8f).row();
        table.add(btnTirar).size(220, 52).padTop(8f).row();
        table.add(btnSalir).size(220, 48).padTop(8f).row();
        stage.addActor(table);
    }

    private BitmapFont crearFuente(int size) {
        String ttfPath = Gdx.files.internal("fonts/OpenSans-SemiBold.ttf").exists()
            ? "fonts/OpenSans-SemiBold.ttf"
            : (Gdx.files.internal("fonts/Body.ttf").exists() ? "fonts/Body.ttf" : null);
        if (ttfPath != null && Gdx.files.internal(ttfPath).exists()) {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(ttfPath));
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = size;
            p.color = Color.BLACK;
            BitmapFont font = gen.generateFont(p);
            gen.dispose();
            return font;
        }
        BitmapFont fallback = new BitmapFont();
        fallback.getData().setScale(size / 18f); // escalar default
        fallback.setColor(Color.BLACK);
        return fallback;
    }

    private TextButton crearBoton(String texto) {
        TextButton.TextButtonStyle base = skin.get(TextButton.TextButtonStyle.class);
        TextButton.TextButtonStyle estilo = new TextButton.TextButtonStyle();
        estilo.up = base.up;
        estilo.down = base.down;
        estilo.checked = base.checked;
        estilo.over = base.over;
        estilo.font = fontCuerpo;
        estilo.fontColor = Color.BLACK;
        return new TextButton(texto, estilo);
    }

    private void iniciarPartida() {
        if (partidaActiva) return;
        Moneda dinero = jugador.getDinero();
        if (!dinero.restar(10)) {
            lblEstado.setText("No tienes suficientes monedas para jugar.");
            return;
        }
        apuestaActual = 10;
        primeraTirada = true;
        punto = 0;
        dado1 = 0; dado2 = 0;
        partidaActiva = true;
        lblEstado.setText("Apuesta colocada. Tira los dados.");
        lblDados.setText("");
    }

    private boolean tirarDados() {
        if (!partidaActiva) {
            lblEstado.setText("Pulsa 'Jugar' para apostar primero.");
            return false;
        }

        dado1 = (int) (Math.random() * 6) + 1;
        dado2 = (int) (Math.random() * 6) + 1;
        int suma = dado1 + dado2;
            lblDados.setText("Dado1: " + dado1 + " Dado2: " + dado2 + " Suma: " + suma);

        if (primeraTirada) {
            if (suma == 7 || suma == 11) {
                rachaGanadas++;
                int recompensa = 30 + 10 * Math.max(0, rachaGanadas - 1);
                puntosPendientes += recompensa + apuestaActual; // devuelve apuesta + recompensa
                lblEstado.setText("¡Ganaste! +" + recompensa + " (se devuelve apuesta " + apuestaActual + ") racha " + rachaGanadas);
                partidaActiva = false; // termina la ronda
                primeraTirada = true; // Reiniciar
            } else if (suma == 2 || suma == 3 || suma == 12) {
                lblEstado.setText("Perdiste.");
                rachaGanadas = 0;
                partidaActiva = false;
                primeraTirada = true;
            } else {
                punto = suma;
                lblEstado.setText("Punto establecido: " + punto + ". Tira de nuevo.");
                primeraTirada = false;
            }
        } else {
            if (suma == punto) {
                rachaGanadas++;
                int recompensa = 30 + 10 * Math.max(0, rachaGanadas - 1);
                puntosPendientes += recompensa + apuestaActual; // devuelve apuesta + recompensa
                lblEstado.setText("¡Ganaste el punto! +" + recompensa + " (se devuelve apuesta " + apuestaActual + ") racha " + rachaGanadas);
                partidaActiva = false;
                primeraTirada = true;
            } else if (suma == 7) {
                lblEstado.setText("Perdiste (sacaste 7).");
                rachaGanadas = 0;
                partidaActiva = false;
                primeraTirada = true;
            } else {
                lblEstado.setText("Sigue tirando. Punto: " + punto);
            }
        }
        lblPuntos.setText("Puntos acumulados: " + puntosPendientes);
        if (!partidaActiva) apuestaActual = 0;
        return false;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();

        // Dibujar dados si hay resultado
        if (dado1 > 0) {
            batch.setProjectionMatrix(stage.getCamera().combined);
            batch.begin();
            // Tinte oscuro para contrastar con el fondo blanco
            batch.setColor(Color.DARK_GRAY);
            float size = calcularTamanoDado();
            float espacio = size * 0.4f;
            float total = size * 2 + espacio;
            float startX = (Gdx.graphics.getWidth() - total) / 2f;
            float y = (Gdx.graphics.getHeight() * 0.7f) - (size / 2f);
            batch.draw(dadosRegion[dado1 - 1], startX, y, size, size);
            batch.draw(dadosRegion[dado2 - 1], startX + size + espacio, y, size, size);
            batch.setColor(Color.WHITE);
            batch.end();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            salir();
        }
    }

    private void salir() {
        if (finalizado) return;
        finalizado = true;
        if (finListener != null) finListener.onFin(puntosPendientes);
    }

    @Override
    public void dispose() {
        stage.dispose();
        batch.dispose();
        if (fontTitulo != null) fontTitulo.dispose();
        if (fontCuerpo != null) fontCuerpo.dispose();
        for (Texture t : dados) t.dispose();
    }

    private float calcularTamanoDado() {
        float base = Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return Math.max(90f, Math.min(180f, base * 0.14f));
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        batch.setProjectionMatrix(stage.getCamera().combined);
    }
}
