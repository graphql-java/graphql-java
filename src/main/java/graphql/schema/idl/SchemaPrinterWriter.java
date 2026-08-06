package graphql.schema.idl;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.ArrayValue;
import graphql.language.AstPrinter;
import graphql.language.Comment;
import graphql.language.DescribedNode;
import graphql.language.EnumValue;
import graphql.language.Node;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.ExecutableSchema;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphqlTypeComparatorEnvironment;
import graphql.schema.GraphqlTypeComparatorRegistry;
import graphql.schema.InputValueWithState;
import graphql.schema.SchemaAppliedDirective;
import graphql.schema.SchemaAppliedDirectiveArgument;
import graphql.schema.SchemaArgument;
import graphql.schema.SchemaDirective;
import graphql.schema.SchemaEnum;
import graphql.schema.SchemaEnumValue;
import graphql.schema.SchemaField;
import graphql.schema.SchemaFieldsContainer;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaInputType;
import graphql.schema.SchemaInterface;
import graphql.schema.SchemaList;
import graphql.schema.SchemaModifiedType;
import graphql.schema.SchemaNamedElement;
import graphql.schema.SchemaNamedType;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaObject;
import graphql.schema.SchemaScalar;
import graphql.schema.SchemaType;
import graphql.schema.SchemaUnion;
import graphql.util.FpKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.util.EscapeUtil.escapeJsonString;

@Internal
@NullMarked
public final class SchemaPrinterWriter {

    private final ExecutableSchema schema;
    private final SchemaPrinter.Options options;

    public SchemaPrinterWriter(
            ExecutableSchema schema,
            SchemaPrinter.Options options) {
        this.schema = assertNotNull(schema);
        this.options = assertNotNull(options);
    }

    public String print() {
        StringWriter stringWriter = new StringWriter();
        PrintWriter out = new PrintWriter(stringWriter);
        printSchema(out);

        List<SchemaNamedElement> elements = new ArrayList<>();
        elements.addAll(schema.getTypes());
        elements.addAll(schema.getDirectives());
        elements.removeIf(element -> !isIncluded(element));
        elements = sort(null, elements, null, true);
        for (SchemaNamedElement element : elements) {
            printElement(out, element);
        }
        return trimNewLineChars(stringWriter.toString());
    }

    private void printSchema(PrintWriter out) {
        SchemaObject queryType = schema.getQueryType();
        SchemaObject mutationType = schema.getMutationType();
        SchemaObject subscriptionType = schema.getSubscriptionType();
        boolean needsSchemaPrinted = options.isIncludeSchemaDefinition()
                || !"Query".equals(queryType.getName());
        if (mutationType != null
                && !"Mutation".equals(mutationType.getName())) {
            needsSchemaPrinted = true;
        }
        if (subscriptionType != null
                && !"Subscription".equals(subscriptionType.getName())) {
            needsSchemaPrinted = true;
        }
        if (!needsSchemaPrinted) {
            return;
        }

        printComments(
                out,
                schema.getDescription(),
                schema.getDefinition(),
                "");
        out.format(
                "schema %s{\n",
                directivesString(null, schema.getAppliedDirectives(), true));
        out.format("  query: %s\n", queryType.getName());
        if (mutationType != null) {
            out.format("  mutation: %s\n", mutationType.getName());
        }
        if (subscriptionType != null) {
            out.format("  subscription: %s\n", subscriptionType.getName());
        }
        out.format("}\n\n");
    }

    private void printElement(
            PrintWriter out,
            SchemaNamedElement element) {
        if (element instanceof SchemaObject) {
            printObject(out, (SchemaObject) element);
            return;
        }
        if (element instanceof SchemaInterface) {
            printInterface(out, (SchemaInterface) element);
            return;
        }
        if (element instanceof SchemaUnion) {
            printUnion(out, (SchemaUnion) element);
            return;
        }
        if (element instanceof SchemaEnum) {
            printEnum(out, (SchemaEnum) element);
            return;
        }
        if (element instanceof SchemaScalar) {
            printScalar(out, (SchemaScalar) element);
            return;
        }
        if (element instanceof SchemaInputObject) {
            printInputObject(out, (SchemaInputObject) element);
            return;
        }
        if (element instanceof SchemaDirective) {
            printDirectiveDefinition(out, (SchemaDirective) element);
            return;
        }
        assertShouldNeverHappen(
                "Unsupported top-level schema print element %s",
                element);
    }

    private void printObject(PrintWriter out, SchemaObject type) {
        if (isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format("type %s", type.getName());
        printInterfaces(out, type);
        out.print(directivesString(
                type,
                schema.getAppliedDirectives(type),
                false));
        printFields(out, type);
        out.format("\n\n");
    }

    private void printInterface(
            PrintWriter out,
            SchemaInterface type) {
        if (isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format("interface %s", type.getName());
        printInterfaces(out, type);
        out.print(directivesString(
                type,
                schema.getAppliedDirectives(type),
                false));
        printFields(out, type);
        out.format("\n\n");
    }

    private void printInterfaces(
            PrintWriter out,
            SchemaFieldsContainer type) {
        List<SchemaInterface> interfaces = sort(
                type,
                schema.getInterfaces(type),
                GraphQLOutputType.class,
                false);
        if (interfaces.isEmpty()) {
            return;
        }
        String names = interfaces.stream()
                .map(SchemaInterface::getName)
                .collect(Collectors.joining(" & "));
        out.format(" implements %s", names);
    }

    private void printFields(
            PrintWriter out,
            SchemaFieldsContainer type) {
        List<SchemaField> fields = sort(
                type,
                schema.getFields(type),
                GraphQLFieldDefinition.class,
                false);
        fields.removeIf(field -> !isIncluded(field));
        if (fields.isEmpty()) {
            return;
        }
        out.format(" {\n");
        for (SchemaField field : fields) {
            printComments(out, field, "  ");
            out.format(
                    "  %s%s: %s%s\n",
                    field.getName(),
                    argsString(field),
                    typeString(field.getType()),
                    directivesString(
                            field,
                            schema.getAppliedDirectives(field),
                            false));
        }
        out.format("}");
    }

    private void printUnion(PrintWriter out, SchemaUnion type) {
        if (isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format(
                "union %s%s = ",
                type.getName(),
                directivesString(
                        type,
                        schema.getAppliedDirectives(type),
                        false));
        List<SchemaObject> members = sort(
                type,
                schema.getUnionMembers(type),
                GraphQLOutputType.class,
                false);
        out.print(members.stream()
                .map(SchemaObject::getName)
                .collect(Collectors.joining(" | ")));
        out.format("\n\n");
    }

    private void printEnum(PrintWriter out, SchemaEnum type) {
        if (isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format(
                "enum %s%s",
                type.getName(),
                directivesString(
                        type,
                        schema.getAppliedDirectives(type),
                        false));
        List<SchemaEnumValue> values = sort(
                type,
                type.getValues(),
                GraphQLEnumValueDefinition.class,
                false);
        if (!values.isEmpty()) {
            out.format(" {\n");
            for (SchemaEnumValue value : values) {
                printComments(out, value, "  ");
                out.format(
                        "  %s%s\n",
                        value.getName(),
                        directivesString(
                                value,
                                schema.getAppliedDirectives(value),
                                false));
            }
            out.format("}");
        }
        out.format("\n\n");
    }

    private void printScalar(PrintWriter out, SchemaScalar type) {
        if (!options.isIncludeScalars()
                || ScalarInfo.isGraphqlSpecifiedScalar(type.getName())) {
            return;
        }
        printComments(out, type, "");
        List<SchemaAppliedDirective> directives =
                new ArrayList<>(schema.getAppliedDirectives(type));
        directives.removeIf(directive ->
                "specifiedBy".equals(directive.getName()));
        out.format(
                "scalar %s%s%s\n\n",
                type.getName(),
                directivesString(type, directives, false),
                specifiedByUrlString(type));
    }

    private String specifiedByUrlString(SchemaScalar scalar) {
        String url = scalar.getSpecifiedByUrl();
        if (url == null
                || !options.getIncludeDirective().test("specifiedBy")) {
            return "";
        }
        return " @specifiedBy(url : \"" + escapeJsonString(url) + "\")";
    }

    private void printInputObject(
            PrintWriter out,
            SchemaInputObject type) {
        if (isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format(
                "input %s%s",
                type.getName(),
                directivesString(
                        type,
                        schema.getAppliedDirectives(type),
                        false));
        List<SchemaInputField> fields = sort(
                type,
                schema.getInputFields(type),
                GraphQLInputObjectField.class,
                false);
        fields.removeIf(field -> !isIncluded(field));
        if (!fields.isEmpty()) {
            out.format(" {\n");
            for (SchemaInputField field : fields) {
                printInputField(out, field);
            }
            out.format("}");
        }
        out.format("\n\n");
    }

    private void printInputField(
            PrintWriter out,
            SchemaInputField field) {
        printComments(out, field, "  ");
        SchemaInputType type = field.getType();
        out.format(
                "  %s: %s",
                field.getName(),
                typeString(type));
        InputValueWithState defaultValue =
                field.getInputFieldDefaultValue();
        if (defaultValue.isSet()) {
            out.format(" = %s", printValue(defaultValue, type));
        }
        out.print(directivesString(
                field,
                schema.getAppliedDirectives(field),
                false));
        out.format("\n");
    }

    private void printDirectiveDefinition(
            PrintWriter out,
            SchemaDirective directive) {
        if (!options.isIncludeDirectiveDefinitions()
                || !options.getIncludeDirective().test(directive.getName())
                || !options.getIncludeDirectiveDefinition()
                .test(directive.getName())) {
            return;
        }
        printComments(out, directive, "");
        out.format(
                "directive @%s%s%s",
                directive.getName(),
                argsString(directive),
                directivesString(
                        directive,
                        schema.getAppliedDirectives(directive),
                        false));
        if (directive.isRepeatable()) {
            out.print(" repeatable");
        }
        String locations = directive.validLocations().stream()
                .map(DirectiveLocation::name)
                .collect(Collectors.joining(" | "));
        out.format(" on %s\n\n", locations);
    }

    private String argsString(SchemaNamedElement parent) {
        List<? extends SchemaArgument> parentArguments =
                getArguments(parent);
        boolean hasComments = parentArguments.stream()
                .anyMatch(this::hasAstDefinitionComments);
        boolean hasDescriptions = parentArguments.stream()
                .anyMatch(this::hasDescription);
        String halfPrefix = hasComments || hasDescriptions ? "  " : "";
        String prefix = hasComments || hasDescriptions ? "    " : "";
        List<SchemaArgument> arguments = sort(
                parent,
                parentArguments,
                GraphQLArgument.class,
                false);
        arguments.removeIf(argument -> !isIncluded(argument));
        if (arguments.isEmpty()) {
            return "";
        }
        return argumentsString(
                arguments,
                halfPrefix,
                prefix,
                hasComments || hasDescriptions);
    }

    private List<? extends SchemaArgument> getArguments(
            SchemaNamedElement parent) {
        if (parent instanceof SchemaField) {
            return ((SchemaField) parent).getArguments();
        }
        return ((SchemaDirective) parent).getArguments();
    }

    private String argumentsString(
            List<SchemaArgument> arguments,
            String halfPrefix,
            String prefix,
            boolean multiline) {
        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < arguments.size(); i++) {
            appendArgumentSeparator(result, i, multiline);
            SchemaArgument argument = arguments.get(i);
            result.append(commentsString(argument, prefix));
            SchemaInputType type = argument.getType();
            result.append(prefix)
                    .append(argument.getName())
                    .append(": ")
                    .append(typeString(type));
            appendDefaultValue(result, argument, type);
            result.append(directivesString(
                    argument,
                    schema.getAppliedDirectives(argument),
                    false));
        }
        if (multiline) {
            result.append("\n");
        }
        return result.append(halfPrefix).append(")").toString();
    }

    private void appendArgumentSeparator(
            StringBuilder result,
            int index,
            boolean multiline) {
        if (index > 0) {
            result.append(",");
            if (!multiline) {
                result.append(" ");
            }
        }
        if (multiline) {
            result.append("\n");
        }
    }

    private void appendDefaultValue(
            StringBuilder result,
            SchemaArgument argument,
            SchemaInputType type) {
        InputValueWithState defaultValue =
                argument.getArgumentDefaultValue();
        if (!defaultValue.isSet()) {
            return;
        }
        result.append(" = ").append(printValue(defaultValue, type));
    }

    private String directivesString(
            @Nullable SchemaNamedElement parent,
            List<? extends SchemaAppliedDirective> directives,
            boolean schemaContainer) {
        List<SchemaAppliedDirective> included = directives.stream()
                .filter(this::isIncluded)
                .filter(directive -> options.getIncludeDirective()
                        .test(directive.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (included.isEmpty()) {
            return "";
        }
        included = sort(
                parent,
                included,
                GraphQLAppliedDirective.class,
                false);
        String result = included.stream()
                .map(this::directiveString)
                .collect(Collectors.joining(" "));
        return schemaContainer ? result : " " + result;
    }

    private String directiveString(SchemaAppliedDirective directive) {
        StringBuilder result = new StringBuilder("@")
                .append(directive.getName());
        List<SchemaAppliedDirectiveArgument> arguments =
                directive.getArguments().stream()
                        .filter(argument -> argument.getArgumentValue().isSet())
                        .collect(Collectors.toCollection(ArrayList::new));
        arguments = sort(
                directive,
                arguments,
                GraphQLAppliedDirectiveArgument.class,
                false);
        if (arguments.isEmpty()) {
            return result.toString();
        }
        result.append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            SchemaAppliedDirectiveArgument argument = arguments.get(i);
            result.append(argument.getName())
                    .append(" : ")
                    .append(printValue(
                            argument.getArgumentValue(),
                            argument.getType()));
        }
        return result.append(")").toString();
    }

    private String typeString(SchemaType type) {
        if (type instanceof SchemaList) {
            return "[" + typeString(
                    ((SchemaList) type).getWrappedType()) + "]";
        }
        if (type instanceof SchemaNonNull) {
            return typeString(
                    ((SchemaNonNull) type).getWrappedType()) + "!";
        }
        return ((SchemaNamedType) type).getName();
    }

    private String printValue(
            InputValueWithState value,
            SchemaInputType type) {
        if (type instanceof GraphQLType) {
            return AstPrinter.printAst(
                    graphql.execution.ValuesResolver.valueToLiteral(
                            value,
                            (GraphQLType) type,
                            GraphQLContext.getDefault(),
                            Locale.getDefault()));
        }
        if (value.isLiteral()) {
            return AstPrinter.printAst(
                    (Value<?>) assertNotNull(value.getValue()));
        }
        Value<?> literal = valueToLiteral(
                value.getValue(),
                type,
                value.isInternal());
        return AstPrinter.printAst(literal);
    }

    private Value<?> valueToLiteral(
            @Nullable Object value,
            SchemaInputType type,
            boolean internal) {
        SchemaInputType unwrappedType = unwrapNonNull(type);
        if (value == null) {
            return NullValue.of();
        }
        if (unwrappedType instanceof SchemaList) {
            return listLiteral(
                    value,
                    (SchemaList) unwrappedType,
                    internal);
        }
        if (unwrappedType instanceof SchemaInputObject) {
            return objectLiteral(
                    value,
                    (SchemaInputObject) unwrappedType,
                    internal);
        }
        if (unwrappedType instanceof SchemaEnum) {
            return EnumValue.newEnumValue(String.valueOf(value)).build();
        }
        return scalarLiteral(
                value,
                (SchemaScalar) unwrappedType,
                internal);
    }

    private SchemaInputType unwrapNonNull(SchemaInputType type) {
        if (!(type instanceof SchemaNonNull)) {
            return type;
        }
        return (SchemaInputType)
                ((SchemaModifiedType) type).getWrappedType();
    }

    private Value<?> listLiteral(
            Object value,
            SchemaList type,
            boolean internal) {
        SchemaInputType wrappedType =
                (SchemaInputType) type.getWrappedType();
        List<Value> values = new ArrayList<>();
        for (Object item : FpKit.toListOrSingletonList(value)) {
            values.add(valueToLiteral(item, wrappedType, internal));
        }
        return ArrayValue.newArrayValue().values(values).build();
    }

    private Value<?> objectLiteral(
            Object value,
            SchemaInputObject type,
            boolean internal) {
        if (!(value instanceof Map<?, ?>)) {
            return assertShouldNeverHappen(
                    "Cannot print value '%s' for input object '%s' "
                            + "without a map",
                    value,
                    type.getName());
        }
        Map<?, ?> map = (Map<?, ?>) value;
        List<ObjectField> fields = new ArrayList<>();
        for (SchemaInputField field : schema.getInputFields(type)) {
            if (!map.containsKey(field.getName())) {
                continue;
            }
            fields.add(ObjectField.newObjectField()
                    .name(field.getName())
                    .value(valueToLiteral(
                            map.get(field.getName()),
                            field.getType(),
                            internal))
                    .build());
        }
        return ObjectValue.newObjectValue().objectFields(fields).build();
    }

    private Value<?> scalarLiteral(
            Object value,
            SchemaScalar type,
            boolean internal) {
        Object externalValue = internal
                ? schema.getScalarCoercing(type).serialize(
                        value,
                        GraphQLContext.getDefault(),
                        Locale.getDefault())
                : value;
        return schema.getScalarCoercing(type).valueToLiteral(
                assertNotNull(externalValue),
                GraphQLContext.getDefault(),
                Locale.getDefault());
    }

    private boolean isIntrospectionType(SchemaNamedType type) {
        return !options.isIncludeIntrospectionTypes()
                && type.getName().startsWith("__");
    }

    private boolean isIncluded(SchemaNamedElement element) {
        if (!(element instanceof GraphQLSchemaElement)) {
            return true;
        }
        return options.getIncludeSchemaElement()
                .test((GraphQLSchemaElement) element);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T extends SchemaNamedElement> List<T> sort(
            @Nullable SchemaNamedElement parent,
            List<? extends T> elements,
            @Nullable Class<? extends GraphQLSchemaElement> elementType,
            boolean topLevel) {
        List<T> result = new ArrayList<>(elements);
        if (result.size() < 2) {
            return result;
        }
        if (result.get(0) instanceof GraphQLSchemaElement) {
            Class<? extends GraphQLSchemaElement> parentType =
                    graphQLParentClass(parent);
            GraphqlTypeComparatorEnvironment environment =
                    GraphqlTypeComparatorEnvironment.newEnvironment()
                            .parentType(parentType)
                            .elementType(elementType)
                            .build();
            Comparator comparator = options.getComparatorRegistry()
                    .getComparator(environment);
            result.sort(comparator);
            return result;
        }
        if (options.getComparatorRegistry()
                == GraphqlTypeComparatorRegistry.AS_IS_REGISTRY) {
            return result;
        }
        Comparator<T> comparator =
                Comparator.comparing(SchemaNamedElement::getName);
        if (topLevel
                && options.getComparatorRegistry()
                != GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY) {
            comparator = Comparator
                    .comparingInt((T element) -> topLevelRank(element))
                    .thenComparing(SchemaNamedElement::getName);
        }
        result.sort(comparator);
        return result;
    }

    private Class<? extends GraphQLSchemaElement> graphQLParentClass(
            @Nullable SchemaNamedElement parent) {
        if (parent == null) {
            return GraphQLSchemaElement.class;
        }
        if (parent instanceof SchemaObject) {
            return GraphQLObjectType.class;
        }
        if (parent instanceof SchemaInterface) {
            return GraphQLInterfaceType.class;
        }
        if (parent instanceof SchemaUnion) {
            return GraphQLUnionType.class;
        }
        if (parent instanceof SchemaEnum) {
            return graphql.schema.GraphQLEnumType.class;
        }
        if (parent instanceof SchemaInputObject) {
            return graphql.schema.GraphQLInputObjectType.class;
        }
        if (parent instanceof SchemaField) {
            return GraphQLFieldDefinition.class;
        }
        if (parent instanceof SchemaDirective) {
            return GraphQLDirective.class;
        }
        if (parent instanceof SchemaAppliedDirective) {
            return GraphQLAppliedDirective.class;
        }
        return GraphQLSchemaElement.class;
    }

    private int topLevelRank(SchemaNamedElement element) {
        if (element instanceof SchemaDirective) {
            return 1;
        }
        if (element instanceof SchemaInterface) {
            return 2;
        }
        if (element instanceof SchemaUnion) {
            return 3;
        }
        if (element instanceof SchemaObject) {
            return 4;
        }
        if (element instanceof SchemaEnum) {
            return 5;
        }
        if (element instanceof SchemaScalar) {
            return 6;
        }
        if (element instanceof SchemaInputObject) {
            return 7;
        }
        return 0;
    }

    private void printComments(
            PrintWriter out,
            SchemaNamedElement element,
            String prefix) {
        out.print(commentsString(element, prefix));
    }

    private String commentsString(
            SchemaNamedElement element,
            String prefix) {
        return commentsString(
                description(element),
                element.getDefinition(),
                prefix);
    }

    private void printComments(
            PrintWriter out,
            @Nullable String description,
            @Nullable Node<?> definition,
            String prefix) {
        out.print(commentsString(description, definition, prefix));
    }

    private String commentsString(
            @Nullable String description,
            @Nullable Node<?> definition,
            String prefix) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter out = new PrintWriter(stringWriter);
        if (description != null && !description.isEmpty()) {
            printDescription(out, prefix, description);
        }
        if (options.isIncludeAstDefinitionComments()) {
            printAstDefinitionComments(out, definition, prefix);
        }
        return stringWriter.toString();
    }

    private @Nullable String description(SchemaNamedElement element) {
        String description = element.getDescription();
        if (description != null && !description.isEmpty()) {
            return description;
        }
        Node<?> definition = element.getDefinition();
        if (!(definition instanceof DescribedNode<?>)) {
            return description;
        }
        graphql.language.Description astDescription =
                ((DescribedNode<?>) definition).getDescription();
        return astDescription == null
                ? description
                : astDescription.getContent();
    }

    private void printAstDefinitionComments(
            PrintWriter out,
            @Nullable Node<?> definition,
            String prefix) {
        if (definition == null || definition.getComments().isEmpty()) {
            return;
        }
        List<String> comments = definition.getComments().stream()
                .map(Comment::getContent)
                .collect(Collectors.toList());
        printHashDescription(out, prefix, comments);
    }

    private void printDescription(
            PrintWriter out,
            String prefix,
            String description) {
        List<String> lines = Arrays.asList(description.split("\n"));
        if (lines.isEmpty()) {
            return;
        }
        if (options.isDescriptionsAsHashComments()) {
            printHashDescription(out, prefix, lines);
            return;
        }
        if (lines.size() > 1) {
            out.printf("%s\"\"\"\n", prefix);
            for (String line : lines) {
                out.printf(
                        "%s%s\n",
                        prefix,
                        line.replaceAll("\"\"\"", "\\\\\"\"\""));
            }
            out.printf("%s\"\"\"\n", prefix);
            return;
        }
        out.printf(
                "%s\"%s\"\n",
                prefix,
                escapeJsonString(lines.get(0)));
    }

    private void printHashDescription(
            PrintWriter out,
            String prefix,
            List<String> lines) {
        for (String line : lines) {
            out.printf("%s#%s\n", prefix, line);
        }
    }

    private boolean hasDescription(SchemaNamedElement element) {
        String description = description(element);
        return description != null && !description.isEmpty();
    }

    private boolean hasAstDefinitionComments(
            SchemaNamedElement element) {
        if (!options.isIncludeAstDefinitionComments()) {
            return false;
        }
        Node<?> definition = element.getDefinition();
        return definition != null && !definition.getComments().isEmpty();
    }

    private String trimNewLineChars(String value) {
        if (value.endsWith("\n\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
