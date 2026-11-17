package graphql.schema;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;


/**
 * Types that can contain output fields are marked with this interface
 *
 * @see graphql.schema.GraphQLObjectType
 * @see graphql.schema.GraphQLInterfaceType
 */
@PublicApi
@NullMarked
public interface GraphQLFieldsContainer extends GraphQLCompositeType {

    @Nullable GraphQLFieldDefinition getFieldDefinition(String name);

    List<GraphQLFieldDefinition> getFieldDefinitions();

    default @Nullable GraphQLFieldDefinition getField(String name) {
        return getFieldDefinition(name);
    }

    default List<GraphQLFieldDefinition> getFields() {
        return getFieldDefinitions();
    }
}
