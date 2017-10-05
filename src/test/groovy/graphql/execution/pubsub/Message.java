package graphql.execution.pubsub;

/**
 * Our simple message object for testing
 */
public class Message {
    private final String sender;
    private final String text;

    public Message(String sender, String text) {
        this.sender = sender;
        this.text = text;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "Message{" +
                "sender='" + sender + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
