package graphql.schema.diffing;

public class EditOperation {

    private EditOperation(Operation operation,
                          String description,
                          Vertex sourceVertex,
                          Vertex targetVertex,
                          Edge sourceEdge,
                          Edge targetEdge) {
        this.operation = operation;
        this.description = description;
        this.sourceVertex = sourceVertex;
        this.targetVertex = targetVertex;
        this.sourceEdge = sourceEdge;
        this.targetEdge = targetEdge;
    }

    public static EditOperation deleteVertex(String description, Vertex sourceVertex,Vertex targetVertex) {
        return new EditOperation(Operation.DELETE_VERTEX, description, sourceVertex, targetVertex, null, null);
    }

    public static EditOperation insertVertex(String description,Vertex sourceVertex, Vertex targetVertex) {
        return new EditOperation(Operation.INSERT_VERTEX, description, sourceVertex, targetVertex, null, null);
    }

    public static EditOperation changeVertex(String description, Vertex sourceVertex, Vertex targetVertex) {
        return new EditOperation(Operation.CHANGE_VERTEX, description, sourceVertex, targetVertex, null, null);
    }

    public static EditOperation deleteEdge(String description, Edge sourceEdge) {
        return new EditOperation(Operation.DELETE_EDGE, description, null, null, sourceEdge, null);
    }

    public static EditOperation insertEdge(String description, Edge targetEdge) {
        return new EditOperation(Operation.INSERT_EDGE, description, null, null, null, targetEdge);
    }

    public static EditOperation changeEdge(String description, Edge sourceEdge, Edge targetEdge) {
        return new EditOperation(Operation.CHANGE_EDGE, description, null, null, sourceEdge, targetEdge);
    }

    private Operation operation;
    private String description;
    private Vertex sourceVertex;
    private Vertex targetVertex;
    private Edge sourceEdge;
    private Edge targetEdge;


    enum Operation {
        CHANGE_VERTEX, DELETE_VERTEX, INSERT_VERTEX, CHANGE_EDGE, INSERT_EDGE, DELETE_EDGE
    }

    public Operation getOperation() {
        return operation;
    }


    public Vertex getSourceVertex() {
        return sourceVertex;
    }

    public Vertex getTargetVertex() {
        return targetVertex;
    }

    @Override
    public String toString() {
        return "EditOperation{" +
                "operation=" + operation +
                ", description='" + description + '\'' +
                '}';
    }
}
