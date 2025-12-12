package com.heurix.interfaces;

public interface GameController {
    void isGoal(int direction);

    void connect(int numPlayer);

    void start();

    void updatePadPosition(int numPlayer, int y);

    void updateBallPosition(int x, int y);

    void updateScore(String score);

    void endGame(int winner);

    void backToMenu();
}
