package utilidades.network;

import utilidades.interfaces.GameController;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servidor UDP ligero para sincronizar varias instancias locales.
 * Asigna un id incremental y reenvía las posiciones al resto de clientes.
 */
public class ServerThread extends Thread {

    private static class ClientInfo {
        final int id;
        final InetAddress ip;
        final int port;
        float lastX;
        float lastY;
        boolean hasPos;

        ClientInfo(int id, InetAddress ip, int port) {
            this.id = id;
            this.ip = ip;
            this.port = port;
        }

        String getSocketKey() {
            return ip.toString() + ":" + port;
        }
    }

    private DatagramSocket socket;
    private final int serverPort = 5555;
    private volatile boolean end = false;
    private final int MAX_CLIENTS = 4;
    private int connectedClients = 0;
    private final List<ClientInfo> clients = new ArrayList<>();
    private final GameController gameController;

    public ServerThread(GameController gameController) throws SocketException {
        this.gameController = gameController;
        socket = new DatagramSocket(serverPort);
        socket.setBroadcast(true);
        setName("ServerThread");
        setDaemon(true);
    }

    @Override
    public void run() {
        do {
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
            try {
                socket.receive(packet);
                processMessage(packet);
            } catch (IOException e) {
                if (!end) {
                    e.printStackTrace();
                }
            }
        } while (!end);
    }

    private void processMessage(DatagramPacket packet) {
        String message = (new String(packet.getData(), 0, packet.getLength())).trim();
        if (message.isEmpty()) return;
        String[] parts = message.split(":");
        int index = findClientIndex(packet);
        System.out.println("Mensaje recibido " + message);

        switch (parts[0]) {
            case "Connect":
                handleConnect(packet, parts, index);
                break;
            case "Pos":
            case "Move": // aceptar ambos formatos para compatibilidad
                handlePosition(parts, index);
                break;
            case "Disconnect":
                handleDisconnect(index);
                break;
            default:
                break;
        }
    }

    private void handleConnect(DatagramPacket packet, String[] parts, int index) {
        if (index != -1) {
            sendMessage("AlreadyConnected", packet.getAddress(), packet.getPort());
            return;
        }

        if (connectedClients >= MAX_CLIENTS) {
            sendMessage("Full", packet.getAddress(), packet.getPort());
            return;
        }

        connectedClients++;
        ClientInfo newClient = new ClientInfo(connectedClients, packet.getAddress(), packet.getPort());
        if (parts.length >= 3) {
            try {
                newClient.lastX = Float.parseFloat(parts[1]);
                newClient.lastY = Float.parseFloat(parts[2]);
                newClient.hasPos = true;
            } catch (NumberFormatException ignored) {}
        }
        clients.add(newClient);
        sendMessage("Connected:" + connectedClients, packet.getAddress(), packet.getPort());

        // Avisar a todos que hay un nuevo jugador disponible
        broadcast("PlayerJoined:" + connectedClients, newClient.id);

        // Enviar su propia posición inicial al resto si la envió en el connect
        if (newClient.hasPos) {
            broadcast("PlayerPos:" + newClient.id + ":" + newClient.lastX + ":" + newClient.lastY, newClient.id);
            broadcast("Move:" + newClient.id + ":" + newClient.lastX + ":" + newClient.lastY, newClient.id);
        }

        // Enviar al jugador recién conectado las posiciones que ya conocemos
        for (ClientInfo client : clients) {
            if (client.hasPos) {
                sendMessage("PlayerPos:" + client.id + ":" + client.lastX + ":" + client.lastY,
                    newClient.ip, newClient.port);
                sendMessage("Move:" + client.id + ":" + client.lastX + ":" + client.lastY,
                    newClient.ip, newClient.port);
            }
        }

        // Arrancamos cuando haya al menos 2 clientes conectados
        if (connectedClients >= 2) {
            broadcast("Start", -1);
            if (gameController != null) {
                gameController.start();
            }
        }
    }

    private void handlePosition(String[] parts, int index) {
        if (index == -1 || parts.length < 3) return;

        ClientInfo client = clients.get(index);
        try {
            // Mensaje puede venir como Pos:x:y o Move:x:y (sin id)
            int start = parts.length >= 4 ? 2 : 1; // si algún cliente envía Move:id:x:y lo toleramos
            float x = Float.parseFloat(parts[start]);
            float y = Float.parseFloat(parts[start + 1]);
            client.lastX = x;
            client.lastY = y;
            client.hasPos = true;
            broadcast("PlayerPos:" + client.id + ":" + x + ":" + y, client.id);
            broadcast("Move:" + client.id + ":" + x + ":" + y, client.id);
        } catch (NumberFormatException ignored) {
        }
    }

    private void handleDisconnect(int index) {
        if (index == -1) return;
        ClientInfo client = clients.remove(index);
        connectedClients = Math.max(0, connectedClients - 1);
        broadcast("PlayerLeft:" + client.id, client.id);
        if (connectedClients == 0 && gameController != null) {
            gameController.backToMenu();
        }
    }

    private int findClientIndex(DatagramPacket packet) {
        String id = packet.getAddress().toString() + ":" + packet.getPort();
        for (int i = 0; i < clients.size(); i++) {
            if (id.equals(clients.get(i).getSocketKey())) {
                return i;
            }
        }
        return -1;
    }

    private void sendMessage(String message, InetAddress clientIp, int clientPort) {
        byte[] byteMessage = message.getBytes();
        DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, clientIp, clientPort);
        try {
            socket.send(packet);
        } catch (IOException e) {
            if (!end) {
                e.printStackTrace();
            }
        }
    }

    private void broadcast(String message, int exceptId) {
        for (ClientInfo client : clients) {
            if (client.id == exceptId) continue;
            sendMessage(message, client.ip, client.port);
        }
    }

    public void terminate() {
        this.end = true;
        if (socket != null) socket.close();
        this.interrupt();
    }
}
