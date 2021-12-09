package graphql.schema.diffing;

public class EditOperation {

    public EditOperation(Operation operation, String description, Object details) {
        this.operation = operation;
        this.description = description;
        this.details = details;
    }

    private Operation operation;
    private String description;
    private Object details;

    enum Operation {
        CHANGE_VERTEX, DELETE_VERTEX, INSERT_VERTEX, CHANGE_EDGE, INSERT_EDGE, DELETE_EDGE
    }

    public Operation getOperation() {
        return operation;
    }

    public <T> T getDetails() {
        return (T) details;
    }

    @Override
    public String toString() {
        return "EditOperation{" +
                "operation=" + operation +
                ", description='" + description + '\'' +
                '}';
    }
}
