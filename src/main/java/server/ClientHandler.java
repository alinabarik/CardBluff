package server;

import com.google.gson.Gson;
import shared.Card;
import shared.Message;
import shared.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private GameSession session;

    private PrintWriter out;
    private BufferedReader in;
    private final Gson gson = new Gson();

    private boolean isReady = false;
    private String username;
    private List<Card> hand = new ArrayList<>();
    private int bullets = 3;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    public void setSession(GameSession session) { this.session = session; }

    public boolean isReady() { return isReady; }
    public String getUsername() { return username; }
    public List<Card> getHand() { return hand; }
    public void setHand(List<Card> hand) { this.hand = hand; }

    public int getBullets() { return bullets; }
    public void useBullet() { bullets--; sendBulletsCount(); }
    public void addBullet() { bullets++; sendBulletsCount(); }

    public void sendBulletsCount() {
        sendMessage(new Message(MessageType.BULLETS_UPDATE, String.valueOf(bullets)));
    }

    public void sendHand() {
        sendMessage(new Message(MessageType.CARDS_DEALT, gson.toJson(hand)));
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String jsonLine;
            while ((jsonLine = in.readLine()) != null) {
                Message message = gson.fromJson(jsonLine, Message.class);

                switch (message.getType()) {
                    case CONNECT -> {
                        String[] parts = message.getPayload().split(",");
                        int count = Integer.parseInt(parts[0]);
                        this.username = parts[1];

                        sendBulletsCount();
                        server.addToQueue(this, count);
                    }
                    case PLAYER_READY -> {
                        this.isReady = true;
                        if (session != null) {
                            session.broadcast(new Message(MessageType.LOBBY_UPDATE, this.username + " подтвердил готовность!"));
                            session.checkReadiness();
                        }
                    }
                    case PLAY_TURN -> {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<shared.Card>>(){}.getType();
                        List<shared.Card> playedCards = gson.fromJson(message.getPayload(), listType);
                        if (session != null) session.handlePlayTurn(this, playedCards);
                    }
                    case CALL_BLUFF -> {
                        if (session != null) session.handleCallBluff(this);
                    }
                    default -> {}
                }
            }
        } catch (IOException e) {
            System.out.println("Клиент " + username + " отключился.");
        } finally {
            // Обязательно убираем игрока из очередей при разрыве соединения
            server.removeFromQueue(this);
        }
    }

    public void sendMessage(Message message) {
        if (out != null) {
            out.println(gson.toJson(message));
        }
    }
}