package graphql.schema;


import graphql.language.Field;

import java.util.List;
import java.util.Map;

/**
 * <p>DataFetchingEnvironment class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class DataFetchingEnvironment {
    private final Object source;
    private final Map<String, Object> arguments;
    private final Object context;
    private final List<Field> fields;
    private final GraphQLOutputType fieldType;
    private final GraphQLType parentType;
    private final GraphQLSchema graphQLSchema;

    /**
     * <p>Constructor for DataFetchingEnvironment.</p>
     *
     * @param source a {@link java.lang.Object} object.
     * @param arguments a {@link java.util.Map} object.
     * @param context a {@link java.lang.Object} object.
     * @param fields a {@link java.util.List} object.
     * @param fieldType a {@link graphql.schema.GraphQLOutputType} object.
     * @param parentType a {@link graphql.schema.GraphQLType} object.
     * @param graphQLSchema a {@link graphql.schema.GraphQLSchema} object.
     */
    public DataFetchingEnvironment(Object source, Map<String, Object> arguments, Object context, List<Field> fields, GraphQLOutputType fieldType, GraphQLType parentType, GraphQLSchema graphQLSchema) {
        this.source = source;
        this.arguments = arguments;
        this.context = context;
        this.fields = fields;
        this.fieldType = fieldType;
        this.parentType = parentType;
        this.graphQLSchema = graphQLSchema;
    }

    /**
     * <p>Getter for the field <code>source</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getSource() {
        return source;
    }

    /**
     * <p>Getter for the field <code>arguments</code>.</p>
     *
     * @return a {@link java.util.Map} object.
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * <p>containsArgument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a boolean.
     */
    public boolean containsArgument(String name) {
        return arguments.containsKey(name);
    }

    /**
     * <p>getArgument.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param <T> a T object.
     * @return a T object.
     */
    public <T> T getArgument(String name) {
        return (T) arguments.get(name);
    }

    /**
     * <p>Getter for the field <code>context</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getContext() {
        return context;
    }

    /**
     * <p>Getter for the field <code>fields</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Field> getFields() {
        return fields;
    }

    /**
     * <p>Getter for the field <code>fieldType</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLOutputType} object.
     */
    public GraphQLOutputType getFieldType() {
        return fieldType;
    }

    /**
     * <p>Getter for the field <code>parentType</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLType} object.
     */
    public GraphQLType getParentType() {
        return parentType;
    }

    /**
     * <p>Getter for the field <code>graphQLSchema</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLSchema} object.
     */
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }
}
