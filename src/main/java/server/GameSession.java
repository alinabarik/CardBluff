package server;

import shared.Card;
import shared.Message;
import shared.MessageType;
import shared.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameSession {
    private final List<ClientHandler> players;
    private boolean gameStarted = false;
    private boolean sessionAborted = false; // Флаг отмены сессии
    private int currentPlayerIndex = -1;

    private final List<Card> tablePile = new ArrayList<>();
    private final List<Card> lastPlayedCards = new ArrayList<>();
    private ClientHandler lastPlayer;
    private Rank currentTargetRank = Rank.TWO;
    private Rank lastDeclaredRank = Rank.TWO;

    private String lastActionMessage = "";

    public GameSession(List<ClientHandler> players) {
        this.players = players;
    }

    public synchronized void broadcast(Message message) {
        for (ClientHandler player : players) player.sendMessage(message);
    }

    // Метод для обработки отключения игрока
    public synchronized void handlePlayerDisconnect(ClientHandler player) {
        if (sessionAborted) return;
        sessionAborted = true;

        String msg = gameStarted ?
                "ABORT:Игрок " + player.getUsername() + " отключился. Игра завершена." :
                "ABORT:Игрок " + player.getUsername() + " покинул лобби до начала игры.";

        broadcast(new Message(MessageType.GAME_OVER, msg));
    }

    public void initSession() {
        broadcast(new Message(MessageType.LOBBY_UPDATE, "Комната собрана! Ждем готовности всех игроков..."));
        checkReadiness();
    }

    public synchronized void checkReadiness() {
        if (gameStarted || sessionAborted) return;

        for (ClientHandler player : players) {
            if (!player.isReady()) return;
        }

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
        broadcast(new Message(MessageType.TABLE_UPDATE, "0;-;-"));
        lastActionMessage = "Карты розданы.";
        startFirstTurn();
    }

    private void startFirstTurn() {
        currentPlayerIndex = new Random().nextInt(players.size());
        currentTargetRank = Rank.TWO;
        nextTurn();
    }

    public synchronized void nextTurn() {
        if (sessionAborted) return;
        ClientHandler activePlayer = players.get(currentPlayerIndex);

        String turnMsg = "Ходит: " + activePlayer.getUsername();

        if (!lastActionMessage.isEmpty()) {
            broadcast(new Message(MessageType.LOBBY_UPDATE, lastActionMessage + " | " + turnMsg));
        } else {
            broadcast(new Message(MessageType.LOBBY_UPDATE, turnMsg));
        }

        String requiredRank = getRankNameInRussian(currentTargetRank);
        activePlayer.sendMessage(new Message(MessageType.YOUR_TURN, requiredRank));
    }

    public synchronized void handlePlayTurn(ClientHandler player, List<Card> playedCards) {
        if (sessionAborted || players.indexOf(player) != currentPlayerIndex) return;

        lastPlayer = player;
        lastPlayedCards.clear();
        lastPlayedCards.addAll(playedCards);
        tablePile.addAll(playedCards);

        player.getHand().removeAll(playedCards);
        player.sendHand();

        lastDeclaredRank = currentTargetRank;
        int numCards = playedCards.size();

        if (player.getHand().isEmpty()) {
            broadcast(new Message(MessageType.TABLE_UPDATE, tablePile.size() + ";" + getRankNameInRussian(lastDeclaredRank) + ";-"));
            broadcast(new Message(MessageType.LOBBY_UPDATE, player.getUsername() + " ПОБЕДИЛ!"));
            broadcast(new Message(MessageType.GAME_OVER, player.getUsername()));
            return;
        }

        advanceTargetRank();

        broadcast(new Message(MessageType.TABLE_UPDATE, tablePile.size() + ";"
                + getRankNameInRussian(lastDeclaredRank) + ";"
                + getRankNameInRussian(currentTargetRank)));

        lastActionMessage = player.getUsername() + " положил " + numCards + " шт. как " + getRankNameInRussian(lastDeclaredRank);

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        nextTurn();
    }

    public synchronized void handleCallBluff(ClientHandler caller) {
        if (sessionAborted) return;

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
            lastActionMessage = caller.getUsername() + " крикнул БЛЕФ! И оказался прав! " + lastPlayer.getUsername() + " забирает карты";
            lastPlayer.getHand().addAll(tablePile);
            lastPlayer.sendHand();
            caller.addBullet();
            caller.sendMessage(new Message(MessageType.LOBBY_UPDATE, "Вы были правы! Патрон возвращен."));
            currentPlayerIndex = players.indexOf(caller);
        } else {
            lastActionMessage = caller.getUsername() + " крикнул БЛЕФ! Но ошибся! " + lastPlayer.getUsername() + " был честен";
            caller.getHand().addAll(tablePile);
            caller.sendHand();
            caller.sendMessage(new Message(MessageType.LOBBY_UPDATE, "Вы ошиблись. Один патрон сгорел."));
            currentPlayerIndex = players.indexOf(lastPlayer);
        }

        tablePile.clear();
        lastPlayedCards.clear();
        currentTargetRank = lastDeclaredRank;
        broadcast(new Message(MessageType.TABLE_UPDATE, "0;-;-"));
        nextTurn();
    }

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
}