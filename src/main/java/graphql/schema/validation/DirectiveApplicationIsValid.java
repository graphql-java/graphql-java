package graphql.schema.validation;

import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static graphql.schema.validation.SchemaValidationErrorType.DirectiveInIllegalLocation;
import static graphql.schema.validation.SchemaValidationErrorType.DirectiveMissingRequiredArgument;
import static graphql.schema.validation.SchemaValidationErrorType.DirectiveUnknownArgument;
import static java.lang.String.format;

/**
 * Validates that applied directives are used in the correct location, that their arguments
 * are known, and that required arguments are provided.
 */
@Internal
public class DirectiveApplicationIsValid extends GraphQLTypeVisitorStub {

    @Override
    protected TraversalControl visitGraphQLType(GraphQLSchemaElement node, TraverserContext<GraphQLSchemaElement> context) {
        if (!(node instanceof GraphQLDirectiveContainer)) {
            return TraversalControl.CONTINUE;
        }
        DirectiveLocation location = getLocation(node);
        if (location == null) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        GraphQLDirectiveContainer container = (GraphQLDirectiveContainer) node;

        for (Map.Entry<String, List<GraphQLDirective>> entry : container.getAllDirectivesByName().entrySet()) {
            String directiveName = entry.getKey();
            GraphQLDirective directiveDef = schema.getDirective(directiveName);
            if (directiveDef == null) {
                // already handled by AppliedDirectivesAreValid
                continue;
            }
            for (GraphQLDirective appliedDirective : entry.getValue()) {
                checkLocation(errorCollector, container, directiveDef, location);
                checkArguments(errorCollector, container, appliedDirective, directiveDef);
            }
        }
        return TraversalControl.CONTINUE;
    }

    private void checkLocation(SchemaValidationErrorCollector errorCollector, GraphQLDirectiveContainer container,
                               GraphQLDirective directiveDef, DirectiveLocation expectedLocation) {
        EnumSet<DirectiveLocation> validLocations = directiveDef.validLocations();
        if (!validLocations.contains(expectedLocation)) {
            errorCollector.addError(new SchemaValidationError(DirectiveInIllegalLocation,
                    format("Directive '%s' is not allowed on '%s' called '%s'. Allowed locations: %s",
                            directiveDef.getName(), expectedLocation.name(), container.getName(), validLocations)));
        }
    }

    private void checkArguments(SchemaValidationErrorCollector errorCollector, GraphQLDirectiveContainer container,
                                GraphQLDirective appliedDirective, GraphQLDirective directiveDef) {
        // Check for unknown arguments
        for (GraphQLArgument appliedArg : appliedDirective.getArguments()) {
            if (directiveDef.getArgument(appliedArg.getName()) == null) {
                errorCollector.addError(new SchemaValidationError(DirectiveUnknownArgument,
                        format("Directive '%s' on '%s' has an unknown argument '%s'",
                                directiveDef.getName(), container.getName(), appliedArg.getName())));
            }
        }

        // Check for missing required arguments
        for (GraphQLArgument defArg : directiveDef.getArguments()) {
            if (!defArg.hasSetDefaultValue() && GraphQLTypeUtil.isNonNull(defArg.getType())) {
                boolean provided = appliedDirective.getArgument(defArg.getName()) != null;
                if (!provided) {
                    errorCollector.addError(new SchemaValidationError(DirectiveMissingRequiredArgument,
                            format("Directive '%s' on '%s' is missing required argument '%s'",
                                    directiveDef.getName(), container.getName(), defArg.getName())));
                }
            }
        }
    }

    static DirectiveLocation getLocation(GraphQLSchemaElement node) {
        if (node instanceof GraphQLObjectType) {
            return DirectiveLocation.OBJECT;
        }
        if (node instanceof GraphQLInterfaceType) {
            return DirectiveLocation.INTERFACE;
        }
        if (node instanceof GraphQLUnionType) {
            return DirectiveLocation.UNION;
        }
        if (node instanceof GraphQLEnumType) {
            return DirectiveLocation.ENUM;
        }
        if (node instanceof GraphQLEnumValueDefinition) {
            return DirectiveLocation.ENUM_VALUE;
        }
        if (node instanceof GraphQLScalarType) {
            return DirectiveLocation.SCALAR;
        }
        if (node instanceof GraphQLInputObjectType) {
            return DirectiveLocation.INPUT_OBJECT;
        }
        if (node instanceof GraphQLFieldDefinition) {
            return DirectiveLocation.FIELD_DEFINITION;
        }
        if (node instanceof GraphQLArgument) {
            return DirectiveLocation.ARGUMENT_DEFINITION;
        }
        if (node instanceof GraphQLInputObjectField) {
            return DirectiveLocation.INPUT_FIELD_DEFINITION;
        }
        return null;
    }

    /**
     * Checks schema-level applied directives. Called directly from SchemaValidator
     * since the traverser does not visit the schema itself as a directive container.
     */
    static void checkSchemaDirectives(GraphQLSchema schema, SchemaValidationErrorCollector errorCollector) {
        for (Map.Entry<String, List<GraphQLDirective>> entry : schema.getAllSchemaDirectivesByName().entrySet()) {
            String directiveName = entry.getKey();
            GraphQLDirective directiveDef = schema.getDirective(directiveName);
            if (directiveDef == null) {
                continue;
            }
            EnumSet<DirectiveLocation> validLocations = directiveDef.validLocations();
            if (!validLocations.contains(DirectiveLocation.SCHEMA)) {
                errorCollector.addError(new SchemaValidationError(DirectiveInIllegalLocation,
                        format("Directive '%s' is not allowed on SCHEMA. Allowed locations: %s",
                                directiveName, validLocations)));
            }
        }
    }
}
