package utilidades.network;

import utilidades.interfaces.GameController;

import java.io.IOException;
import java.net.*;

public class ClientThread extends Thread {

    private DatagramSocket socket;
    private int serverPort = 5555;
    private String ipServerStr = "127.0.0.1";
    private InetAddress ipServer;
    private boolean end = false;
    private GameController gameController;
    private int playerId = -1;

    public ClientThread(GameController gameController) {
        try {
            this.gameController = gameController;
            ipServer = InetAddress.getByName(ipServerStr);
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        } catch (SocketException | UnknownHostException e) {
//            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        do {
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
            try {
                socket.receive(packet);
                processMessage(packet);
            } catch (IOException e) {
//                throw new RuntimeException(e);
            }
        } while(!end);
    }

    private void processMessage(DatagramPacket packet) {
        String message = (new String(packet.getData(), 0, packet.getLength())).trim();
        String[] parts = message.split(":");

        System.out.println("Mensaje recibido: " + message);

        switch(parts[0]){
            case "AlreadyConnected":
                System.out.println("Ya estas conectado");
                break;
            case "Connected":
                System.out.println("Conectado al servidor");
                this.ipServer = packet.getAddress();
                this.playerId = Integer.parseInt(parts[1]);
                gameController.connect(this.playerId);
                break;
            case "Full":
                System.out.println("Servidor lleno");
                this.end = true;
                break;
            case "Start":
                this.gameController.start();
                break;
            case "PlayerPos":
                this.gameController.updatePlayerPosition(
                    Integer.parseInt(parts[1]),
                    Float.parseFloat(parts[2]),
                    Float.parseFloat(parts[3])
                );
                break;
            case "Move": // compatibilidad con el servidor externo
                // Puede venir como Move:id:x:y o Move:x:y (sin id en el connect)
                if (parts.length >= 4) {
                    this.gameController.updatePlayerPosition(
                        Integer.parseInt(parts[1]),
                        Float.parseFloat(parts[2]),
                        Float.parseFloat(parts[3])
                    );
                } else if (parts.length == 3 && playerId > 0) {
                    // Si no trae id, asumimos que es nuestra propia posición eco; la ignoramos
                    break;
                }
                break;
            case "PlayerJoined":
                // Alguien nuevo entró: mando mi posición para que me vean
                try {
                    gameController.updatePlayerPosition(Integer.parseInt(parts[1]), -1, -1);
                } catch (Exception ignored) {}
                break;
            case "PlayerLeft":
                this.gameController.playerLeft(Integer.parseInt(parts[1]));
                break;
            case "UpdateScore":
                this.gameController.updateScore(parts[1]);
                break;
            case "EndGame":
                this.gameController.endGame(Integer.parseInt(parts[1]));
                break;
            case "Disconnect":
                this.gameController.backToMenu();
                break;
        }

    }

    public void sendMessage(String message) {
        byte[] byteMessage = message.getBytes();
        DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, ipServer, serverPort);
        try {
            socket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendConnect(float x, float y) {
        sendMessage("Connect:" + x + ":" + y);
    }

    public void sendPosition(float x, float y) {
        // Enviamos ambos formatos para ser compatibles con el servidor interno (Pos/PlayerPos)
        // y el servidor externo (Move).
        sendMessage("Pos:" + x + ":" + y);
        sendMessage("Move:" + x + ":" + y);
    }

    public int getPlayerId() {
        return playerId;
    }

    public void terminate() {
        this.end = true;
        socket.close();
        this.interrupt();
    }
}
