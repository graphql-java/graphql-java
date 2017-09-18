package graphql.schema;


import java.util.Map;

/**
 * Certain graphql runtime types can have arbitrary metadata associated with them
 */
public interface GraphQLMetadataSupport extends GraphQLType {

    /**
     * @return a map of arbitrary metadata on a type
     */
    Map<String, Object> getMetadata();
}
