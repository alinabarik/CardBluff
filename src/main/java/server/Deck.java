package server;

import shared.Card;
import shared.Rank;
import shared.Suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {
    private final List<Card> cards;

    public Deck() {
        cards = new ArrayList<>();
        initializeDeck();
    }

    // Заполняем колоду всеми возможными комбинациями (52 карты)
    private void initializeDeck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    // Перемешиваем колоду
    public void shuffle() {
        Collections.shuffle(cards);
    }

    // Выдаем верхнюю карту из колоды
    public Card drawCard() {
        if (cards.isEmpty()) {
            return null; // Колода закончилась
        }
        // Удаляем и возвращаем последнюю карту в списке (верхнюю)
        return cards.remove(cards.size() - 1);
    }

    // Проверка оставшегося количества карт
    public int cardsLeft() {
        return cards.size();
    }
}