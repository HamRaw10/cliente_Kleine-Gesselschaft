package utilidades.interfaces;

public interface GameController {
    void connect(int numPlayer);

    void start();

    void updatePlayerPosition(int numPlayer, float x, float y);

    void playerLeft(int numPlayer);

    void updateScore(String score);

    void endGame(int winner);

    void updateChatMessage(int playerId, String message);

    void updatePlayerPositionInMap(int numPlayer, float x, float y, String mapName);

    void backToMenu();
}
