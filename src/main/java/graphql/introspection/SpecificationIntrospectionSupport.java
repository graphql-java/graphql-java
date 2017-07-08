package graphql.introspection;

import graphql.schema.GraphQLFieldDefinition;

/**
 * Defined in terms of http://facebook.github.io/graphql/#sec-Introspection
 */
public class SpecificationIntrospectionSupport implements IntrospectionSupport {

    public static IntrospectionSupport INSTANCE = new SpecificationIntrospectionSupport();

    @Override
    public GraphQLFieldDefinition __Schema() {
        return Introspection.SchemaMetaFieldDef;
    }

    @Override
    public GraphQLFieldDefinition __Type() {
        return Introspection.TypeMetaFieldDef;
    }

    @Override
    public GraphQLFieldDefinition __TypeName() {
        return Introspection.TypeNameMetaFieldDef;
    }
}
