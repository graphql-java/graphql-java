package graphql;

/**
 * The {@link graphql.Scalars#GraphQLID} scalar can turn values that implement this interface into
 * IDs.  The specification says this about the ID scalar:
 *
 * <pre>
 *     The ID scalar type represents a unique identifier, often used to re-fetch an object or as the key for a cache.
 *
 *     The ID type is serialized in the same way as a String; however, it is not
 *     intended to be human‐readable.
 * </pre>
 */
public interface GraphQLIDValue {

    /**
     * @return an opaque string that represents this ID value.
     */
    String getValue();
}
