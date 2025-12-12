package utilidades;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import entidades.Personaje;

public class Chat {
        private final Stage escenarioChat;
        private static TextField campoTexto;
        private boolean chatVisible;
        private BitmapFont fuente;
        private SpriteBatch batch;
        private ShapeRenderer shapeRenderer;
        private final Camera camara;

        // Para los globos de chat
        private String mensajeActual;
        private float tiempoMensaje;
        private final float DURACION_MENSAJE = 5f; // segundos
        private Personaje personaje;

        public Chat(Skin skin, Personaje personaje, Camera camara) {
            this.personaje = personaje;
            this.camara = camara;
            this.escenarioChat = new Stage(new ScreenViewport());
            this.batch = new SpriteBatch();
            this.fuente = new BitmapFont();
            this.shapeRenderer = new ShapeRenderer();

            // Configurar el campo de texto
            TextField.TextFieldStyle estiloCampo = new TextField.TextFieldStyle();
            estiloCampo.font = new BitmapFont();
            estiloCampo.fontColor = Color.WHITE;
            estiloCampo.background = skin.getDrawable("default-round");

            campoTexto = new TextField("", estiloCampo);
            campoTexto.setMessageText("Escribe un mensaje...");
            campoTexto.setWidth(400);
            campoTexto.setHeight(40);
            reposicionarCampoTexto();
            campoTexto.setVisible(false);

            escenarioChat.addActor(campoTexto);
            chatVisible = false;

            // Configurar fuente para globos
            fuente.setColor(Color.BLACK);
            fuente.getData().setScale(0.8f); // Tamaño más pequeño para globos
        }

        public void actualizar(float delta) {
            if (chatVisible) {
                escenarioChat.act(delta);

                // Tecla Enter para enviar mensaje
                if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                    enviarMensaje();
                }

                // Tecla ESC para cancelar
                if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                    ocultarChat();
                }
            } else {
                // Tecla T para abrir chat
                if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
                    mostrarChat();
                }
            }

            // Actualizar tiempo del mensaje actual
            if (mensajeActual != null) {
                tiempoMensaje += delta;
                if (tiempoMensaje >= DURACION_MENSAJE) {
                    mensajeActual = null;
                }
            }
        }

        public void render() {
            // Dibujar globo de chat si hay mensaje
            if (mensajeActual != null && personaje != null) {
                dibujarGloboChat();
            }

            // Dibujar interfaz de chat si está visible
            if (chatVisible) {
                escenarioChat.draw();
            }
        }

        private void dibujarGloboChat() {
            if (mensajeActual == null) return;

            if (camara != null) {
                shapeRenderer.setProjectionMatrix(camara.combined);
                batch.setProjectionMatrix(camara.combined);
            } else {
                shapeRenderer.setProjectionMatrix(escenarioChat.getCamera().combined);
                batch.setProjectionMatrix(escenarioChat.getCamera().combined);
            }

            float x = personaje.getPersonajeX() + personaje.getWidth() / 2;
            float y = personaje.getPersonajeY() + personaje.getHeight() + 20;

            // Medir texto con GlyphLayout
            GlyphLayout layout = new GlyphLayout(fuente, mensajeActual);
            float anchoGlobo = layout.width + 20;
            float altoGlobo = layout.height + 15;

            // Ajustar dentro de la vista de la cámara
            x = ajustarXDentroDeVista(x, anchoGlobo);

            // Dibujar fondo del globo con ShapeRenderer
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.rect(x - anchoGlobo/2, y, anchoGlobo, altoGlobo);
            shapeRenderer.triangle(
                x - 8, y,
                x + 8, y,
                x, y - 12
            );
            shapeRenderer.end();

            // Borde
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.BLACK);
            shapeRenderer.rect(x - anchoGlobo/2, y, anchoGlobo, altoGlobo);
            shapeRenderer.end();

            // Texto
            batch.begin();
            fuente.setColor(Color.BLACK);
            fuente.draw(batch, mensajeActual, x - layout.width/2, y + altoGlobo/2 + layout.height/2);
            batch.end();
        }


        private void mostrarChat() {
            chatVisible = true;
            campoTexto.setVisible(true);
            campoTexto.setText("");
            Gdx.input.setInputProcessor(escenarioChat);
            escenarioChat.setKeyboardFocus(campoTexto); // ✅ foco correcto
        }

        private void ocultarChat() {
            chatVisible = false;
            campoTexto.setVisible(false);
            escenarioChat.unfocus(campoTexto); // ✅ sacar foco
        }


        private void enviarMensaje() {
            String mensaje = campoTexto.getText().trim();
            if (!mensaje.isEmpty() && mensaje.length() <= 50) { // Límite de caracteres
                mensajeActual = mensaje;
                tiempoMensaje = 0f;
                Gdx.app.log("CHAT", "Jugador: " + mensaje);
            }
            ocultarChat();
        }

        public void resize(int width, int height) {
            escenarioChat.getViewport().update(width, height, true);
            reposicionarCampoTexto();
        }

        public void dispose() {
            escenarioChat.dispose();
            batch.dispose();
            fuente.dispose();
            shapeRenderer.dispose();
        }

        // Métodos públicos para control externo
        public boolean isChatVisible() {
            return chatVisible;
        }

        public static boolean estaEscribiendo() {
            return campoTexto.hasKeyboardFocus();
        }




        public void setPersonaje(Personaje personaje) {
            this.personaje = personaje;
        }

        public String getMensajeActual() {
            return mensajeActual;
        }

        public void setInputProcessor() {
            if (chatVisible) {
                Gdx.input.setInputProcessor(escenarioChat);
            }
        }

        private void reposicionarCampoTexto() {
            float worldWidth = escenarioChat.getViewport().getWorldWidth();
            campoTexto.setPosition(
                worldWidth / 2f - campoTexto.getWidth() / 2f,
                20f
            );
        }

        private float ajustarXDentroDeVista(float x, float anchoGlobo) {
            if (camara == null) {
                float limite = Gdx.graphics.getWidth();
                return MathUtils.clamp(x, anchoGlobo / 2f, limite - anchoGlobo / 2f);
            }
            float viewportW = camara.viewportWidth;
            float zoom = (camara instanceof OrthographicCamera)
                ? ((OrthographicCamera) camara).zoom
                : 1f;
            float halfView = viewportW * zoom * 0.5f;
            float min = camara.position.x - halfView + anchoGlobo / 2f;
            float max = camara.position.x + halfView - anchoGlobo / 2f;
            return MathUtils.clamp(x, min, max);
        }
    }
