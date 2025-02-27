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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static graphql.Directives.IncludeDirective;
import static graphql.Directives.SkipDirective;

@Internal
public class ConditionalNodes {

    /**
     * return null if skip/include argument contains a variable and therefore could not be resolved
     */
    public Boolean shouldIncludeWithoutVariables(DirectivesContainer<?> element) {
        return shouldInclude(null, element.getDirectives());
    }

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


    private @Nullable Boolean shouldInclude(Map<String, Object> variables, List<Directive> directives) {
        // shortcut on no directives
        if (directives.isEmpty()) {
            return true;
        }
        Boolean skip = getDirectiveResult(variables, directives, SkipDirective.getName(), false);
        if (skip == null) {
            return null;
        }
        if (skip) {
            return false;
        }

        return getDirectiveResult(variables, directives, IncludeDirective.getName(), true);
    }

    public boolean containsSkipOrIncludeDirective(DirectivesContainer<?> directivesContainer) {
        return NodeUtil.findNodeByName(directivesContainer.getDirectives(), SkipDirective.getName()) != null ||
                NodeUtil.findNodeByName(directivesContainer.getDirectives(), IncludeDirective.getName()) != null;
    }


    public String getSkipVariableName(DirectivesContainer<?> directivesContainer) {
        Directive skipDirective = NodeUtil.findNodeByName(directivesContainer.getDirectives(), SkipDirective.getName());
        if (skipDirective == null) {
            return null;
        }
        Argument argument = skipDirective.getArgument("if");
        if (argument.getValue() instanceof VariableReference) {
            return ((VariableReference) argument.getValue()).getName();
        }
        return null;
    }

    public String getIncludeVariableName(DirectivesContainer<?> directivesContainer) {
        Directive skipDirective = NodeUtil.findNodeByName(directivesContainer.getDirectives(), IncludeDirective.getName());
        if (skipDirective == null) {
            return null;
        }
        Argument argument = skipDirective.getArgument("if");
        if (argument.getValue() instanceof VariableReference) {
            return ((VariableReference) argument.getValue()).getName();
        }
        return null;
    }


    private @Nullable Boolean getDirectiveResult(Map<String, Object> variables, List<Directive> directives, String directiveName, boolean defaultValue) {
        Directive foundDirective = NodeUtil.findNodeByName(directives, directiveName);
        if (foundDirective != null) {
            return getIfValue(foundDirective.getArguments(), variables);
        }
        return defaultValue;
    }

    private @Nullable Boolean getIfValue(List<Argument> arguments, @Nullable Map<String, Object> variables) {
        for (Argument argument : arguments) {
            if (argument.getName().equals("if")) {
                Object value = argument.getValue();
                if (value instanceof BooleanValue) {
                    return ((BooleanValue) value).isValue();
                }
                if (value instanceof VariableReference && variables != null) {
                    return (boolean) variables.get(((VariableReference) value).getName());
                }
                return null;
            }
        }
        return Assert.assertShouldNeverHappen("The 'if' argument must be present");
    }
}
