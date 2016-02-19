package graphql.schema;


/**
 * <p>GraphQLModifiedType interface.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public interface GraphQLModifiedType extends GraphQLType {

    /**
     * <p>getWrappedType.</p>
     *
     * @return a {@link graphql.schema.GraphQLType} object.
     */
    GraphQLType getWrappedType();
}
