package graphql;


import graphql.schema.*;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import java.math.BigDecimal;
import java.math.BigInteger;

public class ScalarsQuerySchema {

    public static GraphQLObjectType queryType = newObject()
            .name("QueryType")
            .field(newFieldDefinition()
                    .name("bigInteger")
                    .type(Scalars.GraphQLBigInteger)
                    .staticValue(BigInteger.valueOf(9999))
                    .build())
            .field(newFieldDefinition()
                    .name("bigDecimal")
                    .type(Scalars.GraphQLBigDecimal)
                    .staticValue(BigDecimal.valueOf(1234.0))
                    .build())
            .field(newFieldDefinition()
                    .name("doubleNaN")
                    .type(Scalars.GraphQLFloat)
                    .staticValue(Double.NaN)
                    .build())
            .field(newFieldDefinition()
                    .name("doubleNaNInput")
                    .type(Scalars.GraphQLFloat)
                    .argument(newArgument()
                            .name("input")
                            .type(new GraphQLNonNull(Scalars.GraphQLFloat))
                            .build())
                    .dataFetcher(new DataFetcher() {
                        @Override
                        public Object get(DataFetchingEnvironment environment) {
                            return environment.getArgument("input");
                        }
                    })
                    .build())
            .field(newFieldDefinition()
                    .name("bigIntegerInput")
                    .type(Scalars.GraphQLBigInteger)
                    .argument(newArgument()
                            .name("input")
                            .type(new GraphQLNonNull(Scalars.GraphQLBigInteger))
                            .build())
                    .dataFetcher(new DataFetcher() {
                        @Override
                        public Object get(DataFetchingEnvironment environment) {
                            return environment.getArgument("input");
                        }
                    })
                    .build())
            .field(newFieldDefinition()
                    .name("bigDecimalInput")
                    .type(Scalars.GraphQLBigDecimal)
                    .argument(newArgument()
                            .name("input")
                            .type(new GraphQLNonNull(Scalars.GraphQLBigDecimal))
                            .build())
                    .dataFetcher(new DataFetcher() {
                        @Override
                        public Object get(DataFetchingEnvironment environment) {
                            return environment.getArgument("input");
                        }
                    })
                    .build())
            .build();


    public static GraphQLSchema scalarsQuerySchema = GraphQLSchema.newSchema()
            .query(queryType)
            .build();
}
