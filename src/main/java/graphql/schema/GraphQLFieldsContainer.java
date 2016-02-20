package graphql.schema;

import java.util.List;


/**
 * <p>GraphQLFieldsContainer interface.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public interface GraphQLFieldsContainer extends GraphQLType{

    /**
     * <p>getFieldDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.schema.GraphQLFieldDefinition} object.
     */
    GraphQLFieldDefinition getFieldDefinition(String name);

    /**
     * <p>getFieldDefinitions.</p>
     *
     * @return a {@link java.util.List} object.
     */
    List<GraphQLFieldDefinition> getFieldDefinitions();
}
