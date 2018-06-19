package graphql.analysis;

import graphql.PublicApi;
import graphql.language.Field;
import graphql.language.SelectionSetContainer;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;

import java.util.Map;

@PublicApi
public interface QueryVisitorFieldEnvironment {

    Field getField();

    GraphQLFieldDefinition getFieldDefinition();

    GraphQLCompositeType getParentType();

    QueryVisitorFieldEnvironment getParentEnvironment();

    Map<String, Object> getArguments();

    SelectionSetContainer getSelectionSetContainer();
}
