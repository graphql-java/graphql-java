package graphql.execution.batched;

import graphql.language.Field;
import graphql.schema.GraphQLObjectType;

import java.util.List;
import java.util.Map;

class GraphQLExecutionNode {

    private final GraphQLObjectType parentType;
    private final Map<String, List<Field>> fields;
    private final List<GraphQLExecutionNodeDatum> data;

    /**
     * <p>Constructor for GraphQLExecutionNode.</p>
     *
     * @param parentType a {@link graphql.schema.GraphQLObjectType} object.
     * @param fields a {@link java.util.Map} object.
     * @param data a {@link java.util.List} object.
     */
    public GraphQLExecutionNode(GraphQLObjectType parentType,
                                Map<String, List<Field>> fields,
                                List<GraphQLExecutionNodeDatum> data) {
        this.parentType = parentType;
        this.fields = fields;
        this.data = data;
    }

    /**
     * <p>Getter for the field <code>parentType</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLObjectType} object.
     */
    public GraphQLObjectType getParentType() {
        return parentType;
    }

    /**
     * <p>Getter for the field <code>fields</code>.</p>
     *
     * @return a {@link java.util.Map} object.
     */
    public Map<String, List<Field>> getFields() {
        return fields;
    }

    /**
     * <p>Getter for the field <code>data</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLExecutionNodeDatum> getData() {
        return data;
    }
}
