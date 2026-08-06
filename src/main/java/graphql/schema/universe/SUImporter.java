package graphql.schema.universe;

import graphql.Directives;
import graphql.DirectivesUtil;
import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.language.Node;
import graphql.language.StringValue;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaTraverser;
import graphql.schema.impl.GraphQLTypeCollectingVisitor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;

/**
 * Imports the type-system topology of an existing {@link GraphQLSchema} into a universe.
 *
 * <p>The importer preserves schema-element object identity during one import. Runtime wiring in
 * {@code GraphQLCodeRegistry}, coercing implementations, and programmatic behavior are not part of
 * this topology import. Source AST definitions are retained by default and can be disabled through
 * {@link SUSchemaOptions}.</p>
 */
@ExperimentalApi
public final class SUImporter {

    private final SchemaUniverse universe;
    private final Map<String, SUNamedType> namedTypes = new LinkedHashMap<>();
    private final Map<GraphQLSchemaElement, SUVertex> elementVertices = new IdentityHashMap<>();
    private final Map<GraphQLAppliedDirectiveArgument, SUAppliedDirectiveArgument>
            appliedArgumentValues = new IdentityHashMap<>();
    private final Map<String, GraphQLDirective> directiveDefinitions = new LinkedHashMap<>();
    private @Nullable SUSchemaBuilder schemaBuilder;
    private boolean captureAstDefinitions = true;

    public SUImporter(SchemaUniverse universe) {
        this.universe = assertNotNull(universe);
    }

    public SUSchema importSchema(String name, GraphQLSchema schema) {
        return importSchema(
                name,
                schema,
                SUSchemaOptions.defaultOptions());
    }

    public SUSchema importSchema(
            String name,
            GraphQLSchema schema,
            SUSchemaOptions options) {
        captureAstDefinitions =
                assertNotNull(options).isCaptureAstDefinitions();
        schemaBuilder = universe.newSchema(name, schema.getDescription());
        captureAstDefinitions(
                builder().getRoot(),
                schema.getDefinition(),
                schema.getExtensionDefinitions());
        createNamedTypeVertices(schema.getAllTypesAsList());
        addRootTypes(schema);
        builder().introspectionSchemaType(
                (SUObjectType) namedType(schema.getIntrospectionSchemaType()));
        addDirectiveDefinitions(schema.getDirectives());
        expandNamedTypes(schema.getAllTypesAsList());
        addAppliedDirectives(
                DirectivesUtil.toAppliedDirectives(
                        schema.getSchemaAppliedDirectives(),
                        schema.getSchemaDirectives()),
                schemaBuilderRoot());
        return builder().build();
    }

    @Internal
    public SUObjectType importIntrospectionSchemaType(
            SUSchemaBuilder targetBuilder,
            GraphQLObjectType introspectionSchemaType) {
        assertTrue(schemaBuilder == null, "This schema universe importer has already been used");
        schemaBuilder = assertNotNull(targetBuilder);
        for (GraphQLDirective directive : Directives.BUILT_IN_DIRECTIVES) {
            directiveDefinitions.put(directive.getName(), directive);
        }
        List<GraphQLNamedType> types =
                collectNamedTypes(assertNotNull(introspectionSchemaType));
        createNamedTypeVertices(types);
        expandNamedTypes(types);
        SUObjectType imported =
                (SUObjectType) namedType(introspectionSchemaType);
        builder().introspectionSchemaType(imported);
        return imported;
    }

    private List<GraphQLNamedType> collectNamedTypes(
            GraphQLObjectType introspectionSchemaType) {
        GraphQLTypeCollectingVisitor collectingVisitor =
                new GraphQLTypeCollectingVisitor();
        SchemaTraverser traverser =
                new SchemaTraverser(GraphQLSchemaElement::getChildren);
        traverser.depthFirst(
                collectingVisitor,
                List.of(introspectionSchemaType));
        return new ArrayList<>(
                collectingVisitor.getResult().values());
    }

    private void createNamedTypeVertices(List<GraphQLNamedType> types) {
        for (GraphQLNamedType type : types) {
            SUNamedType vertex = builder().getNamedType(type.getName());
            if (vertex == null) {
                vertex = createNamedTypeVertex(type);
            } else {
                assertCompatibleNamedType(type, vertex);
            }
            namedTypes.put(type.getName(), vertex);
            elementVertices.put(type, vertex);
            builder().addType(vertex);
        }
    }

    private void assertCompatibleNamedType(
            GraphQLNamedType graphQLType,
            SUNamedType universeType) {
        boolean compatible =
                graphQLType instanceof GraphQLObjectType
                        && universeType instanceof SUObjectType
                        || graphQLType instanceof GraphQLInterfaceType
                        && universeType instanceof SUInterfaceType
                        || graphQLType instanceof GraphQLUnionType
                        && universeType instanceof SUUnionType
                        || graphQLType instanceof GraphQLEnumType
                        && universeType instanceof SUEnumType
                        || graphQLType instanceof GraphQLScalarType
                        && universeType instanceof SUScalarType
                        || graphQLType instanceof GraphQLInputObjectType
                        && universeType instanceof SUInputObjectType;
        assertTrue(
                compatible,
                "Type '%s' has an incompatible schema universe kind",
                graphQLType.getName());
    }

    private SUNamedType createNamedTypeVertex(GraphQLNamedType type) {
        if (type instanceof GraphQLObjectType) {
            GraphQLObjectType objectType = (GraphQLObjectType) type;
            SUObjectType vertex = universe.newObjectType(
                    objectType.getName(),
                    objectType.getDescription());
            captureAstDefinitions(
                    vertex,
                    objectType.getDefinition(),
                    objectType.getExtensionDefinitions());
            return vertex;
        }
        if (type instanceof GraphQLInterfaceType) {
            GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) type;
            SUInterfaceType vertex = universe.newInterfaceType(
                    interfaceType.getName(),
                    interfaceType.getDescription());
            captureAstDefinitions(
                    vertex,
                    interfaceType.getDefinition(),
                    interfaceType.getExtensionDefinitions());
            return vertex;
        }
        if (type instanceof GraphQLUnionType) {
            GraphQLUnionType unionType = (GraphQLUnionType) type;
            SUUnionType vertex = universe.newUnionType(
                    unionType.getName(),
                    unionType.getDescription());
            captureAstDefinitions(
                    vertex,
                    unionType.getDefinition(),
                    unionType.getExtensionDefinitions());
            return vertex;
        }
        if (type instanceof GraphQLEnumType) {
            GraphQLEnumType enumType = (GraphQLEnumType) type;
            SUEnumType vertex = universe.newEnumType(
                    enumType.getName(),
                    enumType.getDescription());
            captureAstDefinitions(
                    vertex,
                    enumType.getDefinition(),
                    enumType.getExtensionDefinitions());
            return vertex;
        }
        if (type instanceof GraphQLScalarType) {
            GraphQLScalarType scalarType = (GraphQLScalarType) type;
            SUScalarType vertex = universe.newScalarType(
                    scalarType.getName(),
                    scalarType.getDescription());
            captureAstDefinitions(
                    vertex,
                    scalarType.getDefinition(),
                    scalarType.getExtensionDefinitions());
            return vertex;
        }
        if (type instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType inputType = (GraphQLInputObjectType) type;
            SUInputObjectType vertex = universe.newInputObjectType(
                    inputType.getName(),
                    inputType.getDescription());
            captureAstDefinitions(
                    vertex,
                    inputType.getDefinition(),
                    inputType.getExtensionDefinitions());
            return vertex;
        }
        return assertShouldNeverHappen("Unsupported schema universe named type %s", type.getClass().getName());
    }

    private void addRootTypes(GraphQLSchema schema) {
        SUObjectType query = (SUObjectType) namedType(schema.getQueryType());
        builder().queryType(query);
        GraphQLObjectType mutationType = schema.getMutationType();
        if (mutationType != null) {
            builder().mutationType((SUObjectType) namedType(mutationType));
        }
        GraphQLObjectType subscriptionType = schema.getSubscriptionType();
        if (subscriptionType != null) {
            builder().subscriptionType((SUObjectType) namedType(subscriptionType));
        }
    }

    private void addDirectiveDefinitions(List<GraphQLDirective> directives) {
        for (GraphQLDirective directive : directives) {
            directiveDefinitions.put(directive.getName(), directive);
            SUDirective vertex = universe.newDirective(
                    directive.getName(),
                    directive.getDescription(),
                    directive.isRepeatable(),
                    directive.validLocations());
            captureAstDefinitions(
                    vertex,
                    directive.getDefinition(),
                    directive.getExtensionDefinitions());
            elementVertices.put(directive, vertex);
            builder().addDirectiveDefinition(vertex);
        }
        for (GraphQLDirective directive : directives) {
            SUDirective vertex = directiveVertex(directive);
            for (GraphQLArgument argument : directive.getArguments()) {
                addArgument(vertex, argument);
            }
        }
        for (GraphQLDirective directive : directives) {
            addAppliedDirectives(directive, directiveVertex(directive));
        }
    }

    private void expandNamedTypes(List<GraphQLNamedType> types) {
        for (GraphQLNamedType type : types) {
            SUNamedType vertex = namedType(type);
            if (type instanceof GraphQLObjectType) {
                expandObject((GraphQLObjectType) type, (SUObjectType) vertex);
                continue;
            }
            if (type instanceof GraphQLInterfaceType) {
                expandInterface((GraphQLInterfaceType) type, (SUInterfaceType) vertex);
                continue;
            }
            if (type instanceof GraphQLUnionType) {
                expandUnion((GraphQLUnionType) type, (SUUnionType) vertex);
                continue;
            }
            if (type instanceof GraphQLEnumType) {
                expandEnum((GraphQLEnumType) type, (SUEnumType) vertex);
                continue;
            }
            if (type instanceof GraphQLInputObjectType) {
                expandInputObject((GraphQLInputObjectType) type, (SUInputObjectType) vertex);
                continue;
            }
            if (type instanceof GraphQLDirectiveContainer) {
                addAppliedDirectives((GraphQLDirectiveContainer) type, vertex);
                addSpecifiedByDirective(type, vertex);
            }
        }
    }

    private void expandObject(GraphQLObjectType type, SUObjectType vertex) {
        for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
            addField(vertex, field);
        }
        for (GraphQLNamedOutputType interfaceType : type.getInterfaces()) {
            builder().addInterface(vertex, (SUInterfaceType) typeVertex(interfaceType));
        }
        addAppliedDirectives(type, vertex);
    }

    private void expandInterface(GraphQLInterfaceType type, SUInterfaceType vertex) {
        for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
            addField(vertex, field);
        }
        for (GraphQLNamedOutputType interfaceType : type.getInterfaces()) {
            builder().addInterface(vertex, (SUInterfaceType) typeVertex(interfaceType));
        }
        addAppliedDirectives(type, vertex);
    }

    private void expandUnion(GraphQLUnionType type, SUUnionType vertex) {
        for (GraphQLNamedOutputType memberType : type.getTypes()) {
            builder().addUnionMember(vertex, (SUObjectType) typeVertex(memberType));
        }
        addAppliedDirectives(type, vertex);
    }

    private void expandEnum(GraphQLEnumType type, SUEnumType vertex) {
        for (GraphQLEnumValueDefinition value : type.getValues()) {
            SUEnumValue valueVertex = enumValue(value);
            builder().addEnumValue(vertex, valueVertex);
            addAppliedDirectives(value, valueVertex);
        }
        addAppliedDirectives(type, vertex);
    }

    private void expandInputObject(GraphQLInputObjectType type, SUInputObjectType vertex) {
        for (GraphQLInputObjectField field : type.getFields()) {
            SUInputField fieldVertex = inputField(field);
            builder().addInputField(vertex, fieldVertex);
            builder().setInputFieldType(fieldVertex, typeVertex(field.getType()));
            addAppliedDirectives(field, fieldVertex);
        }
        addAppliedDirectives(type, vertex);
    }

    private void addField(SUObjectType parent, GraphQLFieldDefinition field) {
        SUField fieldVertex = field(field);
        builder().addField(parent, fieldVertex);
        builder().setFieldType(fieldVertex, typeVertex(field.getType()));
        for (GraphQLArgument argument : field.getArguments()) {
            addArgument(fieldVertex, argument);
        }
        addAppliedDirectives(field, fieldVertex);
    }

    private void addField(SUInterfaceType parent, GraphQLFieldDefinition field) {
        SUField fieldVertex = field(field);
        builder().addField(parent, fieldVertex);
        builder().setFieldType(fieldVertex, typeVertex(field.getType()));
        for (GraphQLArgument argument : field.getArguments()) {
            addArgument(fieldVertex, argument);
        }
        addAppliedDirectives(field, fieldVertex);
    }

    private void addArgument(SUField parent, GraphQLArgument argument) {
        SUArgument argumentVertex = argument(argument);
        builder().addArgument(parent, argumentVertex);
        builder().setArgumentType(argumentVertex, typeVertex(argument.getType()));
        addAppliedDirectives(argument, argumentVertex);
    }

    private void addArgument(SUDirective parent, GraphQLArgument argument) {
        SUArgument argumentVertex = argument(argument);
        builder().addArgument(parent, argumentVertex);
        builder().setArgumentType(argumentVertex, typeVertex(argument.getType()));
        addAppliedDirectives(argument, argumentVertex);
    }

    private void addAppliedDirectives(
            GraphQLDirectiveContainer container,
            SUAppliedDirectiveContainer containerVertex) {
        List<GraphQLAppliedDirective> directives =
                DirectivesUtil.toAppliedDirectives(container);
        addAppliedDirectives(directives, containerVertex);
        addDeprecatedDirective(container, directives, containerVertex);
    }

    private void addAppliedDirectives(
            List<GraphQLAppliedDirective> directives,
            SUAppliedDirectiveContainer containerVertex) {
        for (GraphQLAppliedDirective directive : directives) {
            SUAppliedDirective directiveVertex = appliedDirective(directive);
            builder().addAppliedDirective(containerVertex, directiveVertex);
        }
    }

    private void addDeprecatedDirective(
            GraphQLDirectiveContainer container,
            List<GraphQLAppliedDirective> directives,
            SUAppliedDirectiveContainer containerVertex) {
        String reason = deprecationReason(container);
        if (reason == null || hasDirective(directives, Directives.DeprecatedDirective.getName())) {
            return;
        }
        addStringDirective(
                Directives.DeprecatedDirective.getName(),
                "reason",
                reason,
                containerVertex);
    }

    private void addSpecifiedByDirective(
            GraphQLNamedType type,
            SUNamedType typeVertex) {
        if (!(type instanceof GraphQLScalarType)) {
            return;
        }
        GraphQLScalarType scalarType = (GraphQLScalarType) type;
        String url = scalarType.getSpecifiedByUrl();
        if (url == null || hasDirective(
                DirectivesUtil.toAppliedDirectives(scalarType),
                Directives.SpecifiedByDirective.getName())) {
            return;
        }
        addStringDirective(
                Directives.SpecifiedByDirective.getName(),
                "url",
                url,
                typeVertex);
    }

    private void addStringDirective(
            String directiveName,
            String argumentName,
            String value,
            SUAppliedDirectiveContainer containerVertex) {
        GraphQLDirective definition = assertNotNull(
                directiveDefinitions.get(directiveName),
                "Directive '%s' is not part of the imported schema",
                directiveName);
        GraphQLArgument argumentDefinition = assertNotNull(
                definition.getArgument(argumentName),
                "Directive '%s' has no argument '%s'",
                directiveName,
                argumentName);
        SUAppliedDirectiveArgument argument =
                universe.newAppliedDirectiveArgument(
                        argumentName,
                        typeVertex(argumentDefinition.getType()),
                        graphql.schema.InputValueWithState.newLiteralValue(
                                StringValue.newStringValue(value).build()));
        SUAppliedDirective directive = universe.newAppliedDirective(
                directiveName,
                List.of(argument));
        builder().addAppliedDirective(containerVertex, directive);
    }

    private boolean hasDirective(
            List<GraphQLAppliedDirective> directives,
            String name) {
        for (GraphQLAppliedDirective directive : directives) {
            if (name.equals(directive.getName())) {
                return true;
            }
        }
        return false;
    }

    private @Nullable String deprecationReason(
            GraphQLDirectiveContainer container) {
        if (container instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) container).getDeprecationReason();
        }
        if (container instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) container).getDeprecationReason();
        }
        if (container instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) container).getDeprecationReason();
        }
        if (container instanceof GraphQLArgument) {
            return ((GraphQLArgument) container).getDeprecationReason();
        }
        if (container instanceof GraphQLDirective) {
            return ((GraphQLDirective) container).getDeprecationReason();
        }
        return null;
    }

    private SUType typeVertex(GraphQLType type) {
        if (type instanceof GraphQLList) {
            return listType((GraphQLList) type);
        }
        if (type instanceof GraphQLNonNull) {
            return nonNullType((GraphQLNonNull) type);
        }
        if (type instanceof GraphQLTypeReference) {
            return assertNotNull(namedTypes.get(((GraphQLTypeReference) type).getName()));
        }
        return namedType((GraphQLNamedType) type);
    }

    private SUListType listType(GraphQLList type) {
        SUVertex existing = elementVertices.get(type);
        if (existing != null) {
            return (SUListType) existing;
        }
        SUListType vertex = universe.newListType();
        elementVertices.put(type, vertex);
        builder().setWrappedType(vertex, typeVertex(type.getWrappedType()));
        return vertex;
    }

    private SUNonNullType nonNullType(GraphQLNonNull type) {
        SUVertex existing = elementVertices.get(type);
        if (existing != null) {
            return (SUNonNullType) existing;
        }
        SUNonNullType vertex = universe.newNonNullType();
        elementVertices.put(type, vertex);
        builder().setWrappedType(vertex, typeVertex(type.getWrappedType()));
        return vertex;
    }

    private SUField field(GraphQLFieldDefinition field) {
        SUVertex existing = elementVertices.get(field);
        if (existing != null) {
            return (SUField) existing;
        }
        SUField vertex = universe.newField(field.getName(), field.getDescription());
        captureAstDefinitions(
                vertex,
                field.getDefinition(),
                List.of());
        elementVertices.put(field, vertex);
        return vertex;
    }

    private SUArgument argument(GraphQLArgument argument) {
        SUVertex existing = elementVertices.get(argument);
        if (existing != null) {
            return (SUArgument) existing;
        }
        SUArgument vertex = universe.newArgument(
                argument.getName(),
                argument.getDescription(),
                argument.getArgumentDefaultValue());
        captureAstDefinitions(
                vertex,
                argument.getDefinition(),
                List.of());
        elementVertices.put(argument, vertex);
        return vertex;
    }

    private SUInputField inputField(GraphQLInputObjectField field) {
        SUVertex existing = elementVertices.get(field);
        if (existing != null) {
            return (SUInputField) existing;
        }
        SUInputField vertex = universe.newInputField(
                field.getName(),
                field.getDescription(),
                field.getInputFieldDefaultValue());
        captureAstDefinitions(
                vertex,
                field.getDefinition(),
                List.of());
        elementVertices.put(field, vertex);
        return vertex;
    }

    private SUEnumValue enumValue(GraphQLEnumValueDefinition value) {
        SUVertex existing = elementVertices.get(value);
        if (existing != null) {
            return (SUEnumValue) existing;
        }
        SUEnumValue vertex = universe.newEnumValue(value.getName(), value.getDescription());
        captureAstDefinitions(
                vertex,
                value.getDefinition(),
                List.of());
        elementVertices.put(value, vertex);
        return vertex;
    }

    private SUAppliedDirective appliedDirective(GraphQLAppliedDirective directive) {
        SUVertex existing = elementVertices.get(directive);
        if (existing != null) {
            return (SUAppliedDirective) existing;
        }
        List<SUAppliedDirectiveArgument> arguments =
                new ArrayList<>(directive.getArguments().size());
        for (GraphQLAppliedDirectiveArgument argument : directive.getArguments()) {
            arguments.add(appliedArgument(argument));
        }
        SUAppliedDirective vertex = universe.newAppliedDirective(
                directive.getName(),
                arguments);
        captureAstDefinitions(
                vertex,
                directive.getDefinition(),
                List.of());
        elementVertices.put(directive, vertex);
        return vertex;
    }

    private SUAppliedDirectiveArgument appliedArgument(GraphQLAppliedDirectiveArgument argument) {
        SUAppliedDirectiveArgument existing = appliedArgumentValues.get(argument);
        if (existing != null) {
            return existing;
        }
        SUAppliedDirectiveArgument value = universe.newAppliedDirectiveArgument(
                argument.getName(),
                typeVertex(argument.getType()),
                argument.getArgumentValue(),
                captureAstDefinitions
                        ? argument.getDefinition()
                        : null);
        appliedArgumentValues.put(argument, value);
        return value;
    }

    private SUDirective directiveVertex(GraphQLDirective directive) {
        return (SUDirective) assertNotNull(
                elementVertices.get(directive),
                "Directive '%s' is not part of the imported schema",
                directive.getName());
    }

    private SUNamedType namedType(GraphQLNamedType type) {
        return assertNotNull(namedTypes.get(type.getName()), "Type '%s' is not part of the imported schema", type.getName());
    }

    private SUSchemaRoot schemaBuilderRoot() {
        return builder().getRoot();
    }

    private SUSchemaBuilder builder() {
        return assertNotNull(schemaBuilder, "Schema import has not started");
    }

    private void captureAstDefinitions(
            SUVertex vertex,
            @Nullable Node<?> definition,
            List<? extends Node<?>> extensionDefinitions) {
        if (captureAstDefinitions) {
            universe.setAstDefinitions(
                    vertex,
                    definition,
                    extensionDefinitions);
        }
    }
}
