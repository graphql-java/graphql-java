package graphql.schema;


/**
 * <p>TypeResolver interface.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public interface TypeResolver {


    /**
     * <p>getType.</p>
     *
     * @param object a {@link java.lang.Object} object.
     * @return a {@link graphql.schema.GraphQLObjectType} object.
     */
    GraphQLObjectType getType(Object object);

}
