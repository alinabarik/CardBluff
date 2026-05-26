package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class GameServer {
    private static final int PORT = 8080;

    // Очереди матчмейкинга для разного количества игроков
    private final List<ClientHandler> queue2 = new ArrayList<>();
    private final List<ClientHandler> queue3 = new ArrayList<>();
    private final List<ClientHandler> queue4 = new ArrayList<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен. Ожидание игроков...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    public synchronized void addToQueue(ClientHandler player, int count) {
        List<ClientHandler> targetQueue = switch (count) {
            case 3 -> queue3;
            case 4 -> queue4;
            default -> queue2;
        };

        targetQueue.add(player);
        System.out.println("Игрок " + player.getUsername() + " ищет игру на " + count + ". В очереди: " + targetQueue.size() + "/" + count);

        if (targetQueue.size() == count) {
            // Создаем копию списка для сессии и очищаем очередь
            List<ClientHandler> sessionPlayers = new ArrayList<>(targetQueue);
            targetQueue.clear();

            GameSession session = new GameSession(sessionPlayers);
            for (ClientHandler p : sessionPlayers) {
                p.setSession(session);
            }
            System.out.println("Создана новая игровая сессия на " + count + " игроков.");
        }
    }

    public static void main(String[] args) {
        new GameServer().start();
    }
}