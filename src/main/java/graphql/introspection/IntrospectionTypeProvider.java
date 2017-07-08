package graphql.introspection;

import graphql.PublicSpi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;

@PublicSpi
public interface IntrospectionTypeProvider {
    GraphQLObjectType getIntrospectionSchema();

    GraphQLFieldDefinition getSchemaMetaFieldDef();

    GraphQLFieldDefinition getTypeMetaFieldDef();

    GraphQLFieldDefinition getTypeNameMetaFieldDef();

    IntrospectionTypeProvider DEFAULT = new IntrospectionTypeProvider() {
        @Override
        public GraphQLObjectType getIntrospectionSchema() {
            return Introspection.__Schema;
        }

        @Override
        public GraphQLFieldDefinition getSchemaMetaFieldDef() {
            return Introspection.SchemaMetaFieldDef;
        }

        @Override
        public GraphQLFieldDefinition getTypeMetaFieldDef() {
            return Introspection.TypeMetaFieldDef;
        }

        @Override
        public GraphQLFieldDefinition getTypeNameMetaFieldDef() {
            return Introspection.TypeNameMetaFieldDef;
        }
    };
}
