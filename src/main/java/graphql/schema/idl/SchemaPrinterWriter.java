package graphql.schema.idl;

import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.util.EscapeUtil.escapeJsonString;

@Internal
@NullMarked
public final class SchemaPrinterWriter {

    private final SchemaPrintAccess access;
    private final SchemaPrinter.Options options;

    public SchemaPrinterWriter(
            SchemaPrintAccess access,
            SchemaPrinter.Options options) {
        this.access = assertNotNull(access);
        this.options = assertNotNull(options);
    }

    public String print() {
        StringWriter stringWriter = new StringWriter();
        PrintWriter out = new PrintWriter(stringWriter);
        printSchema(out);

        List<Object> elements = new ArrayList<>();
        elements.addAll(access.getTypes());
        elements.addAll(access.getDirectiveDefinitions());
        elements.removeIf(element -> !access.isIncluded(element));
        elements = access.sort(null, SchemaPrintChildKind.TOP_LEVEL, elements);
        for (Object element : elements) {
            printElement(out, element);
        }
        return trimNewLineChars(stringWriter.toString());
    }

    private void printSchema(PrintWriter out) {
        Object queryType = access.getQueryType();
        Object mutationType = access.getMutationType();
        Object subscriptionType = access.getSubscriptionType();
        boolean needsSchemaPrinted = options.isIncludeSchemaDefinition()
                || !"Query".equals(access.getName(queryType));
        if (mutationType != null
                && !"Mutation".equals(access.getName(mutationType))) {
            needsSchemaPrinted = true;
        }
        if (subscriptionType != null
                && !"Subscription".equals(access.getName(subscriptionType))) {
            needsSchemaPrinted = true;
        }
        if (!needsSchemaPrinted) {
            return;
        }

        Object schema = access.getSchema();
        printComments(out, schema, "");
        out.format(
                "schema %s{\n",
                directivesString(schema, access.getAppliedDirectives(schema), true));
        out.format("  query: %s\n", access.getName(queryType));
        if (mutationType != null) {
            out.format("  mutation: %s\n", access.getName(mutationType));
        }
        if (subscriptionType != null) {
            out.format("  subscription: %s\n", access.getName(subscriptionType));
        }
        out.format("}\n\n");
    }

    private void printElement(PrintWriter out, Object element) {
        switch (access.getKind(element)) {
            case OBJECT:
                printObject(out, element);
                return;
            case INTERFACE:
                printInterface(out, element);
                return;
            case UNION:
                printUnion(out, element);
                return;
            case ENUM:
                printEnum(out, element);
                return;
            case SCALAR:
                printScalar(out, element);
                return;
            case INPUT_OBJECT:
                printInputObject(out, element);
                return;
            case DIRECTIVE:
                printDirectiveDefinition(out, element);
                return;
            default:
                assertShouldNeverHappen(
                        "Unsupported top-level schema print element %s",
                        element);
        }
    }

    private void printObject(PrintWriter out, Object type) {
        if (access.isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format("type %s", access.getName(type));
        printInterfaces(out, type);
        out.print(directivesString(
                type,
                access.getAppliedDirectives(type),
                false));
        printFields(out, type);
        out.format("\n\n");
    }

    private void printInterface(PrintWriter out, Object type) {
        if (access.isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format("interface %s", access.getName(type));
        printInterfaces(out, type);
        out.print(directivesString(
                type,
                access.getAppliedDirectives(type),
                false));
        printFields(out, type);
        out.format("\n\n");
    }

    private void printInterfaces(PrintWriter out, Object type) {
        List<Object> interfaces = access.sort(
                type,
                SchemaPrintChildKind.INTERFACE,
                access.getInterfaces(type));
        if (interfaces.isEmpty()) {
            return;
        }
        String names = interfaces.stream()
                .map(access::getName)
                .collect(Collectors.joining(" & "));
        out.format(" implements %s", names);
    }

    private void printFields(PrintWriter out, Object type) {
        List<Object> fields = access.sort(
                type,
                SchemaPrintChildKind.FIELD,
                access.getFields(type));
        fields.removeIf(field -> !access.isIncluded(field));
        if (fields.isEmpty()) {
            return;
        }
        out.format(" {\n");
        for (Object field : fields) {
            printComments(out, field, "  ");
            out.format(
                    "  %s%s: %s%s\n",
                    access.getName(field),
                    argsString(field),
                    access.getTypeString(access.getType(field)),
                    directivesString(
                            field,
                            access.getAppliedDirectives(field),
                            false));
        }
        out.format("}");
    }

    private void printUnion(PrintWriter out, Object type) {
        if (access.isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format(
                "union %s%s = ",
                access.getName(type),
                directivesString(
                        type,
                        access.getAppliedDirectives(type),
                        false));
        List<Object> members = access.sort(
                type,
                SchemaPrintChildKind.UNION_MEMBER,
                access.getUnionMembers(type));
        out.print(members.stream()
                .map(access::getName)
                .collect(Collectors.joining(" | ")));
        out.format("\n\n");
    }

    private void printEnum(PrintWriter out, Object type) {
        if (access.isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format(
                "enum %s%s",
                access.getName(type),
                directivesString(
                        type,
                        access.getAppliedDirectives(type),
                        false));
        List<Object> values = access.sort(
                type,
                SchemaPrintChildKind.ENUM_VALUE,
                access.getEnumValues(type));
        if (!values.isEmpty()) {
            out.format(" {\n");
            for (Object value : values) {
                printComments(out, value, "  ");
                out.format(
                        "  %s%s\n",
                        access.getName(value),
                        directivesString(
                                value,
                                access.getAppliedDirectives(value),
                                false));
            }
            out.format("}");
        }
        out.format("\n\n");
    }

    private void printScalar(PrintWriter out, Object type) {
        if (!options.isIncludeScalars() || access.isSpecifiedScalar(type)) {
            return;
        }
        printComments(out, type, "");
        List<Object> directives = access.getAppliedDirectives(type).stream()
                .filter(directive -> !"specifiedBy".equals(access.getName(directive)))
                .collect(Collectors.toList());
        out.format(
                "scalar %s%s%s\n\n",
                access.getName(type),
                directivesString(type, directives, false),
                specifiedByUrlString(type));
    }

    private String specifiedByUrlString(Object scalar) {
        String url = access.getSpecifiedByUrl(scalar);
        if (url == null || !options.getIncludeDirective().test("specifiedBy")) {
            return "";
        }
        return " @specifiedBy(url : \"" + escapeJsonString(url) + "\")";
    }

    private void printInputObject(PrintWriter out, Object type) {
        if (access.isIntrospectionType(type)) {
            return;
        }
        printComments(out, type, "");
        out.format(
                "input %s%s",
                access.getName(type),
                directivesString(
                        type,
                        access.getAppliedDirectives(type),
                        false));
        List<Object> fields = access.sort(
                type,
                SchemaPrintChildKind.INPUT_FIELD,
                access.getInputFields(type));
        fields.removeIf(field -> !access.isIncluded(field));
        if (!fields.isEmpty()) {
            out.format(" {\n");
            for (Object field : fields) {
                printInputField(out, field);
            }
            out.format("}");
        }
        out.format("\n\n");
    }

    private void printInputField(PrintWriter out, Object field) {
        printComments(out, field, "  ");
        Object type = access.getType(field);
        out.format(
                "  %s: %s",
                access.getName(field),
                access.getTypeString(type));
        InputValueWithState defaultValue = access.getDefaultValue(field);
        if (defaultValue.isSet()) {
            out.format(" = %s", access.printValue(defaultValue, type));
        }
        out.print(directivesString(
                field,
                access.getAppliedDirectives(field),
                false));
        out.format("\n");
    }

    private void printDirectiveDefinition(PrintWriter out, Object directive) {
        if (!options.isIncludeDirectiveDefinitions()
                || !options.getIncludeDirective().test(access.getName(directive))
                || !options.getIncludeDirectiveDefinition()
                        .test(access.getName(directive))) {
            return;
        }
        printComments(out, directive, "");
        out.format(
                "directive @%s%s",
                access.getName(directive),
                argsString(directive));
        if (access.isRepeatable(directive)) {
            out.print(" repeatable");
        }
        String locations = access.getDirectiveLocations(directive).stream()
                .map(DirectiveLocation::name)
                .collect(Collectors.joining(" | "));
        out.format(" on %s\n\n", locations);
    }

    private String argsString(Object parent) {
        List<Object> arguments = access.getArguments(parent);
        boolean hasComments = arguments.stream()
                .anyMatch(this::hasAstDefinitionComments);
        boolean hasDescriptions = arguments.stream()
                .anyMatch(this::hasDescription);
        String halfPrefix = hasComments || hasDescriptions ? "  " : "";
        String prefix = hasComments || hasDescriptions ? "    " : "";
        arguments = access.sort(
                parent,
                SchemaPrintChildKind.ARGUMENT,
                arguments);
        arguments.removeIf(argument -> !access.isIncluded(argument));
        if (arguments.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < arguments.size(); i++) {
            Object argument = arguments.get(i);
            if (i > 0) {
                result.append(",");
                if (!hasComments && !hasDescriptions) {
                    result.append(" ");
                }
            }
            if (hasComments || hasDescriptions) {
                result.append("\n");
            }
            result.append(commentsString(argument, prefix));
            Object type = access.getType(argument);
            result.append(prefix)
                    .append(access.getName(argument))
                    .append(": ")
                    .append(access.getTypeString(type));
            InputValueWithState defaultValue = access.getDefaultValue(argument);
            if (defaultValue.isSet()) {
                result.append(" = ")
                        .append(access.printValue(defaultValue, type));
            }
            result.append(directivesString(
                    argument,
                    access.getAppliedDirectives(argument),
                    false));
        }
        if (hasComments || hasDescriptions) {
            result.append("\n");
        }
        return result.append(halfPrefix).append(")").toString();
    }

    private String directivesString(
            Object parent,
            List<Object> directives,
            boolean schemaContainer) {
        directives = directives.stream()
                .filter(access::isIncluded)
                .filter(directive -> options.getIncludeDirective()
                        .test(access.getName(directive)))
                .collect(Collectors.toList());
        if (directives.isEmpty()) {
            return "";
        }
        directives = access.sort(
                parent,
                SchemaPrintChildKind.APPLIED_DIRECTIVE,
                directives);
        String result = directives.stream()
                .map(this::directiveString)
                .collect(Collectors.joining(" "));
        return schemaContainer ? result : " " + result;
    }

    private String directiveString(Object directive) {
        StringBuilder result = new StringBuilder("@")
                .append(access.getName(directive));
        List<Object> arguments = access.getAppliedDirectiveArguments(directive)
                .stream()
                .filter(argument -> access.getAppliedDirectiveArgumentValue(argument)
                        .isSet())
                .collect(Collectors.toList());
        arguments = access.sort(
                directive,
                SchemaPrintChildKind.APPLIED_DIRECTIVE_ARGUMENT,
                arguments);
        if (arguments.isEmpty()) {
            return result.toString();
        }
        result.append("(");
        for (int i = 0; i < arguments.size(); i++) {
            Object argument = arguments.get(i);
            if (i > 0) {
                result.append(", ");
            }
            result.append(access.getName(argument))
                    .append(" : ")
                    .append(access.printValue(
                            access.getAppliedDirectiveArgumentValue(argument),
                            access.getType(argument)));
        }
        return result.append(")").toString();
    }

    private void printComments(
            PrintWriter out,
            Object element,
            String prefix) {
        out.print(commentsString(element, prefix));
    }

    private String commentsString(Object element, String prefix) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter out = new PrintWriter(stringWriter);
        String description = access.getDescription(element);
        if (description != null && !description.isEmpty()) {
            printDescription(out, prefix, description);
        }
        if (options.isIncludeAstDefinitionComments()) {
            String comments = access.getAstDefinitionComments(element);
            if (comments != null && !comments.isEmpty()) {
                printHashDescription(
                        out,
                        prefix,
                        Arrays.asList(comments.split("\n")));
            }
        }
        return stringWriter.toString();
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
        out.printf("%s\"%s\"\n", prefix, escapeJsonString(lines.get(0)));
    }

    private void printHashDescription(
            PrintWriter out,
            String prefix,
            List<String> lines) {
        for (String line : lines) {
            out.printf("%s#%s\n", prefix, line);
        }
    }

    private boolean hasDescription(Object element) {
        String description = access.getDescription(element);
        return description != null && !description.isEmpty();
    }

    private boolean hasAstDefinitionComments(Object element) {
        if (!options.isIncludeAstDefinitionComments()) {
            return false;
        }
        String comments = access.getAstDefinitionComments(element);
        return comments != null && !comments.isEmpty();
    }

    private String trimNewLineChars(String value) {
        if (value.endsWith("\n\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
