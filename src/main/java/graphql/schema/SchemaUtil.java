package graphql.schema;


import graphql.GraphQL;

import java.util.Map;

public class SchemaUtil {


    private static void typeCollector(GraphQLType root, Map<String, GraphQLType> result) {
        if (root instanceof GraphQLNonNull) {
            typeCollector(((GraphQLNonNull) root).getWrappedType(), result);
        } else if (root instanceof GraphQLList) {
            typeCollector(((GraphQLList) root).getWrappedType(), result);
        } else if (root instanceof GraphQLEnumType) {
            result.put(((GraphQLEnumType) root).getName(), root);
        } else if (root instanceof GraphQLScalarType) {
            result.put(((GraphQLScalarType) root).getName(), root);
        } else if (root instanceof GraphQLObjectType) {

        } else if (root instanceof GraphQLInterfaceType) {

        } else if (root instanceof GraphQLUnionType) {

        }



    }

    //TODO: Move into a better place where it doesn't need to 'cache' it
    public static GraphQLType findType(GraphQLSchema schema, String name) {
        GraphQLObjectType queryType = schema.getQueryType();
        return null;
    }
}
