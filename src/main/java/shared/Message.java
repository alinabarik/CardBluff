package shared;

public class Message {
    private MessageType type;
    private String payload;

    public Message() {}

    public Message(MessageType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() { return type; }
    public String getPayload() { return payload; }
}
