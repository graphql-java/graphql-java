package graphql.schema.visibility;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;

import java.util.List;

import static graphql.schema.visibility.BlacklistGraphqlFieldVisibility.newBlacklist;

/**
 * This field visibility will prevent Introspection queries from being performed.  Technically this puts your
 * system in contravention of the specification - http://facebook.github.io/graphql/#sec-Introspection but some
 * production systems want this lock down in place.
 */
public class NoIntrospectionGraphqlFieldVisibility implements GraphqlFieldVisibility {

    public static NoIntrospectionGraphqlFieldVisibility NO_INTROSPECTION_FIELD_VISIBILITY = new NoIntrospectionGraphqlFieldVisibility();


    private final BlacklistGraphqlFieldVisibility blackListUnderscoreFields;

    public NoIntrospectionGraphqlFieldVisibility() {
        blackListUnderscoreFields = newBlacklist().addPattern("__.*").build();
    }

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
        return blackListUnderscoreFields.getFieldDefinitions(fieldsContainer);
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
        return blackListUnderscoreFields.getFieldDefinition(fieldsContainer, fieldName);
    }

}
