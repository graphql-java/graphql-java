package graphql.schema.idl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.PublicApi;
import graphql.Scalars;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;

import java.util.List;
import java.util.Map;

/**
 * Info on all the standard scalar objects provided by graphql-java
 */
@PublicApi
public class ScalarInfo {

    /**
     * A list of the built-in scalar types as defined by the graphql specification
     */
    public static final List<GraphQLScalarType> GRAPHQL_SPECIFICATION_SCALARS = ImmutableList.of(
            Scalars.GraphQLInt,
            Scalars.GraphQLFloat,
            Scalars.GraphQLString,
            Scalars.GraphQLBoolean,
            Scalars.GraphQLID);

    /**
     * A map of scalar type definitions provided by graphql-java
     */
    public static final Map<String, ScalarTypeDefinition> GRAPHQL_SPECIFICATION_SCALARS_DEFINITIONS = ImmutableMap.of(
            "Int", ScalarTypeDefinition.newScalarTypeDefinition().name("Int").build(),
            "Float", ScalarTypeDefinition.newScalarTypeDefinition().name("Float").build(),
            "String", ScalarTypeDefinition.newScalarTypeDefinition().name("String").build(),
            "Boolean", ScalarTypeDefinition.newScalarTypeDefinition().name("Boolean").build(),
            "ID", ScalarTypeDefinition.newScalarTypeDefinition().name("ID").build());

    /**
     * Returns true if the scalar type is a scalar that is specified by the graphql specification
     *
     * @param scalarTypeName the name of the scalar type in question
     *
     * @return true if the scalar type is specified by the graphql specification
     */
    public static boolean isGraphqlSpecifiedScalar(String scalarTypeName) {
        return inList(GRAPHQL_SPECIFICATION_SCALARS, scalarTypeName);
    }

    /**
     * Returns true if the scalar type is a scalar that is specified by the graphql specification
     *
     * @param scalarType the type in question
     *
     * @return true if the scalar type is specified by the graphql specification
     */
    public static boolean isGraphqlSpecifiedScalar(GraphQLScalarType scalarType) {
        return inList(GRAPHQL_SPECIFICATION_SCALARS, scalarType.getName());
    }

    private static boolean inList(List<GraphQLScalarType> scalarList, String scalarTypeName) {
        return scalarList.stream().anyMatch(sc -> sc.getName().equals(scalarTypeName));
    }

}
