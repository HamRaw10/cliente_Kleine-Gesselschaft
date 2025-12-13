package utilidades.interfaces;

@FunctionalInterface
public interface ChatListener {
    void onSendMessage(String message);
}
