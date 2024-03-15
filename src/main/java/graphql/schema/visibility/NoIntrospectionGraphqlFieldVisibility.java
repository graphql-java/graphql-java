package graphql.schema.visibility;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;

import java.util.List;

import static graphql.schema.visibility.BlockedFields.newBlock;

/**
 * This field visibility will prevent Introspection queries from being performed.  Technically this puts your
 * system in contravention of <a href="https://spec.graphql.org/October2021/#sec-Introspection">the specification</a>
 * but some production systems want this lock down in place.
 *
 * @deprecated This is no longer the best way to prevent Introspection - {@link graphql.introspection.Introspection#enabledJvmWide(boolean)}
 * can be used instead
 */
@PublicApi
@Deprecated(since = "2024-03-16")
public class NoIntrospectionGraphqlFieldVisibility implements GraphqlFieldVisibility {

    @Deprecated(since = "2024-03-16")
    public static NoIntrospectionGraphqlFieldVisibility NO_INTROSPECTION_FIELD_VISIBILITY = new NoIntrospectionGraphqlFieldVisibility();


    private final BlockedFields blockedFields;

    public NoIntrospectionGraphqlFieldVisibility() {
        blockedFields = newBlock().addPattern("__.*").build();
    }

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
        return blockedFields.getFieldDefinitions(fieldsContainer);
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
        return blockedFields.getFieldDefinition(fieldsContainer, fieldName);
    }

}
