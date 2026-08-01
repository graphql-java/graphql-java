package graphql.schema.idl;

import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.ArrayValue;
import graphql.language.AstPrinter;
import graphql.language.BooleanValue;
import graphql.language.Comment;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.InputValueWithState;
import graphql.schema.universe.SUAppliedDirective;
import graphql.schema.universe.SUAppliedDirectiveArgument;
import graphql.schema.universe.SUArgument;
import graphql.schema.universe.SUDirective;
import graphql.schema.universe.SUEnumType;
import graphql.schema.universe.SUInputField;
import graphql.schema.universe.SUInputObjectType;
import graphql.schema.universe.SUInterfaceType;
import graphql.schema.universe.SUListType;
import graphql.schema.universe.SUNonNullType;
import graphql.schema.universe.SUObjectType;
import graphql.schema.universe.SUScalarType;
import graphql.schema.universe.SUSchema;
import graphql.schema.universe.SUUnionType;
import graphql.schema.universe.SUVertex;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;

@Internal
@NullMarked
public final class SUSchemaPrintAccess implements SchemaPrintAccess {

    private final SUSchema schema;
    private final SchemaPrinter.Options options;

    public SUSchemaPrintAccess(
            SUSchema schema,
            SchemaPrinter.Options options) {
        this.schema = assertNotNull(schema);
        this.options = assertNotNull(options);
    }

    @Override
    public Object getSchema() {
        return schema.getRoot();
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
        return new ArrayList<>(schema.getTypes());
    }

    @Override
    public List<Object> getDirectiveDefinitions() {
        return new ArrayList<>(schema.getDirectiveDefinitions());
    }

    @Override
    public SchemaPrintElementKind getKind(Object element) {
        if (element instanceof SUAppliedDirectiveArgument) {
            return SchemaPrintElementKind.APPLIED_DIRECTIVE_ARGUMENT;
        }
        SUVertex vertex = (SUVertex) element;
        switch (vertex.getKind()) {
            case SCHEMA:
                return SchemaPrintElementKind.SCHEMA;
            case OBJECT:
                return SchemaPrintElementKind.OBJECT;
            case FIELD:
                return SchemaPrintElementKind.FIELD;
            case INTERFACE:
                return SchemaPrintElementKind.INTERFACE;
            case UNION:
                return SchemaPrintElementKind.UNION;
            case ENUM:
                return SchemaPrintElementKind.ENUM;
            case ENUM_VALUE:
                return SchemaPrintElementKind.ENUM_VALUE;
            case SCALAR:
                return SchemaPrintElementKind.SCALAR;
            case INPUT_OBJECT:
                return SchemaPrintElementKind.INPUT_OBJECT;
            case INPUT_FIELD:
                return SchemaPrintElementKind.INPUT_FIELD;
            case ARGUMENT:
                return SchemaPrintElementKind.ARGUMENT;
            case DIRECTIVE:
                return SchemaPrintElementKind.DIRECTIVE;
            case APPLIED_DIRECTIVE:
                return SchemaPrintElementKind.APPLIED_DIRECTIVE;
            case LIST:
                return SchemaPrintElementKind.LIST;
            case NON_NULL:
                return SchemaPrintElementKind.NON_NULL;
            default:
                return assertShouldNeverHappen(
                        "Unsupported schema universe print vertex %s",
                        vertex);
        }
    }

    @Override
    public String getName(Object element) {
        if (element instanceof SUAppliedDirectiveArgument) {
            return ((SUAppliedDirectiveArgument) element).getName();
        }
        return assertNotNull(((SUVertex) element).getName());
    }

    @Override
    public @Nullable String getDescription(Object element) {
        if (element instanceof SUAppliedDirectiveArgument) {
            return null;
        }
        return ((SUVertex) element).getDescription();
    }

    @Override
    public @Nullable String getAstDefinitionComments(Object element) {
        Node<?> definition = definition(element);
        if (definition == null || definition.getComments().isEmpty()) {
            return null;
        }
        return definition.getComments().stream()
                .map(Comment::getContent)
                .collect(Collectors.joining("\n", "", "\n"));
    }

    @Override
    public boolean isIncluded(Object element) {
        return true;
    }

    @Override
    public boolean isIntrospectionType(Object type) {
        return !options.isIncludeIntrospectionTypes()
                && getName(type).startsWith("__");
    }

    @Override
    public boolean isSpecifiedScalar(Object type) {
        return ScalarInfo.isGraphqlSpecifiedScalar(getName(type));
    }

    @Override
    public List<Object> getFields(Object type) {
        if (type instanceof SUObjectType) {
            return new ArrayList<>(schema.getFields((SUObjectType) type));
        }
        return new ArrayList<>(schema.getFields((SUInterfaceType) type));
    }

    @Override
    public List<Object> getArguments(Object fieldOrDirective) {
        if (fieldOrDirective instanceof SUDirective) {
            return new ArrayList<>(
                    schema.getArguments((SUDirective) fieldOrDirective));
        }
        return new ArrayList<>(
                schema.getArguments((graphql.schema.universe.SUField) fieldOrDirective));
    }

    @Override
    public Object getType(Object typedElement) {
        if (typedElement instanceof SUAppliedDirectiveArgument) {
            return schema.getType((SUAppliedDirectiveArgument) typedElement);
        }
        if (typedElement instanceof SUArgument) {
            return assertNotNull(schema.getType((SUArgument) typedElement));
        }
        if (typedElement instanceof SUInputField) {
            return assertNotNull(schema.getType((SUInputField) typedElement));
        }
        return assertNotNull(
                schema.getType((graphql.schema.universe.SUField) typedElement));
    }

    @Override
    public List<Object> getInterfaces(Object type) {
        if (type instanceof SUObjectType) {
            return new ArrayList<>(schema.getInterfaces((SUObjectType) type));
        }
        return new ArrayList<>(schema.getInterfaces((SUInterfaceType) type));
    }

    @Override
    public List<Object> getUnionMembers(Object unionType) {
        return new ArrayList<>(
                schema.getUnionMembers((SUUnionType) unionType));
    }

    @Override
    public List<Object> getEnumValues(Object enumType) {
        return new ArrayList<>(
                schema.getEnumValues((SUEnumType) enumType));
    }

    @Override
    public List<Object> getInputFields(Object inputObjectType) {
        return new ArrayList<>(
                schema.getInputFields((SUInputObjectType) inputObjectType));
    }

    @Override
    public List<Object> getAppliedDirectives(Object container) {
        return new ArrayList<>(
                schema.getAppliedDirectives((SUVertex) container));
    }

    @Override
    public List<Object> getAppliedDirectiveArguments(Object directive) {
        return new ArrayList<>(
                schema.getArguments((SUAppliedDirective) directive));
    }

    @Override
    public InputValueWithState getDefaultValue(Object argumentOrInputField) {
        if (argumentOrInputField instanceof SUArgument) {
            return ((SUArgument) argumentOrInputField).getArgumentDefaultValue();
        }
        return ((SUInputField) argumentOrInputField).getInputFieldDefaultValue();
    }

    @Override
    public InputValueWithState getAppliedDirectiveArgumentValue(Object argument) {
        return ((SUAppliedDirectiveArgument) argument).getArgumentValue();
    }

    @Override
    public boolean isRepeatable(Object directive) {
        return ((SUDirective) directive).isRepeatable();
    }

    @Override
    public Set<DirectiveLocation> getDirectiveLocations(Object directive) {
        return ((SUDirective) directive).validLocations();
    }

    @Override
    public @Nullable String getSpecifiedByUrl(Object scalar) {
        for (SUAppliedDirective directive :
                schema.getAppliedDirectives(
                        (SUScalarType) scalar,
                        "specifiedBy")) {
            SUAppliedDirectiveArgument url = schema.getArgument(directive, "url");
            if (url == null || url.getArgumentValue().isNotSet()) {
                continue;
            }
            Object value = url.getArgumentValue().getValue();
            if (value instanceof StringValue) {
                return ((StringValue) value).getValue();
            }
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    @Override
    public String getTypeString(Object type) {
        SUVertex vertex = (SUVertex) type;
        if (vertex instanceof SUListType) {
            return "[" + getTypeString(assertNotNull(
                    schema.getWrappedType((SUListType) vertex))) + "]";
        }
        if (vertex instanceof SUNonNullType) {
            return getTypeString(assertNotNull(
                    schema.getWrappedType((SUNonNullType) vertex))) + "!";
        }
        return assertNotNull(vertex.getName());
    }

    @Override
    public String printValue(InputValueWithState value, Object type) {
        if (value.isLiteral()) {
            return AstPrinter.printAst((Value<?>) assertNotNull(value.getValue()));
        }
        return AstPrinter.printAst(toLiteral(value.getValue(), (SUVertex) type));
    }

    @Override
    public List<Object> sort(
            @Nullable Object parent,
            SchemaPrintChildKind childKind,
            List<Object> elements) {
        List<Object> result = new ArrayList<>(elements);
        if (options.getComparatorRegistry() == GraphqlTypeComparatorRegistry.AS_IS_REGISTRY) {
            return result;
        }
        Comparator<Object> comparator = Comparator.comparing(this::getName);
        if (childKind == SchemaPrintChildKind.TOP_LEVEL
                && options.getComparatorRegistry()
                != GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY) {
            comparator = Comparator.comparingInt(this::topLevelRank)
                    .thenComparing(this::getName);
        }
        result.sort(comparator);
        return result;
    }

    private @Nullable Node<?> definition(Object element) {
        if (element instanceof SUArgument) {
            return ((SUArgument) element).getDefinition();
        }
        if (element instanceof SUInputField) {
            return ((SUInputField) element).getDefinition();
        }
        if (element instanceof SUDirective) {
            return ((SUDirective) element).getDefinition();
        }
        if (element instanceof SUAppliedDirective) {
            return ((SUAppliedDirective) element).getDefinition();
        }
        if (element instanceof SUAppliedDirectiveArgument) {
            return ((SUAppliedDirectiveArgument) element).getDefinition();
        }
        return null;
    }

    private int topLevelRank(Object element) {
        switch (getKind(element)) {
            case DIRECTIVE:
                return 1;
            case INTERFACE:
                return 2;
            case UNION:
                return 3;
            case OBJECT:
                return 4;
            case ENUM:
                return 5;
            case SCALAR:
                return 6;
            case INPUT_OBJECT:
                return 7;
            default:
                return 0;
        }
    }

    private Value<?> toLiteral(@Nullable Object value, SUVertex type) {
        if (type instanceof SUNonNullType) {
            return toLiteral(
                    value,
                    assertNotNull(schema.getWrappedType((SUNonNullType) type)));
        }
        if (value == null) {
            return NullValue.of();
        }
        if (type instanceof SUListType) {
            return listLiteral(value, (SUListType) type);
        }
        if (type instanceof SUInputObjectType) {
            return objectLiteral(value, (SUInputObjectType) type);
        }
        if (type instanceof SUEnumType) {
            return EnumValue.newEnumValue(String.valueOf(value)).build();
        }
        return scalarLiteral(value, assertNotNull(type.getName()));
    }

    private Value<?> listLiteral(Object value, SUListType type) {
        SUVertex wrappedType = assertNotNull(schema.getWrappedType(type));
        List<Value> values = new ArrayList<>();
        if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
                values.add(toLiteral(item, wrappedType));
            }
        } else if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                values.add(toLiteral(Array.get(value, i), wrappedType));
            }
        } else {
            values.add(toLiteral(value, wrappedType));
        }
        return ArrayValue.newArrayValue().values(values).build();
    }

    private Value<?> objectLiteral(Object value, SUInputObjectType type) {
        if (!(value instanceof Map<?, ?>)) {
            return assertShouldNeverHappen(
                    "Cannot print value '%s' for input object '%s' without a map",
                    value,
                    getName(type));
        }
        Map<?, ?> map = (Map<?, ?>) value;
        List<ObjectField> fields = new ArrayList<>();
        for (SUInputField field : schema.getInputFields(type)) {
            String name = assertNotNull(field.getName());
            if (!map.containsKey(name)) {
                continue;
            }
            fields.add(ObjectField.newObjectField()
                    .name(name)
                    .value(toLiteral(
                            map.get(name),
                            assertNotNull(schema.getType(field))))
                    .build());
        }
        return ObjectValue.newObjectValue().objectFields(fields).build();
    }

    private Value<?> scalarLiteral(Object value, String scalarName) {
        if ("String".equals(scalarName) || "ID".equals(scalarName)) {
            return StringValue.newStringValue(String.valueOf(value)).build();
        }
        if ("Boolean".equals(scalarName) && value instanceof Boolean) {
            return BooleanValue.newBooleanValue((Boolean) value).build();
        }
        if ("Int".equals(scalarName) && value instanceof Number) {
            return IntValue.newIntValue(new BigInteger(String.valueOf(value))).build();
        }
        if ("Float".equals(scalarName) && value instanceof Number) {
            return FloatValue.newFloatValue(new BigDecimal(String.valueOf(value))).build();
        }
        return assertShouldNeverHappen(
                "Cannot print a non-literal value for custom scalar '%s' "
                        + "because SUSchema does not retain its coercing",
                scalarName);
    }
}
