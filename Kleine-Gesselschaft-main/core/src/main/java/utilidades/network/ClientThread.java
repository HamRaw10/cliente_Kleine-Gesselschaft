package utilidades.network;

import utilidades.interfaces.GameController;

import java.io.IOException;
import java.net.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientThread extends Thread {

    private DatagramSocket socket;
    private int serverPort = 5555;
    private volatile InetAddress ipServer;
    private boolean end = false;
    private GameController gameController;
    private int playerId = -1;
    private static final String BROADCAST_IP = "255.255.255.255";
    private static final int DISCOVERY_TIMEOUT_MS = 1500;
    private static final int DISCOVERY_RETRY_MS = 2000;
    private static final String DISCOVERY_MESSAGE = "DiscoverServer";
    private static final String DISCOVERY_RESPONSE = "ServerHere";
    private volatile boolean discoveryInProgress = false;
    private long lastDiscoveryRequestMs = 0L;
    private final Queue<String> pendingMessages = new ConcurrentLinkedQueue<>();

    public ClientThread(GameController gameController) {
        try {
            this.gameController = gameController;
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            ipServer = resolveInitialServerAddress();
            if (ipServer == null) {
                requestDiscoveryAsync();
            }
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
            case "Chat":
                handleChatMessage(message);
                break;
            case "MapPos":
                handleMapPos(parts, message);
                break;
            case "Disconnect":
                this.gameController.backToMenu();
                break;
            case "ServerHere":
                handleServerAnnouncement(packet);
                break;
        }

    }

    public void sendMessage(String message) {
        if (ipServer == null) {
            enqueuePending(message);
            requestDiscoveryAsync();
            return;
        }
        performSend(message);
    }

    public void sendConnect(float x, float y, String mapName) {
        sendMessage("Connect:" + x + ":" + y);
    }

    public void sendPosition(float x, float y) {
        // Enviamos ambos formatos para ser compatibles con el servidor interno (Pos/PlayerPos)
        // y el servidor externo (Move).
        sendMessage("Pos:" + x + ":" + y);
        sendMessage("Move:" + x + ":" + y);
    }

    public void sendPositionWithMap(float x, float y, String mapName) {
        if (mapName != null && !mapName.isEmpty()) {
            sendMessage("MapPos:" + mapName + ":" + x + ":" + y);
        }
        sendPosition(x, y);
    }

    public void sendChat(String message) {
        if (message == null || message.trim().isEmpty()) return;
        sendMessage("Chat:" + message.trim());
    }

    public int getPlayerId() {
        return playerId;
    }

    public void terminate() {
        this.end = true;
        socket.close();
        this.interrupt();
    }

    private void handleChatMessage(String rawMessage) {
        // Formato esperado: Chat:<id>:<texto libre>
        try {
            int first = rawMessage.indexOf(':');
            int second = rawMessage.indexOf(':', first + 1);
            if (first == -1 || second == -1) return;
            int senderId = Integer.parseInt(rawMessage.substring(first + 1, second));
            String msg = rawMessage.substring(second + 1);
            System.out.println("CHAT RX de " + senderId + ": " + msg);
            if (gameController != null && msg != null && !msg.isEmpty()) {
                gameController.updateChatMessage(senderId, msg);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleMapPos(String[] parts, String rawMessage) {
        // MapPos puede venir como: MapPos:<map>:<x>:<y> (sin id) o MapPos:<id>:<map>:<x>:<y>
        try {
            int idx = 1;
            int pid = -1;
            if (parts.length == 5) { // MapPos:id:map:x:y
                pid = Integer.parseInt(parts[idx]);
                idx++;
            }
            String map = parts[idx];
            float x = Float.parseFloat(parts[idx + 1]);
            float y = Float.parseFloat(parts[idx + 2]);
            if (pid == -1) {
                // si no vino id explícito, intentamos extraerlo del raw "MapPos:id:map:x:y"
                String[] rawParts = rawMessage.split(":");
                if (rawParts.length >= 5) {
                    pid = Integer.parseInt(rawParts[1]);
                } else {
                    return;
                }
            }
            if (gameController != null) {
                gameController.updatePlayerPositionInMap(pid, x, y, map);
            }
        } catch (Exception ignored) {
        }
    }

    private InetAddress resolveInitialServerAddress() throws UnknownHostException {
        String configuredHost = getConfiguredServerHost();
        if (configuredHost != null) {
            return InetAddress.getByName(configuredHost);
        }
        return tryDiscoverServer();
    }

    private String getConfiguredServerHost() {
        String prop = System.getProperty("kleine.server.ip");
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        String env = System.getenv("KLEINE_SERVER_IP");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return null;
    }

    private void handleServerAnnouncement(DatagramPacket packet) {
        InetAddress address = packet.getAddress();
        if (address == null) return;
        onServerResolved(address);
    }

    private InetAddress tryDiscoverServer() {
        DatagramSocket discoverySocket = null;
        try {
            discoverySocket = new DatagramSocket();
            discoverySocket.setBroadcast(true);
            discoverySocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);
            byte[] data = DISCOVERY_MESSAGE.getBytes();
            DatagramPacket packet = new DatagramPacket(
                data, data.length, InetAddress.getByName(BROADCAST_IP), serverPort);
            discoverySocket.send(packet);
            DatagramPacket response = new DatagramPacket(new byte[256], 256);
            while (true) {
                discoverySocket.receive(response);
                String msg = new String(response.getData(), 0, response.getLength()).trim();
                if (msg.startsWith(DISCOVERY_RESPONSE)) {
                    InetAddress addr = response.getAddress();
                    onServerResolved(addr);
                    return addr;
                }
            }
        } catch (IOException ignored) {
            System.out.println("NETWORK Client: no se pudo descubrir servidor por broadcast");
        } finally {
            if (discoverySocket != null) {
                discoverySocket.close();
            }
        }
        return null;
    }

    private void requestDiscoveryAsync() {
        if (socket == null) return;
        long now = System.currentTimeMillis();
        if (now - lastDiscoveryRequestMs < DISCOVERY_RETRY_MS) return;
        if (discoveryInProgress) return;
        discoveryInProgress = true;
        lastDiscoveryRequestMs = now;
        new Thread(() -> {
            try {
                InetAddress addr = tryDiscoverServer();
            } finally {
                discoveryInProgress = false;
            }
        }, "ClientDiscovery").start();
    }

    private void enqueuePending(String message) {
        if (pendingMessages.size() > 50) {
            pendingMessages.poll();
        }
        pendingMessages.offer(message);
    }

    private void performSend(String message) {
        if (message.startsWith("Chat:")) {
            System.out.println("TX -> " + message + " a " + ipServer + ":" + serverPort);
        }
        byte[] byteMessage = message.getBytes();
        DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, ipServer, serverPort);
        try {
            socket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void flushPendingMessages() {
        if (ipServer == null) return;
        String pending;
        while ((pending = pendingMessages.poll()) != null) {
            performSend(pending);
        }
    }

    private void onServerResolved(InetAddress address) {
        this.ipServer = address;
        if (address != null) {
            System.out.println("NETWORK Client: usando servidor " + address.getHostAddress());
            flushPendingMessages();
        }
    }
}
