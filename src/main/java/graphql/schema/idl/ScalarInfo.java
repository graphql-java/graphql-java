package graphql.schema.idl;

import graphql.Scalars;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Info on all the standard scalar objects provided by graphql-java
 */
public class ScalarInfo {
    /**
     * A list of the scalar types provided by graphql
     */
    public static final List<GraphQLScalarType> STANDARD_SCALARS = new ArrayList<>();

    /**
     * A map of scalar type definitions provided by graphql
     */
    public static final Map<String, ScalarTypeDefinition> STANDARD_SCALAR_DEFINITIONS = new LinkedHashMap<>();

    static {
        STANDARD_SCALARS.add(Scalars.GraphQLInt);
        STANDARD_SCALARS.add(Scalars.GraphQLFloat);
        STANDARD_SCALARS.add(Scalars.GraphQLString);
        STANDARD_SCALARS.add(Scalars.GraphQLBoolean);
        STANDARD_SCALARS.add(Scalars.GraphQLID);

        STANDARD_SCALARS.add(Scalars.GraphQLBigDecimal);
        STANDARD_SCALARS.add(Scalars.GraphQLBigInteger);
        STANDARD_SCALARS.add(Scalars.GraphQLByte);
        STANDARD_SCALARS.add(Scalars.GraphQLChar);
        STANDARD_SCALARS.add(Scalars.GraphQLShort);
        STANDARD_SCALARS.add(Scalars.GraphQLLong);
    }

    static {
        // graphql standard scalars
        STANDARD_SCALAR_DEFINITIONS.put("Int", new ScalarTypeDefinition("Int"));
        STANDARD_SCALAR_DEFINITIONS.put("Float", new ScalarTypeDefinition("Float"));
        STANDARD_SCALAR_DEFINITIONS.put("String", new ScalarTypeDefinition("String"));
        STANDARD_SCALAR_DEFINITIONS.put("Boolean", new ScalarTypeDefinition("Boolean"));
        STANDARD_SCALAR_DEFINITIONS.put("ID", new ScalarTypeDefinition("ID"));

        // graphql-java library extensions
        STANDARD_SCALAR_DEFINITIONS.put("Long", new ScalarTypeDefinition("Long"));
        STANDARD_SCALAR_DEFINITIONS.put("BigInteger", new ScalarTypeDefinition("BigInteger"));
        STANDARD_SCALAR_DEFINITIONS.put("BigDecimal", new ScalarTypeDefinition("BigDecimal"));
        STANDARD_SCALAR_DEFINITIONS.put("Short", new ScalarTypeDefinition("Short"));
        STANDARD_SCALAR_DEFINITIONS.put("Char", new ScalarTypeDefinition("Char"));

    }


    /**
     * Returns true if the scalar type is a standard one provided by graphql
     *
     * @param scalarType the type in question
     *
     * @return true if the scalar type is a standard one provided by graphql
     */
    public static boolean isStandardScalar(GraphQLScalarType scalarType) {
        return STANDARD_SCALARS.stream().anyMatch(sc -> sc.getName().equals(scalarType.getName()));
    }

}
