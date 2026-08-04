package graphql.schema.universe;

import graphql.Directives;
import graphql.ExperimentalApi;
import graphql.introspection.Introspection;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.InputValueWithState;
import graphql.schema.TypeResolver;
import graphql.schema.idl.EchoingWiringFactory;
import graphql.schema.idl.ScalarInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static java.util.stream.Collectors.toList;

/**
 * Exports the type-system topology of an {@link SUSchema} as a {@link GraphQLSchema}.
 *
 * <p>The exported schema is suitable for inspection and structural comparison. It is not intended
 * for execution because a schema universe does not retain data fetchers, type resolvers, custom
 * scalar coercing implementations, or other runtime wiring.</p>
 */
@ExperimentalApi
@NullMarked
public final class SUExporter {

    private static final TypeResolver UNEXECUTABLE_TYPE_RESOLVER = environment -> null;

    private final SUSchema schema;
    private final Map<Integer, GraphQLNamedType> namedTypes = new LinkedHashMap<>();

    public SUExporter(SUSchema schema) {
        this.schema = assertNotNull(schema);
    }

    public GraphQLSchema exportSchema() {
        for (SUVertex type : schema.getTypes()) {
            if (!Introspection.isIntrospectionTypes(requiredName(type))) {
                namedTypes.put(type.getId(), exportNamedType(type));
            }
        }

        GraphQLSchema.Builder builder = GraphQLSchema.newSchema()
                .query(objectType(schema.getQueryType()))
                .description(schema.getRoot().getDescription())
                .codeRegistry(exportCodeRegistry());

        SUObjectType mutationType = schema.getMutationType();
        if (mutationType != null) {
            builder.mutation(objectType(mutationType));
        }
        SUObjectType subscriptionType = schema.getSubscriptionType();
        if (subscriptionType != null) {
            builder.subscription(objectType(subscriptionType));
        }
        for (SUNamedType type : schema.getTypes()) {
            if (isOperationType(type)
                    || Introspection.isIntrospectionTypes(requiredName(type))) {
                continue;
            }
            builder.additionalType(namedType(type));
        }
        for (SUDirective directive : schema.getDirectiveDefinitions()) {
            builder.additionalDirective(exportDirectiveDefinition(directive));
        }
        builder.withSchemaAppliedDirectives(exportAppliedDirectives(schema.getRoot()));
        return builder.build();
    }

    private boolean isOperationType(SUNamedType type) {
        return type == schema.getQueryType()
                || type == schema.getMutationType()
                || type == schema.getSubscriptionType();
    }

    private GraphQLCodeRegistry exportCodeRegistry() {
        GraphQLCodeRegistry.Builder builder = GraphQLCodeRegistry.newCodeRegistry();
        for (GraphQLNamedType type : namedTypes.values()) {
            if (type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType) {
                builder.typeResolver(type.getName(), UNEXECUTABLE_TYPE_RESOLVER);
            }
        }
        return builder.build();
    }

    private GraphQLNamedType exportNamedType(SUVertex type) {
        switch (type.getKind()) {
            case OBJECT:
                return exportObject((SUObjectType) type);
            case INTERFACE:
                return exportInterface((SUInterfaceType) type);
            case UNION:
                return exportUnion((SUUnionType) type);
            case ENUM:
                return exportEnum((SUEnumType) type);
            case SCALAR:
                return exportScalar((SUScalarType) type);
            case INPUT_OBJECT:
                return exportInputObject((SUInputObjectType) type);
            default:
                throw new IllegalStateException("Not a named type: " + type);
        }
    }

    private GraphQLObjectType exportObject(SUObjectType type) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
                .name(requiredName(type))
                .description(type.getDescription())
                .replaceAppliedDirectives(exportAppliedDirectives(type));
        for (SUField field : schema.getFields(type)) {
            builder.field(exportField(field));
        }
        for (SUInterfaceType interfaceType : schema.getInterfaces(type)) {
            builder.withInterface(typeReference(interfaceType));
        }
        return builder.build();
    }

    private GraphQLInterfaceType exportInterface(SUInterfaceType type) {
        GraphQLInterfaceType.Builder builder = GraphQLInterfaceType.newInterface()
                .name(requiredName(type))
                .description(type.getDescription())
                .replaceAppliedDirectives(exportAppliedDirectives(type));
        for (SUField field : schema.getFields(type)) {
            builder.field(exportField(field));
        }
        for (SUInterfaceType interfaceType : schema.getInterfaces(type)) {
            builder.withInterface(typeReference(interfaceType));
        }
        return builder.build();
    }

    private GraphQLUnionType exportUnion(SUUnionType type) {
        GraphQLUnionType.Builder builder = GraphQLUnionType.newUnionType()
                .name(requiredName(type))
                .description(type.getDescription())
                .replaceAppliedDirectives(exportAppliedDirectives(type));
        for (SUObjectType member : schema.getUnionMembers(type)) {
            builder.possibleType(typeReference(member));
        }
        return builder.build();
    }

    private GraphQLEnumType exportEnum(SUEnumType type) {
        GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum()
                .name(requiredName(type))
                .description(type.getDescription())
                .replaceAppliedDirectives(exportAppliedDirectives(type));
        for (SUEnumValue value : schema.getEnumValues(type)) {
            GraphQLEnumValueDefinition.Builder valueBuilder =
                    GraphQLEnumValueDefinition.newEnumValueDefinition()
                            .name(requiredName(value))
                            .description(value.getDescription())
                            .value(requiredName(value))
                            .replaceAppliedDirectives(exportAppliedDirectives(value));
            String deprecationReason = deprecationReason(value);
            if (deprecationReason != null) {
                valueBuilder.deprecationReason(deprecationReason);
            }
            builder.value(valueBuilder.build());
        }
        return builder.build();
    }

    private GraphQLScalarType exportScalar(SUScalarType type) {
        GraphQLScalarType source = specifiedScalar(requiredName(type));
        if (source != null) {
            return source;
        }
        return GraphQLScalarType.newScalar(EchoingWiringFactory.fakeScalar(requiredName(type)))
                .description(type.getDescription())
                .replaceAppliedDirectives(exportAppliedDirectives(type))
                .build();
    }

    private GraphQLInputObjectType exportInputObject(SUInputObjectType type) {
        GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject()
                .name(requiredName(type))
                .description(type.getDescription())
                .replaceAppliedDirectives(exportAppliedDirectives(type));
        for (SUInputField field : schema.getInputFields(type)) {
            builder.field(exportInputField(field));
        }
        return builder.build();
    }

    private GraphQLFieldDefinition exportField(SUField field) {
        GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition()
                .name(requiredName(field))
                .description(field.getDescription())
                .type(outputType(requiredType(field)))
                .replaceAppliedDirectives(exportAppliedDirectives(field));
        for (SUArgument argument : schema.getArguments(field)) {
            builder.argument(exportArgument(argument));
        }
        String deprecationReason = deprecationReason(field);
        if (deprecationReason != null) {
            builder.deprecate(deprecationReason);
        }
        return builder.build();
    }

    private GraphQLArgument exportArgument(SUArgument argument) {
        GraphQLArgument.Builder builder = GraphQLArgument.newArgument()
                .name(requiredName(argument))
                .description(argument.getDescription())
                .type(inputType(requiredType(argument)))
                .replaceAppliedDirectives(exportAppliedDirectives(argument));
        applyArgumentDefault(builder, argument.getArgumentDefaultValue());
        if (argument.getDefinition() != null) {
            builder.definition(argument.getDefinition());
        }
        String deprecationReason = deprecationReason(argument);
        if (deprecationReason != null) {
            builder.deprecate(deprecationReason);
        }
        return builder.build();
    }

    private GraphQLInputObjectField exportInputField(SUInputField field) {
        GraphQLInputObjectField.Builder builder = GraphQLInputObjectField.newInputObjectField()
                .name(requiredName(field))
                .description(field.getDescription())
                .type(inputType(requiredType(field)))
                .replaceAppliedDirectives(exportAppliedDirectives(field));
        applyInputFieldDefault(builder, field.getInputFieldDefaultValue());
        if (field.getDefinition() != null) {
            builder.definition(field.getDefinition());
        }
        String deprecationReason = deprecationReason(field);
        if (deprecationReason != null) {
            builder.deprecate(deprecationReason);
        }
        return builder.build();
    }

    private GraphQLDirective exportDirectiveDefinition(SUDirective directive) {
        GraphQLDirective.Builder builder = GraphQLDirective.newDirective()
                .name(requiredName(directive))
                .description(directive.getDescription())
                .repeatable(directive.isRepeatable())
                .replaceAppliedDirectives(exportAppliedDirectives(directive));
        directive.validLocations().forEach(builder::validLocation);
        for (SUArgument argument : schema.getArguments(directive)) {
            builder.argument(exportArgument(argument));
        }
        if (directive.getDefinition() != null) {
            builder.definition(directive.getDefinition());
        }
        String deprecationReason = deprecationReason(directive);
        if (deprecationReason != null) {
            builder.deprecate(deprecationReason);
        }
        return builder.build();
    }

    private List<GraphQLAppliedDirective> exportAppliedDirectives(SUVertex container) {
        return schema.getAppliedDirectives(container).stream()
                .map(this::exportAppliedDirective)
                .collect(toList());
    }

    private GraphQLAppliedDirective exportAppliedDirective(SUAppliedDirective directive) {
        GraphQLAppliedDirective.Builder builder = GraphQLAppliedDirective.newDirective()
                .name(requiredName(directive));
        if (directive.getDefinition() != null) {
            builder.definition(directive.getDefinition());
        }
        for (SUAppliedDirectiveArgument argument : schema.getArguments(directive)) {
            GraphQLAppliedDirectiveArgument.Builder argumentBuilder =
                    GraphQLAppliedDirectiveArgument.newArgument()
                            .name(argument.getName())
                            .type(inputType(requiredType(argument)))
                            .inputValueWithState(argument.getArgumentValue());
            if (argument.getDefinition() != null) {
                argumentBuilder.definition(argument.getDefinition());
            }
            builder.argument(argumentBuilder.build());
        }
        return builder.build();
    }

    private GraphQLType exportType(SUVertex type) {
        if (type instanceof SUListType) {
            return GraphQLList.list(exportType(requiredWrappedType((SUListType) type)));
        }
        if (type instanceof SUNonNullType) {
            return GraphQLNonNull.nonNull(exportType(requiredWrappedType((SUNonNullType) type)));
        }
        return typeReference(type);
    }

    private GraphQLOutputType outputType(SUVertex type) {
        return (GraphQLOutputType) exportType(type);
    }

    private GraphQLInputType inputType(SUVertex type) {
        return (GraphQLInputType) exportType(type);
    }

    private GraphQLTypeReference typeReference(SUVertex type) {
        return GraphQLTypeReference.typeRef(requiredName(type));
    }

    private SUVertex requiredType(SUField field) {
        return assertNotNull(schema.getType(field), "Field '%s' has no type", requiredName(field));
    }

    private SUVertex requiredType(SUArgument argument) {
        return assertNotNull(schema.getType(argument), "Argument '%s' has no type", requiredName(argument));
    }

    private SUVertex requiredType(SUInputField field) {
        return assertNotNull(schema.getType(field), "Input field '%s' has no type", requiredName(field));
    }

    private SUVertex requiredType(SUAppliedDirectiveArgument argument) {
        return assertNotNull(
                schema.getType(argument),
                "Applied directive argument '%s' has no type",
                argument.getName());
    }

    private SUVertex requiredWrappedType(SUListType type) {
        return assertNotNull(schema.getWrappedType(type), "List type '%s' has no wrapped type", type.getId());
    }

    private SUVertex requiredWrappedType(SUNonNullType type) {
        return assertNotNull(
                schema.getWrappedType(type),
                "Non-null type '%s' has no wrapped type",
                type.getId());
    }

    private GraphQLNamedType namedType(SUVertex type) {
        return assertNotNull(namedTypes.get(type.getId()), "Type '%s' was not exported", type);
    }

    private GraphQLObjectType objectType(SUObjectType type) {
        return (GraphQLObjectType) namedType(type);
    }

    private String requiredName(SUVertex vertex) {
        return assertNotNull(vertex.getName(), "Vertex '%s' has no name", vertex.getId());
    }

    private @Nullable GraphQLScalarType specifiedScalar(String name) {
        for (GraphQLScalarType scalar : ScalarInfo.GRAPHQL_SPECIFICATION_SCALARS) {
            if (name.equals(scalar.getName())) {
                return scalar;
            }
        }
        return null;
    }

    private @Nullable String deprecationReason(SUVertex vertex) {
        for (SUAppliedDirective directive :
                schema.getAppliedDirectives(vertex, Directives.DeprecatedDirective.getName())) {
            SUAppliedDirectiveArgument reason = schema.getArgument(directive, "reason");
            if (reason == null || reason.getArgumentValue().isNotSet()) {
                return Directives.NO_LONGER_SUPPORTED;
            }
            Object value = reason.getArgumentValue().getValue();
            if (value instanceof StringValue) {
                return ((StringValue) value).getValue();
            }
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private void applyArgumentDefault(
            GraphQLArgument.Builder builder,
            InputValueWithState defaultValue) {
        if (defaultValue.isLiteral()) {
            builder.defaultValueLiteral((Value<?>) assertNotNull(defaultValue.getValue()));
        } else if (defaultValue.isExternal()) {
            builder.defaultValueProgrammatic(defaultValue.getValue());
        } else if (defaultValue.isInternal()) {
            builder.defaultValue(defaultValue.getValue());
        } else {
            builder.clearDefaultValue();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyInputFieldDefault(
            GraphQLInputObjectField.Builder builder,
            InputValueWithState defaultValue) {
        if (defaultValue.isLiteral()) {
            builder.defaultValueLiteral((Value<?>) assertNotNull(defaultValue.getValue()));
        } else if (defaultValue.isExternal()) {
            builder.defaultValueProgrammatic(defaultValue.getValue());
        } else if (defaultValue.isInternal()) {
            builder.defaultValue(defaultValue.getValue());
        } else {
            builder.clearDefaultValue();
        }
    }
}
