package server;

import com.google.gson.Gson;
import shared.Card;
import shared.Message;
import shared.MessageType;
import shared.Rank;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameServer {
    private static final int PORT = 8080;
    private int expectedPlayers = 0;
    private final List<ClientHandler> players = new ArrayList<>();
    private boolean gameStarted = false;
    private int currentPlayerIndex = -1;

    private final List<Card> tablePile = new ArrayList<>();
    private final List<Card> lastPlayedCards = new ArrayList<>();
    private ClientHandler lastPlayer;
    private Rank currentTargetRank;
    private Rank lastDeclaredRank;

    private final DatabaseManager dbManager = new DatabaseManager();

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT + ". Ожидание игроков...");

            while (!gameStarted) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                players.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Ошибка сети: " + e.getMessage());
        }
    }

    public synchronized void setExpectedPlayers(int count) {
        if (expectedPlayers == 0) expectedPlayers = count;
    }

    public synchronized void broadcast(Message message) {
        for (ClientHandler player : players) player.sendMessage(message);
    }

    public synchronized void checkReadiness() {
        if (players.size() != expectedPlayers || expectedPlayers == 0) return;
        for (ClientHandler player : players) if (!player.isReady()) return;

        gameStarted = true;
        broadcast(new Message(MessageType.GAME_START, "Все готовы! Раздаем карты..."));
        dealCards();
    }

    private void dealCards() {
        Deck deck = new Deck();
        deck.shuffle();
        int cardsPerPlayer = deck.cardsLeft() / players.size();

        for (ClientHandler player : players) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < cardsPerPlayer; i++) hand.add(deck.drawCard());
            player.setHand(hand);
            player.sendHand();
        }
        startFirstTurn();
    }

    private void startFirstTurn() {
        currentPlayerIndex = new Random().nextInt(players.size());
        // Первый ход назначаем случайно, далее пойдет по порядку
        currentTargetRank = Rank.values()[new Random().nextInt(Rank.values().length)];
        nextTurn();
    }

    public synchronized void nextTurn() {
        ClientHandler activePlayer = players.get(currentPlayerIndex);
        broadcast(new Message(MessageType.LOBBY_UPDATE, "Сейчас ходит: " + activePlayer.getUsername()));

        String requiredRank = getRankNameInRussian(currentTargetRank);
        activePlayer.sendMessage(new Message(MessageType.YOUR_TURN, requiredRank));
    }

    public synchronized void handlePlayTurn(ClientHandler player, List<Card> playedCards) {
        if (players.indexOf(player) != currentPlayerIndex) return;

        lastPlayer = player;
        lastPlayedCards.clear();
        lastPlayedCards.addAll(playedCards);
        tablePile.addAll(playedCards);

        player.getHand().removeAll(playedCards);
        player.sendHand();

        lastDeclaredRank = currentTargetRank;
        int numCards = playedCards.size();
        broadcast(new Message(MessageType.LOBBY_UPDATE,
                player.getUsername() + " положил " + numCards + " карт(ы) как: " + getRankNameInRussian(currentTargetRank)));

        if (player.getHand().isEmpty()) {
            broadcast(new Message(MessageType.LOBBY_UPDATE, "🏆 " + player.getUsername() + " ПОБЕДИЛ!"));
            broadcast(new Message(MessageType.GAME_OVER, player.getUsername()));

            dbManager.recordWin(player.getUsername());
            for (ClientHandler p : players) {
                if (p != player) dbManager.recordLoss(p.getUsername());
            }
            return;
        }

        advanceTargetRank();
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        nextTurn();
    }

    public synchronized void handleCallBluff(ClientHandler caller) {
        if (lastPlayedCards.isEmpty()) {
            caller.sendMessage(new Message(MessageType.LOBBY_UPDATE, "На столе пусто! Блефовать пока нельзя."));
            caller.sendMessage(new Message(MessageType.YOUR_TURN, getRankNameInRussian(currentTargetRank)));
            return;
        }

        if (caller.getBullets() <= 0) {
            caller.sendMessage(new Message(MessageType.LOBBY_UPDATE, "У вас закончились патроны!"));
            caller.sendMessage(new Message(MessageType.YOUR_TURN, getRankNameInRussian(currentTargetRank)));
            return;
        }

        caller.useBullet();

        boolean lied = false;
        for (Card c : lastPlayedCards) {
            if (c.rank() != lastDeclaredRank) {
                lied = true;
                break;
            }
        }

        if (lied) {
            broadcast(new Message(MessageType.LOBBY_UPDATE, "🔥 БЛЕФ РАСКРЫТ! " + lastPlayer.getUsername() + " забирает все карты (" + tablePile.size() + " шт.)!"));
            lastPlayer.getHand().addAll(tablePile);
            lastPlayer.sendHand();
            caller.addBullet();
            caller.sendMessage(new Message(MessageType.LOBBY_UPDATE, "Вы были правы! Патрон возвращен."));
            currentPlayerIndex = players.indexOf(caller); // Ход переходит тому, кто успешно вскрыл блеф
        } else {
            broadcast(new Message(MessageType.LOBBY_UPDATE, "❌ ОШИБКА! " + lastPlayer.getUsername() + " говорил правду. " + caller.getUsername() + " забирает карты!"));
            caller.getHand().addAll(tablePile);
            caller.sendHand();
            caller.sendMessage(new Message(MessageType.LOBBY_UPDATE, "Вы ошиблись. Один патрон сгорел."));
            currentPlayerIndex = players.indexOf(lastPlayer); // Ход остается у честного игрока
        }

        tablePile.clear();
        lastPlayedCards.clear();
        currentTargetRank = lastDeclaredRank; // Номинал не повышается после вскрытия
        nextTurn();
    }

    // Логика последовательного повышения номинала
    private void advanceTargetRank() {
        int nextOrdinal = (currentTargetRank.ordinal() + 1) % Rank.values().length;
        currentTargetRank = Rank.values()[nextOrdinal];
    }

    private String getRankNameInRussian(Rank rank) {
        return switch (rank) {
            case TWO -> "Двойки"; case THREE -> "Тройки"; case FOUR -> "Четверки";
            case FIVE -> "Пятерки"; case SIX -> "Шестерки"; case SEVEN -> "Семерки";
            case EIGHT -> "Восьмерки"; case NINE -> "Девятки"; case TEN -> "Десятки";
            case JACK -> "Валеты"; case QUEEN -> "Дамы"; case KING -> "Короли"; case ACE -> "Тузы";
        };
    }

    public static void main(String[] args) {
        new GameServer().start();
    }
}