package graphql.schema.idl;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLUnionType;

/**
 * A SchemaDirectiveWiring is responsible for enhancing a runtime element based on directives placed on that
 * element in the Schema Definition Language (SDL).
 *
 * It can enhance the graphql runtime element and add new behaviour for example by changing
 * a fields {@link graphql.schema.DataFetcher}
 */
public interface SchemaDirectiveWiring {

    default GraphQLObjectType onObject(SchemaDirectiveWiringEnvironment<GraphQLObjectType> environment) {
        return environment.getTypeElement();
    }

    default GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        return environment.getTypeElement();
    }

    default GraphQLArgument onArgument(SchemaDirectiveWiringEnvironment<GraphQLArgument> environment) {
        return environment.getTypeElement();
    }

    default GraphQLInterfaceType onInterface(SchemaDirectiveWiringEnvironment<GraphQLInterfaceType> environment) {
        return environment.getTypeElement();
    }

    default GraphQLUnionType onUnion(SchemaDirectiveWiringEnvironment<GraphQLUnionType> environment) {
        return environment.getTypeElement();
    }

    default GraphQLEnumType onEnum(SchemaDirectiveWiringEnvironment<GraphQLEnumType> environment) {
        return environment.getTypeElement();
    }

    default GraphQLEnumValueDefinition onEnumValue(SchemaDirectiveWiringEnvironment<GraphQLEnumValueDefinition> environment) {
        return environment.getTypeElement();
    }

    default GraphQLScalarType onScalar(SchemaDirectiveWiringEnvironment<GraphQLScalarType> environment) {
        return environment.getTypeElement();
    }

    default GraphQLInputObjectType onInputObjectType(SchemaDirectiveWiringEnvironment<GraphQLInputObjectType> environment) {
        return environment.getTypeElement();
    }

    default GraphQLInputObjectField onInputObjectField(SchemaDirectiveWiringEnvironment<GraphQLInputObjectField> environment) {
        return environment.getTypeElement();
    }
}
