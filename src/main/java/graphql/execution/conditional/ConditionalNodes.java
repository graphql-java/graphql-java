package graphql.execution.conditional;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.Directive;
import graphql.language.DirectivesContainer;
import graphql.language.NodeUtil;
import graphql.language.VariableReference;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;

@Internal
public class ConditionalNodes {

    public boolean shouldInclude(DirectivesContainer<?> element,
                                 Map<String, Object> variables,
                                 GraphQLSchema graphQLSchema,
                                 @Nullable GraphQLContext graphQLContext
    ) {
        //
        // call the base @include / @skip first
        if (!shouldInclude(variables, element.getDirectives())) {
            return false;
        }
        //
        // if they have declared a decision callback, then we will use it but we expect this to be mostly
        // empty and hence the cost is a map lookup.
        if (graphQLContext != null) {
            ConditionalNodeDecision conditionalDecision = graphQLContext.get(ConditionalNodeDecision.class);
            if (conditionalDecision != null) {
                return customShouldInclude(variables, element, graphQLSchema, graphQLContext, conditionalDecision);
            }
        }
        // if no one says otherwise, the node is considered included
        return true;
    }

    private boolean customShouldInclude(Map<String, Object> variables,
                                        DirectivesContainer<?> element,
                                        GraphQLSchema graphQLSchema,
                                        GraphQLContext graphQLContext,
                                        ConditionalNodeDecision conditionalDecision
    ) {
        CoercedVariables coercedVariables = CoercedVariables.of(variables);
        return conditionalDecision.shouldInclude(new ConditionalNodeDecisionEnvironment() {
            @Override
            public DirectivesContainer<?> getDirectivesContainer() {
                return element;
            }

            @Override
            public CoercedVariables getVariables() {
                return coercedVariables;
            }

            @Override
            public GraphQLSchema getGraphQlSchema() {
                return graphQLSchema;
            }

            @Override
            public GraphQLContext getGraphQLContext() {
                return graphQLContext;
            }
        });
    }


    private boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {
        if (directives.isEmpty()) {
            return true;
        }
        return !getDirectiveResult(variables, directives, SkipDirective.getName(), false)
                && getDirectiveResult(variables, directives, IncludeDirective.getName(), true);
    }

    private boolean getDirectiveResult(Map<String, Object> variables, List<Directive> directives, String directiveName, boolean defaultValue) {
        Directive foundDirective = NodeUtil.findNodeByName(directives, directiveName);
        if (foundDirective != null) {
            return getIfValue(foundDirective.getArguments(), variables);
        }
        return defaultValue;
    }

    private boolean getIfValue(List<Argument> arguments, Map<String, Object> variables) {
        for (Argument argument : arguments) {
            if (argument.getName().equals("if")) {
                Object value = argument.getValue();
                if (value instanceof BooleanValue) {
                    return ((BooleanValue) value).isValue();
                }
                if (value instanceof VariableReference) {
                    return (boolean) variables.get(((VariableReference) value).getName());
                }
                return Assert.assertShouldNeverHappen("The 'if' argument must be a Boolean or variable reference");
            }
        }
        return Assert.assertShouldNeverHappen("The 'if' argument must be present");
    }
}
