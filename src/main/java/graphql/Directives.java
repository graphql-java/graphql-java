package graphql;


import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNonNull;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLFieldArgument.newFieldArgument;

public class Directives {

    public static GraphQLDirective includeDirective = GraphQLDirective.newDirective()
            .name("include")
            .description("Directs the executor to include this field or fragment only when the `if` argument is true")
            .argument(
                    newFieldArgument()
                            .name("if")
                            .type(new GraphQLNonNull(GraphQLBoolean))
                            .description("Included when true.")
                            .build()
            )
            .onOperation(false)
            .onFragment(true)
            .onField(true)
            .build();

    public static GraphQLDirective skipDirective = GraphQLDirective.newDirective()
            .name("skip")
            .description(" 'Directs the executor to skip this field or fragment when the `if`'argument is true.")
            .argument(
                    newFieldArgument()
                            .name("skip")
                            .type(new GraphQLNonNull(GraphQLBoolean))
                            .description("Skipped when true")
                            .build()
            )
            .onOperation(false)
            .onFragment(true)
            .onField(true)
            .build();


}
