package graphql.execution;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class DirectivesResolver {

    private final ValuesResolver valuesResolver;

    public DirectivesResolver(ValuesResolver valuesResolver) {
        this.valuesResolver = valuesResolver;
    }

    public Map<String, GraphQLDirective> resolveDirectives(List<Directive> directives, GraphQLSchema schema, Map<String, Object> variables) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, GraphQLDirective> directiveMap = new LinkedHashMap<>();
        directives.forEach(directive -> {
            GraphQLDirective protoType = schema.getDirective(directive.getName());
            if (protoType != null) {
                EnumSet<Introspection.DirectiveLocation> validLocations = protoType.validLocations();
                //
                // we only allow values for directives that exist and are on the right location.  Validation before hand
                // will have caught most of these but we are being defensive here
                //
                if (validLocations.contains(Introspection.DirectiveLocation.FIELD)) {
                    GraphQLDirective newDirective = protoType.transform(builder -> buildArguments(builder, fieldVisibility, protoType, directive, variables));
                    directiveMap.put(newDirective.getName(), newDirective);
                }
            }
        });
        return directiveMap;
    }

    private void buildArguments(GraphQLDirective.Builder directiveBuilder, GraphqlFieldVisibility fieldVisibility, GraphQLDirective protoType, Directive fieldDirective, Map<String, Object> variables) {
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