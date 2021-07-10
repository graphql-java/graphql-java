package graphql;


import graphql.schema.DataFetcher;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class ScalarsQuerySchema {

    public static final DataFetcher inputDF = environment -> environment.getArgument("input");

    public static final GraphQLObjectType queryType = newObject()
            .name("QueryType")
            /** Static Scalars */
            .field(newFieldDefinition()
                    .name("floatNaN")
                    .type(Scalars.GraphQLFloat)
                    .staticValue(Double.NaN))


            /** Scalars with input of same type, value echoed back */
            .field(newFieldDefinition()
                    .name("floatNaNInput")
                    .type(Scalars.GraphQLFloat)
                    .argument(newArgument()
                            .name("input")
                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLFloat)))
                    .dataFetcher(inputDF))
            .field(newFieldDefinition()
                    .name("stringInput")
                    .type(Scalars.GraphQLString)
                    .argument(newArgument()
                            .name("input")
                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLString)))
                    .dataFetcher(inputDF))


            /** Scalars with input of String, cast to scalar */
            .field(newFieldDefinition()
                    .name("floatString")
                    .type(Scalars.GraphQLFloat)
                    .argument(newArgument()
                            .name("input")
                            .type(Scalars.GraphQLString))
                    .dataFetcher(inputDF))
            .field(newFieldDefinition()
                    .name("intString")
                    .type(Scalars.GraphQLInt)
                    .argument(newArgument()
                            .name("input")
                            .type(Scalars.GraphQLString))
                    .dataFetcher(inputDF))
            .build();


    public static final GraphQLSchema scalarsQuerySchema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();
}
