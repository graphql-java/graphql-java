package graphql.schema.diffing;

public class EditOperation {
    public EditOperation(Operation operation, String detail) {
        this.operation = operation;
        this.detail = detail;
    }

    private Operation operation;
    private String detail;

    enum Operation {
        CHANGE_VERTEX, DELETE_VERTEX, INSERT_VERTEX, CHANGE_EDGE, INSERT_EDGE, DELETE_EDGE
    }

    @Override
    public String toString() {
        return "EditOperation{" +
                "operation=" + operation +
                ", detail='" + detail + '\'' +
                '}';
    }
}
