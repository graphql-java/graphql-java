package graphql.execution;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Internal
public class DirectivesResolver {

    private final static ValuesResolver valuesResolver = new ValuesResolver();

    public static Map<String, GraphQLDirective> getFieldDirectives(Field field, GraphQLSchema schema, Map<String, Object> variables) {
        GraphqlFieldVisibility fieldVisibility = schema.getFieldVisibility();

        Map<String, GraphQLDirective> directiveMap = new HashMap<>();

        field.getDirectives().forEach(fieldDirective -> {
            GraphQLDirective protoType = schema.getDirective(fieldDirective.getName());
            if (protoType != null) {
                EnumSet<Introspection.DirectiveLocation> validLocations = protoType.validLocations();
                //
                // we only allow values for directives that exist and are on the right location.  Validation before hand
                // will have caught most of these but we are being defensive here
                //
                if (validLocations.contains(Introspection.DirectiveLocation.FIELD)) {
                    GraphQLDirective newDirective = protoType.transform(builder -> buildArguments(builder, fieldVisibility, protoType, fieldDirective, variables));
                    directiveMap.put(newDirective.getName(), newDirective);
                }
            }
        });
        return directiveMap;
    }

    private static void buildArguments(GraphQLDirective.Builder directiveBuilder, GraphqlFieldVisibility fieldVisibility, GraphQLDirective protoType, Directive fieldDirective, Map<String, Object> variables) {

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldVisibility, protoType.getArguments(), fieldDirective.getArguments(), variables);

        directiveBuilder.clearArguments();
        protoType.getArguments().forEach(protoArg -> {
            if (argumentValues.containsKey(protoArg.getName())) {
                Object argValue = argumentValues.get(protoArg.getName());
                GraphQLArgument newArgument = protoArg.transform(argBuilder -> argBuilder.value(argValue));
                directiveBuilder.argument(newArgument);
            }
        });
    }
}
