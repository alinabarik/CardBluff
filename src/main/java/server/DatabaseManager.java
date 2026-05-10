package server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // Путь к файлу базы данных (создастся автоматически в папке проекта)
    private static final String DB_URL = "jdbc:sqlite:cardbluff.db";

    public DatabaseManager() {
        initializeDatabase();
    }

    // Создание таблицы, если её еще нет
    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS players (" +
                "username TEXT PRIMARY KEY, " +
                "wins INTEGER DEFAULT 0, " +
                "games_played INTEGER DEFAULT 0)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("База данных успешно инициализирована.");
        } catch (SQLException e) {
            System.err.println("Ошибка БД: " + e.getMessage());
        }
    }

    // Регистрация или авторизация игрока
    public void loginPlayer(String username) {
        String insertSql = "INSERT OR IGNORE INTO players (username, wins, games_played) VALUES (?, 0, 0)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка авторизации: " + e.getMessage());
        }
    }

    // Получение статистики профиля
    public String getPlayerStats(String username) {
        String sql = "SELECT wins, games_played FROM players WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int wins = rs.getInt("wins");
                int gamesPlayed = rs.getInt("games_played");
                return "Профиль [" + username + "] | Побед: " + wins + " | Игр: " + gamesPlayed;
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения статистики: " + e.getMessage());
        }
        return "Статистика недоступна";
    }

    // Запись победы в базу
    public void recordWin(String username) {
        String sql = "UPDATE players SET wins = wins + 1, games_played = games_played + 1 WHERE username = ?";
        updateStats(username, sql);
    }

    // Запись простого участия в игре
    public void recordLoss(String username) {
        String sql = "UPDATE players SET games_played = games_played + 1 WHERE username = ?";
        updateStats(username, sql);
    }

    private void updateStats(String username, String sql) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Ошибка обновления статистики: " + e.getMessage());
        }
    }
}