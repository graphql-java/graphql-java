package graphql.schema.diffing;

import graphql.Internal;

import java.util.Objects;

/**
 * An edit operation between two graphs can be one of six types:
 * insert vertex,
 * delete vertex,
 * change vertex,
 * insert edge,
 * delete edge,
 * change edge
 */
@Internal
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

    public static EditOperation deleteVertex(String description, Vertex sourceVertex, Vertex targetVertex) {
        return new EditOperation(Operation.DELETE_VERTEX, description, sourceVertex, targetVertex, null, null);
    }

    public static EditOperation insertVertex(String description, Vertex sourceVertex, Vertex targetVertex) {
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


    public enum Operation {
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

    public Edge getSourceEdge() {
        return sourceEdge;
    }

    public Edge getTargetEdge() {
        return targetEdge;
    }

    @Override
    public String toString() {
        return "EditOperation{" +
                "operation=" + operation +
                ", description='" + description + '\'' +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EditOperation that = (EditOperation) o;
        return operation == that.operation && Objects.equals(description, that.description) && Objects.equals(sourceVertex, that.sourceVertex) && Objects.equals(targetVertex, that.targetVertex) && Objects.equals(sourceEdge, that.sourceEdge) && Objects.equals(targetEdge, that.targetEdge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operation, description, sourceVertex, targetVertex, sourceEdge, targetEdge);
    }
}
