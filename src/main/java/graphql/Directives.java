package graphql;


import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNonNull;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.schema.GraphQLArgument.newArgument;

/**
 * <p>Directives class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Directives {

    /** Constant <code>IncludeDirective</code> */
    public static GraphQLDirective IncludeDirective = GraphQLDirective.newDirective()
            .name("include")
            .description("Directs the executor to include this field or fragment only when the `if` argument is true")
            .argument(newArgument()
                    .name("if")
                    .type(new GraphQLNonNull(GraphQLBoolean))
                    .description("Included when true.")
                    .build())
            .onOperation(false)
            .onFragment(true)
            .onField(true)
            .build();

    /** Constant <code>SkipDirective</code> */
    public static GraphQLDirective SkipDirective = GraphQLDirective.newDirective()
            .name("skip")
            .description("Directs the executor to skip this field or fragment when the `if`'argument is true.")
            .argument(newArgument()
                    .name("skip")
                    .type(new GraphQLNonNull(GraphQLBoolean))
                    .description("Skipped when true.")
                    .build())
            .onOperation(false)
            .onFragment(true)
            .onField(true)
            .build();


}
