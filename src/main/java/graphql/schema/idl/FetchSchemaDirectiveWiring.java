package graphql.schema.idl;

import graphql.Internal;
import graphql.execution.ValuesResolver;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.PropertyDataFetcher;

import java.util.List;
import java.util.Optional;

import static graphql.DirectivesUtil.directiveWithArg;
import static graphql.schema.FieldCoordinates.coordinates;

/**
 * This adds ' @fetch(from : "otherName") ' support so you can rename what property is read for a given field
 *
 * @deprecated This support introduces a non standard directive and has interfere with some implementations.  This is no longer
 * installed default and will be removed in a future version
 */
@Internal
@Deprecated
public class FetchSchemaDirectiveWiring implements SchemaDirectiveWiring {
    public static final String FETCH = "fetch";

    @Override
    public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
        GraphQLFieldDefinition field = environment.getElement();
        String fetchName = atFetchFromSupport(field.getName(), field.getDirectives());
        DataFetcher dataFetcher = new PropertyDataFetcher(fetchName);

        environment.getCodeRegistry().dataFetcher(coordinates(environment.getFieldsContainer(), field), dataFetcher);
        return field;
    }


    private String atFetchFromSupport(String fieldName, List<GraphQLDirective> directives) {
        // @fetch(from : "name")
        Optional<GraphQLArgument> from = directiveWithArg(directives, FETCH, "from");
        return from.map(arg -> (String) ValuesResolver.valueToInternalValue(arg.getArgumentValue(), arg.getType())).orElse(fieldName);
    }

}
