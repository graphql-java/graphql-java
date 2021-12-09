package graphql.schema.diffing;

import graphql.Assert;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static graphql.Assert.assertNotNull;

/**
 * Higher level GraphQL semantic assigned to
 */
public class DefaultGraphEditOperationAnalyzer {

    private GraphQLSchema oldSchema;
    private GraphQLSchema newSchema;
    private SchemaGraph oldSchemaGraph;
    private SchemaGraph newSchemaGraph;
    private SchemaChangedHandler schemaChangedHandler;

    public DefaultGraphEditOperationAnalyzer(GraphQLSchema oldSchema, GraphQLSchema newSchema, SchemaGraph oldSchemaGraph, SchemaGraph newSchemaGraph, SchemaChangedHandler schemaChangedHandler) {
        this.oldSchema = oldSchema;
        this.newSchema = newSchema;
        this.oldSchemaGraph = oldSchemaGraph;
        this.newSchemaGraph = newSchemaGraph;
        this.schemaChangedHandler = schemaChangedHandler;
    }

    public void analyzeEdits(List<EditOperation> editOperations) {
        for (EditOperation editOperation : editOperations) {
            // 5 edit operations
            if (editOperation.getOperation() == EditOperation.Operation.DELETE_VERTEX) {
                Vertex deletedVertex = editOperation.getDetails();
                if (!deletedVertex.getType().equals("Field")) {
                    continue;
                }
                String fieldName = deletedVertex.getProperty("name");
                // find the "dummy-type" vertex for this field
                Edge edgeToDummyTypeVertex = assertNotNull(oldSchemaGraph.getSingleAdjacentEdge(deletedVertex, edge -> edge.getTwo().getType().equals(SchemaGraphFactory.DUMMY_TYPE_VERTICE)));
                Vertex dummyTypeVertex = edgeToDummyTypeVertex.getTwo();

                Edge edgeToObjectOrInterface = assertNotNull(oldSchemaGraph.getSingleAdjacentEdge(deletedVertex, edge ->
                        edge.getOne().getType().equals("Object") || edge.getOne().getType().equals("Interface") ||
                        edge.getTwo().getType().equals("Object") || edge.getTwo().getType().equals("Interface")
                ));
                Edge edgeFromDummyTypeToType = Assert.assertNotNull(oldSchemaGraph.getSingleAdjacentEdge(dummyTypeVertex, edge -> edge != edgeToDummyTypeVertex));

                List<EditOperation> relatedEditOperations = searchForOperations(editOperations, Arrays.asList(
                        eo -> {
                            if (eo.getOperation() == EditOperation.Operation.DELETE_VERTEX) {
                                return eo.getDetails() == dummyTypeVertex;
                            }
                            return false;
                        },
                        eo -> {
                            if (eo.getOperation() == EditOperation.Operation.DELETE_EDGE) {
                                return eo.getDetails() == edgeToObjectOrInterface;
                            }
                            return false;
                        },
                        eo -> {
                            if (eo.getOperation() == EditOperation.Operation.DELETE_EDGE) {
                                return eo.getDetails() == edgeToDummyTypeVertex;
                            }
                            return false;
                        },
                        eo -> {
                            if (eo.getOperation() == EditOperation.Operation.DELETE_EDGE) {
                                return eo.getDetails() == edgeFromDummyTypeToType;
                            }
                            return false;
                        })
                );
                if (relatedEditOperations.size() == 4) {
                    schemaChangedHandler.fieldRemoved("Field " + edgeToObjectOrInterface.getOne().get("name") + "." + fieldName + " removed");
                }
            }
        }
    }

    private List<EditOperation> searchForOperations(List<EditOperation> editOperations, List<Predicate<EditOperation>> predicates) {
        List<EditOperation> result = new ArrayList<>();
        for (EditOperation editOperation : editOperations) {
            for (Predicate<EditOperation> predicate : predicates) {
                if (predicate.test(editOperation)) {
                    result.add(editOperation);
                }
            }
        }
        return result;
    }

}
