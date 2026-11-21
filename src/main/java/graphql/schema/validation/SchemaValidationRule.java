package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLUnionType;
import org.jspecify.annotations.NullMarked;

/**
 * A schema validation rule. {@link SchemaValidator} calls each hook once for every matching element of the schema;
 * rules override the hooks they need and report problems to the error collector they were created with.
 */
@Internal
@NullMarked
public interface SchemaValidationRule {

    default void checkObjectType(GraphQLObjectType objectType) {
    }

    default void checkInterfaceType(GraphQLInterfaceType interfaceType) {
    }

    default void checkUnionType(GraphQLUnionType unionType) {
    }

    default void checkEnumType(GraphQLEnumType enumType) {
    }

    default void checkInputObjectType(GraphQLInputObjectType inputObjectType) {
    }

    default void checkFieldDefinition(GraphQLFieldsContainer container, GraphQLFieldDefinition fieldDefinition) {
    }

    default void checkInputObjectField(GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField) {
    }

    /**
     * @param fieldOrDirective the field definition or directive definition declaring the argument
     * @param argument         the argument
     */
    default void checkArgument(GraphQLSchemaElement fieldOrDirective, GraphQLArgument argument) {
    }

    /**
     * Called for every type, field, argument, input field, enum value and directive definition.
     *
     * @param container the element that directives can be applied to
     */
    default void checkDirectiveContainer(GraphQLDirectiveContainer container) {
    }
}
