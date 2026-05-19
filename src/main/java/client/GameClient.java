package client;

import com.google.gson.Gson;
import shared.Message;
import shared.MessageType;

import javax.swing.*;
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

    private JTextField serverIpField; // Новое поле для IP
    private JComboBox<Integer> playersCountBox;
    private JTextField nicknameField;
    private JButton connectButton;
    private JButton readyButton;
    private JTextArea logArea;
    private JPanel handPanel;
    private JButton playCardsButton;
    private JButton callBluffButton;
    private JLabel bulletsLabel;

    private final List<shared.Card> selectedCards = new ArrayList<>();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final Gson gson = new Gson();

    public GameClient() {
        setTitle("Карточный блеф");
        setSize(850, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel topPanel = new JPanel();

        // --- Добавлено поле для IP сервера ---
        topPanel.add(new JLabel("IP:"));
        serverIpField = new JTextField("127.0.0.1", 8);
        topPanel.add(serverIpField);
        // ---------------------------------------

        topPanel.add(new JLabel("Игроков:"));
        playersCountBox = new JComboBox<>(new Integer[]{2, 3, 4});
        topPanel.add(playersCountBox);
        topPanel.add(new JLabel(" Ник:"));
        nicknameField = new JTextField("Игрок", 8);
        topPanel.add(nicknameField);
        connectButton = new JButton("Подключиться");
        readyButton = new JButton("Я готов!");
        readyButton.setEnabled(false);
        topPanel.add(connectButton);
        topPanel.add(readyButton);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(new Color(34, 139, 34));
        tablePanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        handPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JScrollPane handScroll = new JScrollPane(handPanel);
        handScroll.setPreferredSize(new Dimension(850, 130));
        handScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        handScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel actionPanel = new JPanel();
        playCardsButton = new JButton("Сделать ход");
        callBluffButton = new JButton("Блеф!");
        playCardsButton.setEnabled(false);
        callBluffButton.setEnabled(false);
        bulletsLabel = new JLabel("Патроны: O O O ");
        bulletsLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        actionPanel.add(playCardsButton);
        actionPanel.add(callBluffButton);
        actionPanel.add(bulletsLabel);

        bottomPanel.add(handScroll, BorderLayout.CENTER);
        bottomPanel.add(actionPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(tablePanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setupListeners();
    }

    private void setupListeners() {
        connectButton.addActionListener(e -> connectToServer());
        readyButton.addActionListener(e -> sendReadyStatus());

        playCardsButton.addActionListener(e -> {
            if (selectedCards.isEmpty() || selectedCards.size() > 4) {
                JOptionPane.showMessageDialog(this, "Для хода выберите от 1 до 4 карт!");
                return;
            }
            out.println(gson.toJson(new Message(MessageType.PLAY_TURN, gson.toJson(selectedCards))));
            disableActionButtons();
            selectedCards.clear();
        });

        callBluffButton.addActionListener(e -> {
            out.println(gson.toJson(new Message(MessageType.CALL_BLUFF, "Блеф!")));
            disableActionButtons();
        });
    }

    private void connectToServer() {
        try {
            String serverAddress = serverIpField.getText().trim();
            if (serverAddress.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Укажите IP-адрес сервера!");
                return;
            }

            int expectedPlayers = (Integer) playersCountBox.getSelectedItem();
            String nickname = nicknameField.getText().trim();
            if (nickname.isEmpty()) nickname = "Player_" + new java.util.Random().nextInt(100);

            logArea.append("Подключение к серверу " + serverAddress + "...\n");
            socket = new Socket(serverAddress, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            connectButton.setEnabled(false);
            serverIpField.setEnabled(false);
            playersCountBox.setEnabled(false);
            nicknameField.setEnabled(false);
            readyButton.setEnabled(true);

            String payload = expectedPlayers + "," + nickname;
            out.println(gson.toJson(new Message(MessageType.CONNECT, payload)));
            new Thread(this::listenForMessages).start();
        } catch (IOException ex) {
            logArea.append("Ошибка подключения: " + ex.getMessage() + "\n");
        }
    }

    private void sendReadyStatus() {
        readyButton.setEnabled(false);
        out.println(gson.toJson(new Message(MessageType.PLAYER_READY, "Готов")));
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
                    case YOUR_TURN -> SwingUtilities.invokeLater(() -> {
                        logArea.setText(""); // Очищаем чат от прошлых ходов
                        playCardsButton.setEnabled(true);
                        callBluffButton.setEnabled(true);
                        logArea.append("=========================================\n");
                        logArea.append("⚡ ВАШ ХОД! Требуемый ранг: [" + message.getPayload().toUpperCase() + "]\n");
                        logArea.append("=========================================\n");
                        logArea.append("Выберите от 1 до 4 карт и нажмите 'Сделать ход'.\n");
                        logArea.append("Либо нажмите 'Блеф!', если не верите предыдущему игроку.\n");
                    });
                    case GAME_OVER -> {
                        String winner = message.getPayload();
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this,
                                    "Игра окончена!\nПобедитель: " + winner + "\n\nДля новой игры перезапустите приложение.",
                                    "Конец игры", JOptionPane.INFORMATION_MESSAGE);
                            disableActionButtons();
                        });
                    }
                    case BULLETS_UPDATE -> {
                        int count = Integer.parseInt(message.getPayload());
                        String bulletsText = "Патроны: " + "O ".repeat(Math.max(0, count));
                        if (count == 0) bulletsText = "Патроны: ПУСТО";
                        String finalBulletsText = bulletsText;
                        SwingUtilities.invokeLater(() -> bulletsLabel.setText(finalBulletsText));
                    }
                    default -> SwingUtilities.invokeLater(() -> logArea.append("Сервер: " + message.getPayload() + "\n"));
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> logArea.append("Связь с сервером прервана.\n"));
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
            Color cardColor = (card.suit() == shared.Suit.HEARTS || card.suit() == shared.Suit.DIAMONDS) ? Color.RED : Color.BLACK;

            CardButton cardButton = new CardButton(rankStr, suitSymbol, cardColor);
            cardButton.addActionListener(e -> {
                if (selectedCards.contains(card)) {
                    selectedCards.remove(card);
                    cardButton.setSelectedState(false);
                } else {
                    selectedCards.add(card);
                    cardButton.setSelectedState(true);
                }
            });
            handPanel.add(cardButton);
        }
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void disableActionButtons() {
        playCardsButton.setEnabled(false);
        callBluffButton.setEnabled(false);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameClient().setVisible(true));
    }

    // --- Внутренний класс графической карты ---
    class CardButton extends JButton {
        private final String rank;
        private final String suit;
        private final Color color;
        private boolean isSelectedState = false;

        public CardButton(String rank, String suit, Color color) {
            this.rank = rank;
            this.suit = suit;
            this.color = color;
            setPreferredSize(new Dimension(70, 100));
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

            if (isSelectedState) g2.setColor(new Color(200, 230, 255));
            else g2.setColor(Color.WHITE);
            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 15, 15);

            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 15, 15);

            g2.setColor(color);
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            g2.drawString(rank, 8, 22);

            g2.setFont(new Font("Arial", Font.PLAIN, 14));
            g2.drawString(suit, 8, 38);

            g2.setFont(new Font("Arial", Font.PLAIN, 40));
            FontMetrics fm = g2.getFontMetrics();
            int suitX = (getWidth() - fm.stringWidth(suit)) / 2;
            int suitY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent() + 5;
            g2.drawString(suit, suitX, suitY);

            g2.setFont(new Font("Arial", Font.BOLD, 16));
            fm = g2.getFontMetrics();
            g2.drawString(rank, getWidth() - fm.stringWidth(rank) - 8, getHeight() - 8);
        }
    }
}