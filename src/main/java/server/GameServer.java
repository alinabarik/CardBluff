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
            System.err.println("Ошибка сервера: " + e.getMessage());
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

        // Оповещаем всех в очереди о текущем прогрессе сбора
        updateQueueStatus(targetQueue, count);

        // Если нужное количество набралось — создаем сессию
        if (targetQueue.size() == count) {
            List<ClientHandler> sessionPlayers = new ArrayList<>(targetQueue);
            targetQueue.clear(); // Очищаем очередь для следующих желающих

            GameSession session = new GameSession(sessionPlayers);
            for (ClientHandler p : sessionPlayers) {
                p.setSession(session);
            }
            // Запускаем проверку готовности
            session.initSession();
            System.out.println("Создана новая игровая сессия на " + count + " игроков.");
        }
    }

    // Удаляем игрока из очередей, если он отключился до старта
    public synchronized void removeFromQueue(ClientHandler player) {
        if (queue2.remove(player)) updateQueueStatus(queue2, 2);
        if (queue3.remove(player)) updateQueueStatus(queue3, 3);
        if (queue4.remove(player)) updateQueueStatus(queue4, 4);
    }

    private void updateQueueStatus(List<ClientHandler> queue, int max) {
        for (ClientHandler p : queue) {
            p.sendMessage(new shared.Message(shared.MessageType.LOBBY_UPDATE, "Ожидание игроков... (" + queue.size() + "/" + max + ")"));
        }
    }

    public static void main(String[] args) {
        new GameServer().start();
    }
}