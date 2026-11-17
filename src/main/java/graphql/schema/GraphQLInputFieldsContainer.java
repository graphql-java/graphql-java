package graphql.schema;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Types that can contain input fields are marked with this interface
 *
 * @see graphql.schema.GraphQLInputType
 */
@PublicApi
@NullMarked
public interface GraphQLInputFieldsContainer extends GraphQLNamedType {

    @Nullable GraphQLInputObjectField getFieldDefinition(String name);

    List<GraphQLInputObjectField> getFieldDefinitions();
}