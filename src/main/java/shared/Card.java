package shared;

public record Card(Suit suit, Rank rank) {
    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}