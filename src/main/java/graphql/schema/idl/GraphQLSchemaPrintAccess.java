package graphql.schema.idl;

import graphql.Directives;
import graphql.DirectivesUtil;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.ValuesResolver;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Comment;
import graphql.language.Description;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.Node;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.UnionTypeDefinition;
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
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlTypeComparatorEnvironment;
import graphql.schema.InputValueWithState;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

@Internal
@NullMarked
public final class GraphQLSchemaPrintAccess implements SchemaPrintAccess {

    private final GraphQLSchema schema;
    private final SchemaPrinter.Options options;
    private final GraphqlFieldVisibility visibility;

    public GraphQLSchemaPrintAccess(
            GraphQLSchema schema,
            SchemaPrinter.Options options) {
        this.schema = assertNotNull(schema);
        this.options = assertNotNull(options);
        this.visibility = schema.getCodeRegistry().getFieldVisibility();
    }

    @Override
    public Object getSchema() {
        return schema;
    }

    @Override
    public Object getQueryType() {
        return schema.getQueryType();
    }

    @Override
    public @Nullable Object getMutationType() {
        return schema.getMutationType();
    }

    @Override
    public @Nullable Object getSubscriptionType() {
        return schema.getSubscriptionType();
    }

    @Override
    public List<Object> getTypes() {
        return new ArrayList<>(schema.getAllTypesAsList());
    }

    @Override
    public List<Object> getDirectiveDefinitions() {
        return new ArrayList<>(schema.getDirectives());
    }

    @Override
    public SchemaPrintElementKind getKind(Object element) {
        if (element instanceof GraphQLSchema) {
            return SchemaPrintElementKind.SCHEMA;
        }
        if (element instanceof GraphQLObjectType) {
            return SchemaPrintElementKind.OBJECT;
        }
        if (element instanceof GraphQLFieldDefinition) {
            return SchemaPrintElementKind.FIELD;
        }
        if (element instanceof GraphQLInterfaceType) {
            return SchemaPrintElementKind.INTERFACE;
        }
        if (element instanceof GraphQLUnionType) {
            return SchemaPrintElementKind.UNION;
        }
        if (element instanceof GraphQLEnumType) {
            return SchemaPrintElementKind.ENUM;
        }
        if (element instanceof GraphQLEnumValueDefinition) {
            return SchemaPrintElementKind.ENUM_VALUE;
        }
        if (element instanceof GraphQLScalarType) {
            return SchemaPrintElementKind.SCALAR;
        }
        if (element instanceof GraphQLInputObjectType) {
            return SchemaPrintElementKind.INPUT_OBJECT;
        }
        if (element instanceof GraphQLInputObjectField) {
            return SchemaPrintElementKind.INPUT_FIELD;
        }
        if (element instanceof GraphQLArgument) {
            return SchemaPrintElementKind.ARGUMENT;
        }
        if (element instanceof GraphQLDirective) {
            return SchemaPrintElementKind.DIRECTIVE;
        }
        if (element instanceof GraphQLAppliedDirective) {
            return SchemaPrintElementKind.APPLIED_DIRECTIVE;
        }
        if (element instanceof GraphQLAppliedDirectiveArgument) {
            return SchemaPrintElementKind.APPLIED_DIRECTIVE_ARGUMENT;
        }
        if (element instanceof GraphQLList) {
            return SchemaPrintElementKind.LIST;
        }
        if (element instanceof GraphQLNonNull) {
            return SchemaPrintElementKind.NON_NULL;
        }
        return assertShouldNeverHappen(
                "Unsupported GraphQL schema print element %s",
                element.getClass().getName());
    }

    @Override
    public String getName(Object element) {
        if (element instanceof GraphQLNamedType) {
            return ((GraphQLNamedType) element).getName();
        }
        if (element instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) element).getName();
        }
        if (element instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) element).getName();
        }
        if (element instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) element).getName();
        }
        if (element instanceof GraphQLArgument) {
            return ((GraphQLArgument) element).getName();
        }
        if (element instanceof GraphQLDirective) {
            return ((GraphQLDirective) element).getName();
        }
        if (element instanceof GraphQLAppliedDirective) {
            return ((GraphQLAppliedDirective) element).getName();
        }
        if (element instanceof GraphQLAppliedDirectiveArgument) {
            return ((GraphQLAppliedDirectiveArgument) element).getName();
        }
        return assertShouldNeverHappen(
                "Schema print element has no name: %s",
                element.getClass().getName());
    }

    @Override
    public @Nullable String getDescription(Object element) {
        if (element instanceof GraphQLSchema) {
            GraphQLSchema graphQLSchema = (GraphQLSchema) element;
            SchemaDefinition definition = graphQLSchema.getDefinition();
            return description(
                    graphQLSchema.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLObjectType) {
            GraphQLObjectType type = (GraphQLObjectType) element;
            ObjectTypeDefinition definition = type.getDefinition();
            return description(
                    type.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLFieldDefinition) {
            GraphQLFieldDefinition field = (GraphQLFieldDefinition) element;
            FieldDefinition definition = field.getDefinition();
            return description(
                    field.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLInterfaceType) {
            GraphQLInterfaceType type = (GraphQLInterfaceType) element;
            InterfaceTypeDefinition definition = type.getDefinition();
            return description(
                    type.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLUnionType) {
            GraphQLUnionType type = (GraphQLUnionType) element;
            UnionTypeDefinition definition = type.getDefinition();
            return description(
                    type.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLEnumType) {
            GraphQLEnumType type = (GraphQLEnumType) element;
            EnumTypeDefinition definition = type.getDefinition();
            return description(
                    type.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLEnumValueDefinition) {
            GraphQLEnumValueDefinition value =
                    (GraphQLEnumValueDefinition) element;
            EnumValueDefinition definition = value.getDefinition();
            return description(
                    value.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLScalarType) {
            GraphQLScalarType type = (GraphQLScalarType) element;
            ScalarTypeDefinition definition = type.getDefinition();
            return description(
                    type.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLInputObjectType) {
            GraphQLInputObjectType type = (GraphQLInputObjectType) element;
            InputObjectTypeDefinition definition = type.getDefinition();
            return description(
                    type.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLInputObjectField) {
            GraphQLInputObjectField field = (GraphQLInputObjectField) element;
            InputValueDefinition definition = field.getDefinition();
            return description(
                    field.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLArgument) {
            GraphQLArgument argument = (GraphQLArgument) element;
            InputValueDefinition definition = argument.getDefinition();
            return description(
                    argument.getDescription(),
                    definition == null ? null : definition.getDescription());
        }
        if (element instanceof GraphQLDirective) {
            return ((GraphQLDirective) element).getDescription();
        }
        return null;
    }

    private @Nullable String description(
            @Nullable String runtimeDescription,
            @Nullable Description astDescription) {
        if (runtimeDescription != null && !runtimeDescription.isEmpty()) {
            return runtimeDescription;
        }
        return astDescription == null ? runtimeDescription : astDescription.getContent();
    }

    @Override
    public @Nullable String getAstDefinitionComments(Object element) {
        Node<?> definition = getDefinition(element);
        if (definition == null || definition.getComments().isEmpty()) {
            return null;
        }
        return definition.getComments().stream()
                .map(Comment::getContent)
                .collect(Collectors.joining("\n", "", "\n"));
    }

    @Override
    public boolean isIncluded(Object element) {
        return options.getIncludeSchemaElement().test((GraphQLSchemaElement) element);
    }

    @Override
    public boolean isIntrospectionType(Object type) {
        return !options.isIncludeIntrospectionTypes()
                && getName(type).startsWith("__");
    }

    @Override
    public boolean isSpecifiedScalar(Object type) {
        return ScalarInfo.isGraphqlSpecifiedScalar((GraphQLScalarType) type);
    }

    @Override
    public List<Object> getFields(Object type) {
        if (type instanceof GraphQLObjectType) {
            return new ArrayList<>(
                    visibility.getFieldDefinitions((GraphQLObjectType) type));
        }
        return new ArrayList<>(
                visibility.getFieldDefinitions((GraphQLInterfaceType) type));
    }

    @Override
    public List<Object> getArguments(Object fieldOrDirective) {
        if (fieldOrDirective instanceof GraphQLFieldDefinition) {
            return new ArrayList<>(
                    ((GraphQLFieldDefinition) fieldOrDirective).getArguments());
        }
        return new ArrayList<>(((GraphQLDirective) fieldOrDirective).getArguments());
    }

    @Override
    public Object getType(Object typedElement) {
        if (typedElement instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) typedElement).getType();
        }
        if (typedElement instanceof GraphQLArgument) {
            return ((GraphQLArgument) typedElement).getType();
        }
        if (typedElement instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) typedElement).getType();
        }
        return ((GraphQLAppliedDirectiveArgument) typedElement).getType();
    }

    @Override
    public List<Object> getInterfaces(Object type) {
        if (type instanceof GraphQLObjectType) {
            return new ArrayList<>(((GraphQLObjectType) type).getInterfaces());
        }
        return new ArrayList<>(((GraphQLInterfaceType) type).getInterfaces());
    }

    @Override
    public List<Object> getUnionMembers(Object unionType) {
        return new ArrayList<>(((GraphQLUnionType) unionType).getTypes());
    }

    @Override
    public List<Object> getEnumValues(Object enumType) {
        return new ArrayList<>(((GraphQLEnumType) enumType).getValues());
    }

    @Override
    public List<Object> getInputFields(Object inputObjectType) {
        return new ArrayList<>(
                visibility.getFieldDefinitions((GraphQLInputObjectType) inputObjectType));
    }

    @Override
    public List<Object> getAppliedDirectives(Object container) {
        if (container instanceof GraphQLSchema) {
            GraphQLSchema graphQLSchema = (GraphQLSchema) container;
            return new ArrayList<>(DirectivesUtil.toAppliedDirectives(
                    graphQLSchema.getSchemaAppliedDirectives(),
                    graphQLSchema.getSchemaDirectives()));
        }
        GraphQLDirectiveContainer directiveContainer =
                (GraphQLDirectiveContainer) container;
        List<GraphQLAppliedDirective> directives =
                DirectivesUtil.toAppliedDirectives(directiveContainer);
        if (!isDeprecated(container)) {
            return new ArrayList<>(directives);
        }
        return new ArrayList<>(withDeprecationReason(
                directives,
                assertNotNull(getDeprecationReason(container))));
    }

    @Override
    public List<Object> getAppliedDirectiveArguments(Object directive) {
        return new ArrayList<>(
                ((GraphQLAppliedDirective) directive).getArguments());
    }

    @Override
    public InputValueWithState getDefaultValue(Object argumentOrInputField) {
        if (argumentOrInputField instanceof GraphQLArgument) {
            return ((GraphQLArgument) argumentOrInputField).getArgumentDefaultValue();
        }
        return ((GraphQLInputObjectField) argumentOrInputField)
                .getInputFieldDefaultValue();
    }

    @Override
    public InputValueWithState getAppliedDirectiveArgumentValue(Object argument) {
        return ((GraphQLAppliedDirectiveArgument) argument).getArgumentValue();
    }

    @Override
    public boolean isRepeatable(Object directive) {
        return ((GraphQLDirective) directive).isRepeatable();
    }

    @Override
    public Set<DirectiveLocation> getDirectiveLocations(Object directive) {
        return ((GraphQLDirective) directive).validLocations();
    }

    @Override
    public @Nullable String getSpecifiedByUrl(Object scalar) {
        return ((GraphQLScalarType) scalar).getSpecifiedByUrl();
    }

    @Override
    public String getTypeString(Object type) {
        return GraphQLTypeUtil.simplePrint((GraphQLType) type);
    }

    @Override
    public String printValue(InputValueWithState value, Object type) {
        return graphql.language.AstPrinter.printAst(ValuesResolver.valueToLiteral(
                value,
                (GraphQLInputType) type,
                GraphQLContext.getDefault(),
                Locale.getDefault()));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Object> sort(
            @Nullable Object parent,
            SchemaPrintChildKind childKind,
            List<Object> elements) {
        Class<? extends GraphQLSchemaElement> parentType =
                parent == null ? GraphQLSchemaElement.class : elementClass(parent);
        Class<? extends GraphQLSchemaElement> elementType = childClass(childKind);
        GraphqlTypeComparatorEnvironment environment =
                GraphqlTypeComparatorEnvironment.newEnvironment()
                        .parentType(parentType)
                        .elementType(elementType)
                        .build();
        Comparator comparator = options.getComparatorRegistry().getComparator(environment);
        List<Object> result = new ArrayList<>(elements);
        result.sort(comparator);
        return result;
    }

    private @Nullable Node<?> getDefinition(Object element) {
        if (element instanceof GraphQLSchema) {
            return ((GraphQLSchema) element).getDefinition();
        }
        if (element instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) element).getDefinition();
        }
        if (element instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) element).getDefinition();
        }
        if (element instanceof GraphQLInterfaceType) {
            return ((GraphQLInterfaceType) element).getDefinition();
        }
        if (element instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) element).getDefinition();
        }
        if (element instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) element).getDefinition();
        }
        if (element instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) element).getDefinition();
        }
        if (element instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) element).getDefinition();
        }
        if (element instanceof GraphQLInputObjectType) {
            return ((GraphQLInputObjectType) element).getDefinition();
        }
        if (element instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) element).getDefinition();
        }
        if (element instanceof GraphQLArgument) {
            return ((GraphQLArgument) element).getDefinition();
        }
        if (element instanceof GraphQLDirective) {
            return ((GraphQLDirective) element).getDefinition();
        }
        return null;
    }

    private boolean isDeprecated(Object element) {
        if (element instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) element).isDeprecated();
        }
        if (element instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) element).isDeprecated();
        }
        if (element instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) element).isDeprecated();
        }
        if (element instanceof GraphQLArgument) {
            return ((GraphQLArgument) element).isDeprecated();
        }
        return false;
    }

    private @Nullable String getDeprecationReason(Object element) {
        if (element instanceof GraphQLFieldDefinition) {
            return ((GraphQLFieldDefinition) element).getDeprecationReason();
        }
        if (element instanceof GraphQLEnumValueDefinition) {
            return ((GraphQLEnumValueDefinition) element).getDeprecationReason();
        }
        if (element instanceof GraphQLInputObjectField) {
            return ((GraphQLInputObjectField) element).getDeprecationReason();
        }
        if (element instanceof GraphQLArgument) {
            return ((GraphQLArgument) element).getDeprecationReason();
        }
        return null;
    }

    private List<GraphQLAppliedDirective> withDeprecationReason(
            List<GraphQLAppliedDirective> directives,
            String reason) {
        List<GraphQLAppliedDirective> result = new ArrayList<>(directives.size() + 1);
        boolean found = false;
        for (GraphQLAppliedDirective directive : directives) {
            if (!Directives.DeprecatedDirective.getName().equals(directive.getName())) {
                result.add(directive);
                continue;
            }
            found = true;
            GraphQLAppliedDirectiveArgument existing = directive.getArgument("reason");
            if (existing != null
                    && existing.getArgumentValue() == InputValueWithState.NOT_SET) {
                result.add(directive);
                continue;
            }
            result.add(directive.transform(builder ->
                    builder.argument(deprecationReasonArgument(reason))));
        }
        if (!found) {
            result.add(GraphQLAppliedDirective.newDirective()
                    .name(Directives.DeprecatedDirective.getName())
                    .argument(deprecationReasonArgument(reason))
                    .build());
        }
        return result;
    }

    private GraphQLAppliedDirectiveArgument deprecationReasonArgument(String reason) {
        return GraphQLAppliedDirectiveArgument.newArgument()
                .name("reason")
                .valueProgrammatic(reason)
                .type(graphql.Scalars.GraphQLString)
                .build();
    }

    private Class<? extends GraphQLSchemaElement> elementClass(Object element) {
        switch (getKind(element)) {
            case SCHEMA:
                return GraphQLSchemaElement.class;
            case OBJECT:
                return GraphQLObjectType.class;
            case FIELD:
                return GraphQLFieldDefinition.class;
            case INTERFACE:
                return GraphQLInterfaceType.class;
            case UNION:
                return GraphQLUnionType.class;
            case ENUM:
                return GraphQLEnumType.class;
            case ENUM_VALUE:
                return GraphQLEnumValueDefinition.class;
            case SCALAR:
                return GraphQLScalarType.class;
            case INPUT_OBJECT:
                return GraphQLInputObjectType.class;
            case INPUT_FIELD:
                return GraphQLInputObjectField.class;
            case ARGUMENT:
                return GraphQLArgument.class;
            case DIRECTIVE:
                return GraphQLDirective.class;
            case APPLIED_DIRECTIVE:
                return GraphQLAppliedDirective.class;
            case APPLIED_DIRECTIVE_ARGUMENT:
                return GraphQLAppliedDirectiveArgument.class;
            default:
                return assertShouldNeverHappen(
                        "No comparator class for %s",
                        element.getClass().getName());
        }
    }

    private @Nullable Class<? extends GraphQLSchemaElement> childClass(
            SchemaPrintChildKind childKind) {
        switch (childKind) {
            case TOP_LEVEL:
                return null;
            case FIELD:
                return GraphQLFieldDefinition.class;
            case ARGUMENT:
                return GraphQLArgument.class;
            case INTERFACE:
            case UNION_MEMBER:
                return GraphQLOutputType.class;
            case ENUM_VALUE:
                return GraphQLEnumValueDefinition.class;
            case INPUT_FIELD:
                return GraphQLInputObjectField.class;
            case APPLIED_DIRECTIVE:
                return GraphQLAppliedDirective.class;
            case APPLIED_DIRECTIVE_ARGUMENT:
                return GraphQLAppliedDirectiveArgument.class;
            default:
                return assertShouldNeverHappen("Unsupported schema print child kind");
        }
    }
}
