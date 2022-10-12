package graphql;


import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class ScalarsQuerySchema {

    public static final DataFetcher<?> inputDF = environment -> environment.getArgument("input");

    public static final GraphQLObjectType queryType = newObject()
            .name("QueryType")
            // Static scalars
            .field(newFieldDefinition()
                    .name("floatNaN")
                    .type(Scalars.GraphQLFloat)
                    .staticValue(Double.NaN)) // Retain for test coverage
            // Scalars with input of same type, value echoed back
            .field(newFieldDefinition()
                    .name("floatNaNInput")
                    .type(Scalars.GraphQLFloat)
                    .argument(newArgument()
                            .name("input")
                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLFloat))))
            .field(newFieldDefinition()
                    .name("stringInput")
                    .type(Scalars.GraphQLString)
                    .argument(newArgument()
                            .name("input")
                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLString))))
            // Scalars with input of String, cast to scalar
            .field(newFieldDefinition()
                    .name("floatString")
                    .type(Scalars.GraphQLFloat)
                    .argument(newArgument()
                            .name("input")
                            .type(Scalars.GraphQLString)))
            .field(newFieldDefinition()
                    .name("intString")
                    .type(Scalars.GraphQLInt)
                    .argument(newArgument()
                            .name("input")
                            .type(Scalars.GraphQLString)))
            .build();

    static FieldCoordinates floatNaNCoordinates = FieldCoordinates.coordinates("QueryType", "floatNaNInput");
    static FieldCoordinates stringInputCoordinates = FieldCoordinates.coordinates("QueryType", "stringInput");
    static FieldCoordinates floatStringCoordinates = FieldCoordinates.coordinates("QueryType", "floatString");
    static FieldCoordinates intStringCoordinates = FieldCoordinates.coordinates("QueryType", "intString");
    static GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
            .dataFetcher(floatNaNCoordinates, inputDF)
            .dataFetcher(stringInputCoordinates, inputDF)
            .dataFetcher(floatStringCoordinates, inputDF)
            .dataFetcher(intStringCoordinates, inputDF)
            .build();

    public static final GraphQLSchema scalarsQuerySchema = GraphQLSchema.newSchema()
            .codeRegistry(codeRegistry)
            .query(queryType)
            .build();
}
