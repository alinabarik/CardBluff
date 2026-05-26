package client;

import com.google.gson.Gson;
import shared.Message;
import shared.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class GameClient extends JFrame {
    private static final int SERVER_PORT = 8080;

    private JTextField serverIpField;
    private JComboBox<Integer> playersCountBox;
    private JTextField nicknameField;
    private JButton connectButton;
    private JButton readyButton;
    private JPanel handPanel;
    private TablePanel tableArea;
    private JButton playCardsButton;
    private JButton callBluffButton;
    private JLabel bulletsLabel;

    private final List<shared.Card> selectedCards = new ArrayList<>();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final Gson gson = new Gson();

    private boolean isMyTurn = false;

    public GameClient() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        setTitle("Карточный блеф");
        setSize(950, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // --- ВЕРХНЯЯ ПАНЕЛЬ ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        topPanel.setBackground(new Color(240, 240, 245));
        topPanel.add(new JLabel("IP сервера:"));
        serverIpField = new JTextField("127.0.0.1", 10);
        topPanel.add(serverIpField);
        topPanel.add(new JLabel("Игроков:"));
        playersCountBox = new JComboBox<>(new Integer[]{2, 3, 4});
        topPanel.add(playersCountBox);
        topPanel.add(new JLabel("Ник:"));
        nicknameField = new JTextField("Игрок", 10);
        topPanel.add(nicknameField);
        connectButton = new JButton("Подключиться");
        readyButton = new JButton("Я готов!");
        readyButton.setEnabled(false);
        topPanel.add(connectButton);
        topPanel.add(readyButton);

        // --- ЦЕНТРАЛЬНАЯ ПАНЕЛЬ (Стол) ---
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        centerPanel.setBackground(new Color(40, 45, 50));

        tableArea = new TablePanel();
        tableArea.setBackground(new Color(30, 90, 45));
        tableArea.setBorder(BorderFactory.createLineBorder(new Color(20, 60, 30), 4));
        centerPanel.add(tableArea, BorderLayout.CENTER);

        // --- НИЖНЯЯ ПАНЕЛЬ (Рука + Кнопки) ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(240, 240, 245));

        handPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        handPanel.setBackground(new Color(220, 225, 230));
        JScrollPane handScroll = new JScrollPane(handPanel);
        handScroll.setPreferredSize(new Dimension(950, 160));
        handScroll.setBorder(null);
        handScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        actionPanel.setBackground(new Color(240, 240, 245));
        playCardsButton = new JButton("Сбросить карты");
        playCardsButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        callBluffButton = new JButton("Кричать: БЛЕФ!");
        callBluffButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        callBluffButton.setForeground(new Color(180, 0, 0));
        playCardsButton.setEnabled(false);
        callBluffButton.setEnabled(false);
        bulletsLabel = new JLabel("Патроны: O O O ");
        bulletsLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        actionPanel.add(playCardsButton);
        actionPanel.add(callBluffButton);
        actionPanel.add(bulletsLabel);

        bottomPanel.add(handScroll, BorderLayout.CENTER);
        bottomPanel.add(actionPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setupListeners();
    }

    private void setupListeners() {
        connectButton.addActionListener(e -> connectToServer());
        readyButton.addActionListener(e -> sendReadyStatus());

        playCardsButton.addActionListener(e -> {
            out.println(gson.toJson(new Message(MessageType.PLAY_TURN, gson.toJson(selectedCards))));
            isMyTurn = false;
            updateActionButtons();
            selectedCards.clear();
        });

        callBluffButton.addActionListener(e -> {
            out.println(gson.toJson(new Message(MessageType.CALL_BLUFF, "Блеф!")));
            isMyTurn = false;
            updateActionButtons();
        });
    }

    private void updateActionButtons() {
        if (isMyTurn) {
            int totalHandCards = handPanel.getComponentCount();

            // Если осталась только 1 карта, разрешаем кинуть 1, иначе МИНИМУМ 2
            if (totalHandCards <= 1) {
                playCardsButton.setEnabled(selectedCards.size() == 1);
            } else {
                playCardsButton.setEnabled(selectedCards.size() >= 2 && selectedCards.size() <= 4);
            }
            callBluffButton.setEnabled(tableArea.getCardsCount() > 0);
        } else {
            playCardsButton.setEnabled(false);
            callBluffButton.setEnabled(false);
        }
    }

    private void connectToServer() {
        try {
            String serverAddress = serverIpField.getText().trim();
            if (serverAddress.isEmpty()) return;

            int expectedPlayers = (Integer) playersCountBox.getSelectedItem();
            String nickname = nicknameField.getText().trim();
            if (nickname.isEmpty()) nickname = "Player_" + new java.util.Random().nextInt(100);

            socket = new Socket(serverAddress, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            connectButton.setEnabled(false);
            serverIpField.setEnabled(false);
            playersCountBox.setEnabled(false);
            nicknameField.setEnabled(false);
            readyButton.setEnabled(true);
            tableArea.setStatus("Подключение успешно. Ожидание...");

            String payload = expectedPlayers + "," + nickname;
            out.println(gson.toJson(new Message(MessageType.CONNECT, payload)));
            new Thread(this::listenForMessages).start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка: " + ex.getMessage());
        }
    }

    private void sendReadyStatus() {
        readyButton.setEnabled(false);
        out.println(gson.toJson(new Message(MessageType.PLAYER_READY, "Готов")));
    }

    // Возврат интерфейса в стартовое состояние Лобби
    private void resetToLobby() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        isMyTurn = false;
        tableArea.updateTable(0, "-", "-");
        tableArea.setStatus("Игра окончена. Можно начать заново.");
        handPanel.removeAll();
        handPanel.repaint();

        connectButton.setEnabled(true);
        serverIpField.setEnabled(true);
        playersCountBox.setEnabled(true);
        nicknameField.setEnabled(true);
        readyButton.setEnabled(false);
        playCardsButton.setEnabled(false);
        callBluffButton.setEnabled(false);
        bulletsLabel.setText("Патроны: O O O ");
    }

    private void listenForMessages() {
        try {
            String jsonLine;
            while ((jsonLine = in.readLine()) != null) {
                Message message = gson.fromJson(jsonLine, Message.class);

                switch (message.getType()) {
                    case CARDS_DEALT -> {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<shared.Card>>(){}.getType();
                        java.util.List<shared.Card> hand = gson.fromJson(message.getPayload(), listType);
                        SwingUtilities.invokeLater(() -> renderHand(hand));
                    }
                    case TABLE_UPDATE -> {
                        String[] parts = message.getPayload().split(";");
                        int cardsOnTable = Integer.parseInt(parts[0]);
                        String lastRank = parts.length > 1 ? parts[1] : "-";
                        String targetRank = parts.length > 2 ? parts[2] : "-";
                        SwingUtilities.invokeLater(() -> {
                            tableArea.updateTable(cardsOnTable, lastRank, targetRank);
                            updateActionButtons();
                        });
                    }
                    case YOUR_TURN -> SwingUtilities.invokeLater(() -> {
                        isMyTurn = true;
                        updateActionButtons();
                        tableArea.setStatus("ВАШ ХОД! Требуется: " + message.getPayload().toUpperCase());
                    });
                    case GAME_OVER -> {
                        String winner = message.getPayload();
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Победитель: " + winner, "Конец игры", JOptionPane.INFORMATION_MESSAGE);
                            resetToLobby();
                        });
                        break; // Выходим из цикла прослушивания, сокет закрыт
                    }
                    case BULLETS_UPDATE -> {
                        int count = Integer.parseInt(message.getPayload());
                        String bulletsText = "Патроны: " + "O ".repeat(Math.max(0, count));
                        if (count == 0) bulletsText = "Патроны: ПУСТО";
                        String finalBulletsText = bulletsText;
                        SwingUtilities.invokeLater(() -> bulletsLabel.setText(finalBulletsText));
                    }
                    case LOBBY_UPDATE -> SwingUtilities.invokeLater(() -> {
                        tableArea.setStatus(message.getPayload());
                    });
                    default -> {}
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                if (!connectButton.isEnabled()) {
                    tableArea.setStatus("Связь с сервером прервана.");
                    resetToLobby();
                }
            });
        }
    }

    private void renderHand(List<shared.Card> hand) {
        handPanel.removeAll();
        selectedCards.clear();
        for (shared.Card card : hand) {
            String suitSymbol = switch (card.suit()) {
                case HEARTS -> "♥"; case DIAMONDS -> "♦"; case CLUBS -> "♣"; case SPADES -> "♠";
            };
            String rankStr = switch (card.rank()) {
                case TWO -> "2"; case THREE -> "3"; case FOUR -> "4"; case FIVE -> "5";
                case SIX -> "6"; case SEVEN -> "7"; case EIGHT -> "8"; case NINE -> "9"; case TEN -> "10";
                case JACK -> "В"; case QUEEN -> "Д"; case KING -> "К"; case ACE -> "Т";
            };
            Color cardColor = (card.suit() == shared.Suit.HEARTS || card.suit() == shared.Suit.DIAMONDS) ? new Color(200, 30, 30) : new Color(30, 30, 30);

            CardButton cardButton = new CardButton(rankStr, suitSymbol, cardColor);
            cardButton.addActionListener(e -> {
                if (selectedCards.contains(card)) {
                    selectedCards.remove(card);
                    cardButton.setSelectedState(false);
                } else {
                    if (selectedCards.size() >= 4) return;
                    selectedCards.add(card);
                    cardButton.setSelectedState(true);
                }
                updateActionButtons();
            });
            handPanel.add(cardButton);
        }
        handPanel.revalidate();
        handPanel.repaint();
        updateActionButtons();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameClient().setVisible(true));
    }

    class TablePanel extends JPanel {
        private int cardsCount = 0;
        private String lastDeclared = "-";
        private String target = "-";
        private String statusMsg = "Добро пожаловать в игру!";

        public void updateTable(int count, String last, String nextTarget) {
            this.cardsCount = count;
            this.lastDeclared = last;
            this.target = nextTarget;
            repaint();
        }

        public void setStatus(String msg) {
            this.statusMsg = msg;
            repaint();
        }

        public int getCardsCount() {
            return cardsCount;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));

            if (!target.equals("-")) {
                g2.drawString("Требуется положить: " + target, 15, 30);
            }
            if (cardsCount > 0 && !lastDeclared.equals("-")) {
                g2.drawString("Последними выложили: " + lastDeclared, 15, 55);
            }

            // Отрисовка статуса по центру снизу
            if (!statusMsg.isEmpty()) {
                g2.setColor(new Color(255, 230, 100));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(statusMsg, (getWidth() - fm.stringWidth(statusMsg)) / 2, getHeight() - 25);
            }

            if (cardsCount == 0) {
                return;
            }

            int startX = getWidth() / 2 - 40;
            int startY = getHeight() / 2 - 60;

            for (int i = 0; i < cardsCount; i++) {
                double angle = Math.toRadians(-15 + (i * 7 % 30));
                int offsetX = i * 4;
                int offsetY = i * 2;

                g2.translate(startX + offsetX + 40, startY + offsetY + 60);
                g2.rotate(angle);

                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(-37, -57, 80, 120, 10, 10);

                g2.setColor(new Color(25, 60, 120));
                g2.fillRoundRect(-40, -60, 80, 120, 10, 10);
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(-40, -60, 80, 120, 10, 10);

                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillOval(-20, -20, 40, 40);

                g2.rotate(-angle);
                g2.translate(-(startX + offsetX + 40), -(startY + offsetY + 60));
            }
        }
    }

    class CardButton extends JButton {
        private final String rank;
        private final String suit;
        private final Color color;
        private boolean isSelectedState = false;

        public CardButton(String rank, String suit, Color color) {
            this.rank = rank;
            this.suit = suit;
            this.color = color;
            setPreferredSize(new Dimension(80, 120));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        public void setSelectedState(boolean selected) {
            this.isSelectedState = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillRoundRect(5, 5, getWidth() - 5, getHeight() - 5, 12, 12);

            if (isSelectedState) {
                g2.setPaint(new GradientPaint(0, 0, new Color(230, 245, 255), 0, getHeight(), new Color(190, 220, 255)));
            } else {
                g2.setColor(Color.WHITE);
            }
            g2.fillRoundRect(0, 0, getWidth() - 6, getHeight() - 6, 12, 12);

            g2.setColor(new Color(200, 200, 200));
            g2.drawRoundRect(0, 0, getWidth() - 6, getHeight() - 6, 12, 12);

            g2.setColor(color);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            g2.drawString(rank, 8, 24);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            g2.drawString(suit, 8, 42);

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 46));
            FontMetrics fm = g2.getFontMetrics();
            int suitX = (getWidth() - 6 - fm.stringWidth(suit)) / 2;
            int suitY = (getHeight() - 6 - fm.getHeight()) / 2 + fm.getAscent() + 5;
            g2.drawString(suit, suitX, suitY);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            fm = g2.getFontMetrics();
            g2.drawString(rank, getWidth() - 6 - fm.stringWidth(rank) - 8, getHeight() - 6 - 8);
        }
    }
}