package graphql.language;

import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.util.EscapeUtil.escapeJsonStringTo;

/**
 * This can take graphql language AST and print it out as a string
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@PublicApi
@NullMarked
public class AstPrinter {

    /**
     * @return an {@link AstPrinter} that is in full print mode
     */
    static AstPrinter full() {
        return new AstPrinter(false);
    }

    /**
     * @return an {@link AstPrinter} that is in compact print mode
     */
    static AstPrinter compact() {
        return new AstPrinter(true);
    }

    private final Map<Class<? extends Node>, NodePrinter<? extends Node>> printers = new LinkedHashMap<>();

    private final boolean compactMode;

    AstPrinter(boolean compactMode) {
        this.compactMode = compactMode;
        printers.put(Argument.class, argument());
        printers.put(ArrayValue.class, value());
        printers.put(BooleanValue.class, value());
        printers.put(NullValue.class, value());
        printers.put(Directive.class, directive());
        printers.put(DirectiveDefinition.class, directiveDefinition());
        printers.put(DirectiveLocation.class, directiveLocation());
        printers.put(Document.class, document());
        printers.put(EnumTypeDefinition.class, enumTypeDefinition());
        printers.put(EnumTypeExtensionDefinition.class, enumTypeExtensionDefinition());
        printers.put(EnumValue.class, enumValue());
        printers.put(EnumValueDefinition.class, enumValueDefinition());
        printers.put(Field.class, field());
        printers.put(FieldDefinition.class, fieldDefinition());
        printers.put(FloatValue.class, value());
        printers.put(FragmentDefinition.class, fragmentDefinition());
        printers.put(FragmentSpread.class, fragmentSpread());
        printers.put(InlineFragment.class, inlineFragment());
        printers.put(InputObjectTypeDefinition.class, inputObjectTypeDefinition());
        printers.put(InputObjectTypeExtensionDefinition.class, inputObjectTypeExtensionDefinition());
        printers.put(InputValueDefinition.class, inputValueDefinition());
        printers.put(InterfaceTypeDefinition.class, interfaceTypeDefinition());
        printers.put(InterfaceTypeExtensionDefinition.class, interfaceTypeExtensionDefinition());
        printers.put(IntValue.class, value());
        printers.put(ListType.class, type());
        printers.put(NonNullType.class, type());
        printers.put(ObjectField.class, objectField());
        printers.put(ObjectTypeDefinition.class, objectTypeDefinition());
        printers.put(ObjectTypeExtensionDefinition.class, objectTypeExtensionDefinition());
        printers.put(ObjectValue.class, value());
        printers.put(OperationDefinition.class, operationDefinition());
        printers.put(OperationTypeDefinition.class, operationTypeDefinition());
        printers.put(ScalarTypeDefinition.class, scalarTypeDefinition());
        printers.put(ScalarTypeExtensionDefinition.class, scalarTypeExtensionDefinition());
        printers.put(SchemaDefinition.class, schemaDefinition());
        printers.put(SchemaExtensionDefinition.class, schemaExtensionDefinition());
        printers.put(SelectionSet.class, selectionSet());
        printers.put(StringValue.class, value());
        printers.put(TypeName.class, type());
        printers.put(UnionTypeDefinition.class, unionTypeDefinition());
        printers.put(UnionTypeExtensionDefinition.class, unionTypeExtensionDefinition());
        printers.put(VariableDefinition.class, variableDefinition());
        printers.put(VariableReference.class, variableReference());
    }

    private NodePrinter<Argument> argument() {
        if (compactMode) {
            return (out, node) -> {
                out.append(node.getName()).append(':');
                value(out, node.getValue());
            };
        }
        return (out, node) -> {
            out.append(node.getName()).append(": ");
            value(out, node.getValue());
        };
    }

    private NodePrinter<Document> document() {
        if (compactMode) {
            return (out, node) -> join(out, node.getDefinitions(), " ");
        }
        return (out, node) -> {
            join(out, node.getDefinitions(), "\n\n");
            out.append('\n');
        };
    }

    private NodePrinter<Directive> directive() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            out.append('@');
            out.append(node.getName());
            if (!isEmpty(node.getArguments())) {
                out.append('(');
                join(out, node.getArguments(), argSep);
                out.append(')');
            }
        };
    }

    private NodePrinter<DirectiveDefinition> directiveDefinition() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            description(out, node);
            out.append("directive @");
            out.append(node.getName());
            if (!isEmpty(node.getInputValueDefinitions())) {
                out.append('(');
                join(out, node.getInputValueDefinitions(), argSep);
                out.append(')');
            }
            out.append(" ");
            if (node.isRepeatable()) {
                out.append("repeatable ");
            }
            out.append("on ");
            join(out, node.getDirectiveLocations(), " | ");
        };
    }

    private NodePrinter<DirectiveLocation> directiveLocation() {
        return (out, node) -> out.append(node.getName());
    }

    private NodePrinter<EnumTypeDefinition> enumTypeDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append("enum ");
            out.append(node.getName());
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
            out.append(' ');
            block(out, node.getEnumValueDefinitions());
        };
    }

    private NodePrinter<EnumValue> enumValue() {
        return (out, node) -> out.append(node.getName());
    }

    private NodePrinter<EnumValueDefinition> enumValueDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append(node.getName());
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
        };
    }

    private NodePrinter<Field> field() {
        final String argSep = compactMode ? "," : ", ";
        final String aliasSuffix = compactMode ? ":" : ": ";
        return (out, node) -> {
            String name = node.getName();
                if (!isEmpty(node.getAlias())) {
                    out.append(node.getAlias());
                    out.append(aliasSuffix);
                }
                out.append(name);
                if (!isEmpty(node.getArguments())) {
                    out.append('(');
                    join(out, node.getArguments(), argSep);
                    out.append(')');
                }
                if (!isEmpty(node.getDirectives())) {
                    out.append(' ');
                    directives(out, node.getDirectives());
                }
                if (node.getSelectionSet() != null && !isEmpty(node.getSelectionSet().getSelections())) {
                    if (!compactMode) {
                        out.append(' ');
                    }
                    node(out, node.getSelectionSet());
                }
        };
    }

    private NodePrinter<FieldDefinition> fieldDefinition() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            if (hasDescription(node) && !compactMode) {
                description(out, node);
                out.append(node.getName());
                if (!isEmpty(node.getInputValueDefinitions())) {
                    out.append("(\n");
                    join(out, node.getInputValueDefinitions(), "\n");
                    out.append(')');
                }
                out.append(": ");
                type(out, node.getType());
                if (!isEmpty(node.getDirectives())) {
                    out.append(' ');
                    directives(out, node.getDirectives());
                }
            } else {
                out.append(node.getName());
                if (!isEmpty(node.getInputValueDefinitions())) {
                    out.append('(');
                    join(out, node.getInputValueDefinitions(), argSep);
                    out.append(')');
                }
                out.append(": ");
                type(out, node.getType());
                if (!isEmpty(node.getDirectives())) {
                    out.append(' ');
                    directives(out, node.getDirectives());
                }
            }
        };
    }

    private static boolean hasDescription(Node<?> node) {
        if (node instanceof AbstractDescribedNode) {
            AbstractDescribedNode<?> describedNode = (AbstractDescribedNode<?>) node;
            return describedNode.getDescription() != null;
        }
        return false;
    }

    private NodePrinter<FragmentDefinition> fragmentDefinition() {
        return (out, node) -> {
            out.append("fragment ");
            out.append(node.getName());
            out.append(" on ");
            type(out, node.getTypeCondition());
            out.append(' ');
            directives(out, node.getDirectives());
            node(out, node.getSelectionSet());
        };
    }

    private NodePrinter<FragmentSpread> fragmentSpread() {
        return (out, node) -> {
            out.append("...");
            out.append(node.getName());
            directives(out, node.getDirectives());
        };
    }

    private NodePrinter<InlineFragment> inlineFragment() {
        return (out, node) -> {
            out.append("...");
            if (compactMode) {
                // believe it or not but "...on Foo" is valid syntax
                if (node.getTypeCondition() != null) {
                    out.append("on ");
                    type(out, node.getTypeCondition());
                }
                directives(out, node.getDirectives());
                node(out, node.getSelectionSet());
            } else {
                if (node.getTypeCondition() != null) {
                    out.append(" on ");
                    type(out, node.getTypeCondition());
                }
                if (!isEmpty(node.getDirectives())) {
                    out.append(' ');
                    directives(out, node.getDirectives());
                }
                out.append(' ');
                node(out, node.getSelectionSet());
            }
        };
    }

    private NodePrinter<InputObjectTypeDefinition> inputObjectTypeDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append("input ");
            out.append(node.getName());
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
            if (!isEmpty(node.getInputValueDefinitions())) {
                out.append(' ');
                block(out, node.getInputValueDefinitions());
            }
        };
    }

    private NodePrinter<InputValueDefinition> inputValueDefinition() {
        String nameTypeSep = compactMode ? ":" : ": ";
        String defaultValueEquals = compactMode ? "=" : "= ";
        return (out, node) -> {
            Value<?> defaultValue = node.getDefaultValue();
            description(out, node);
            out.append(node.getName());
            out.append(nameTypeSep);
            type(out, node.getType());
            if (defaultValue != null) {
                out.append(' ');
                out.append(defaultValueEquals);
                node(out, defaultValue);
            }
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
        };
    }

    private NodePrinter<InterfaceTypeDefinition> interfaceTypeDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append("interface ");
            out.append(node.getName());
            if (!isEmpty(node.getImplements())) {
                out.append(" implements ");
                join(out, node.getImplements(), " & ");
            }
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
            if (!isEmpty(node.getFieldDefinitions())) {
                out.append(' ');
                block(out, node.getFieldDefinitions());
            }
        };
    }

    private NodePrinter<ObjectField> objectField() {
        String nameValueSep = compactMode ? ":" : " : ";
        return (out, node) -> {
            out.append(node.getName());
            out.append(nameValueSep);
            value(out, node.getValue());
        };
    }

    private NodePrinter<OperationDefinition> operationDefinition() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            String name = node.getName();
            // Anonymous queries with no directives or variable definitions can use
            // the query short form.
            if (isEmpty(name) && isEmpty(node.getDirectives()) && isEmpty(node.getVariableDefinitions())
                    && node.getOperation() == OperationDefinition.Operation.QUERY) {
                node(out, node.getSelectionSet());
            } else {
                out.append(node.getOperation().toString().toLowerCase());
                if (!isEmpty(name)) {
                    out.append(' ');
                    out.append(name);
                }
                if (!isEmpty(node.getVariableDefinitions())) {
                    if (isEmpty(name)) {
                        out.append(' ');
                    }
                    out.append('(');
                    join(out, node.getVariableDefinitions(), argSep);
                    out.append(')');
                }
                if (!isEmpty(node.getDirectives())) {
                    out.append(' ');
                    directives(out, node.getDirectives());
                }
                if (!compactMode) {
                    out.append(' ');
                }
                node(out, node.getSelectionSet());
            }
        };
    }

    private NodePrinter<OperationTypeDefinition> operationTypeDefinition() {
        String nameTypeSep = compactMode ? ":" : ": ";
        return (out, node) -> {
            out.append(node.getName());
            out.append(nameTypeSep);
            type(out, node.getTypeName());
        };
    }

    private NodePrinter<ObjectTypeDefinition> objectTypeDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append("type ");
            out.append(node.getName());
            if (!isEmpty(node.getImplements())) {
                out.append(" implements ");
                join(out, node.getImplements(), " & ");
            }
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
            if (!isEmpty(node.getFieldDefinitions())) {
                out.append(' ');
                block(out, node.getFieldDefinitions());
            }
        };
    }

    private NodePrinter<SelectionSet> selectionSet() {
        return (out, node) -> block(out, node.getSelections());
    }

    private NodePrinter<ScalarTypeDefinition> scalarTypeDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append("scalar ");
            out.append(node.getName());
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
        };
    }


    private NodePrinter<SchemaDefinition> schemaDefinition() {
        return (out, node) -> {
            description(out, node);
            out.append("schema ");
            if (!isEmpty(node.getDirectives())) {
                directives(out, node.getDirectives());
                out.append(' ');
            }
            block(out, node.getOperationTypeDefinitions());
        };
    }


    private NodePrinter<Type<?>> type() {
        return this::type;
    }

    private void type(StringBuilder out, Type<?> type) {
        if (type instanceof NonNullType) {
            NonNullType inner = (NonNullType) type;
            type(out, inner.getType());
            out.append('!');
        } else if (type instanceof ListType) {
            ListType inner = (ListType) type;
            out.append('[');
            type(out, inner.getType());
            out.append(']');
        } else {
            TypeName inner = (TypeName) type;
            out.append(inner.getName());
        }
    }

    private NodePrinter<ObjectTypeExtensionDefinition> objectTypeExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, ObjectTypeDefinition.class);
        };
    }

    private NodePrinter<EnumTypeExtensionDefinition> enumTypeExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, EnumTypeDefinition.class);
        };
    }

    private NodePrinter<InterfaceTypeDefinition> interfaceTypeExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, InterfaceTypeDefinition.class);
        };
    }

    private NodePrinter<UnionTypeExtensionDefinition> unionTypeExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, UnionTypeDefinition.class);
        };
    }

    private NodePrinter<ScalarTypeExtensionDefinition> scalarTypeExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, ScalarTypeDefinition.class);
        };
    }

    private NodePrinter<InputObjectTypeExtensionDefinition> inputObjectTypeExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, InputObjectTypeDefinition.class);
        };
    }

    private NodePrinter<SchemaExtensionDefinition> schemaExtensionDefinition() {
        return (out, node) -> {
            out.append("extend ");
            node(out, node, SchemaDefinition.class);
        };
    }

    private NodePrinter<UnionTypeDefinition> unionTypeDefinition() {
        String barSep = compactMode ? "|" : " | ";
        String equals = compactMode ? "=" : "= ";
        return (out, node) -> {
            description(out, node);
            out.append("union ");
            out.append(node.getName());
            if (!isEmpty(node.getDirectives())) {
                out.append(' ');
                directives(out, node.getDirectives());
            }
            out.append(' ');
            out.append(equals);
            join(out, node.getMemberTypes(), barSep);
        };
    }

    private NodePrinter<VariableDefinition> variableDefinition() {
        String nameTypeSep = compactMode ? ":" : ": ";
        String defaultValueEquals = compactMode ? "=" : " = ";
        return (out, node) -> {
            out.append('$');
            out.append(node.getName());
            out.append(nameTypeSep);
            type(out, node.getType());
            if (node.getDefaultValue() != null) {
                out.append(defaultValueEquals);
                node(out, node.getDefaultValue());
            }
            directives(out, node.getDirectives());
        };
    }

    private NodePrinter<VariableReference> variableReference() {
        return (out, node) -> out.append('$').append(node.getName());
    }

    private String node(Node<?> node) {
        return node(node, null);
    }

    private void node(StringBuilder out, Node<?> node) {
        node(out, node, null);
    }

    private String node(Node<?> node, @Nullable Class<?> startClass) {
        StringBuilder builder = new StringBuilder();
        node(builder, node, startClass);
        return builder.toString();
    }

    private void node(StringBuilder out, Node<?> node, @Nullable Class<?> startClass) {
        if (startClass != null) {
            assertTrue(startClass.isInstance(node), "The starting class must be in the inherit tree");
        }
        NodePrinter<Node<?>> printer = _findPrinter(node, startClass);
        printer.print(out, node);
    }

    @SuppressWarnings("unchecked")
    <T extends Node> NodePrinter<T> _findPrinter(Node node) {
        return _findPrinter(node, null);
    }

    <T extends Node> NodePrinter<T> _findPrinter(Node node, @Nullable Class<?> startClass) {
        Class<?> clazz = startClass != null ? startClass : node.getClass();
        while (clazz != Object.class) {
            NodePrinter nodePrinter = printers.get(clazz);
            if (nodePrinter != null) {
                // noinspection unchecked
                return nodePrinter;
            }
            clazz = clazz.getSuperclass();
        }
        return assertShouldNeverHappen("We have a missing printer implementation for %s : report a bug!", clazz);
    }

    private static <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private static <T> List<T> nvl(List<T> list) {
        return list != null ? list : ImmutableKit.emptyList();
    }

    private NodePrinter<Value<?>> value() {
        return this::value;
    }

    private void value(StringBuilder out, Value<?> value) {
        String argSep = compactMode ? "," : ", ";
        if (value instanceof IntValue) {
            out.append(((IntValue) value).getValue());
        } else if (value instanceof FloatValue) {
            out.append(((FloatValue) value).getValue());
        } else if (value instanceof StringValue) {
            out.append('"');
            escapeJsonStringTo(out, ((StringValue) value).getValue());
            out.append('"');
        } else if (value instanceof EnumValue) {
            out.append(((EnumValue) value).getName());
        } else if (value instanceof BooleanValue) {
            out.append(((BooleanValue) value).isValue());
        } else if (value instanceof NullValue) {
            out.append("null");
        } else if (value instanceof ArrayValue) {
            out.append('[');
            join(out, ((ArrayValue) value).getValues(), argSep);
            out.append(']');
        } else if (value instanceof ObjectValue) {
            out.append('{');
            join(out, ((ObjectValue) value).getObjectFields(), argSep);
            out.append('}');
        } else if (value instanceof VariableReference) {
            out.append('$');
            out.append(((VariableReference) value).getName());
        }
    }

    private void description(StringBuilder out, Node<?> node) {
        Description description = ((AbstractDescribedNode<?>) node).getDescription();
        if (description == null || description.getContent() == null || compactMode) {
            return;
        }
;
        if (description.isMultiLine()) {
            out.append("\"\"\"");
            if (description.getContent().isEmpty() || description.getContent().charAt(0) != '\n') {
                out.append('\n');
            }
            out.append(description.getContent());
            out.append("\n\"\"\"\n");
        } else {
            out.append('"');
            escapeJsonStringTo(out, description.getContent());
            out.append("\"\n");
        }
    }

    private void directives(StringBuilder out, List<Directive> directives) {
        join(out, nvl(directives), compactMode ? "" : " ");
    }

    private <T extends Node<?>> void join(StringBuilder out, List<T> nodes, String delim) {
        if (isEmpty(nodes)) {
            return;
        }
        Iterator<T> iterator = nodes.iterator();
        node(out, iterator.next());
        while (iterator.hasNext()) {
            out.append(delim);
            node(out, iterator.next());
        }
    }

    /*
     * Some joined nodes don't need delimiters between them and some do
     * This encodes that knowledge of those that don't require delimiters
     */
    @SuppressWarnings("SameParameterValue")
    private <T extends Node<?>> void joinTight(StringBuilder output, List<T> nodes, String delim, String prefix, String suffix) {
        output.append(prefix);

        boolean first = true;
        for (T node : nodes) {
            if (first) {
                first = false;
            } else {
                if (output.charAt(output.length() - 1) != '}') {
                    output.append(delim);
                }
            }
            node(output, node);
        }

        output.append(suffix);
    }

    String wrap(String start, String maybeString, String end) {
        if (isEmpty(maybeString)) {
            if (start.equals("\"") && end.equals("\"")) {
                return "\"\"";
            }
            return "";
        }
        return start + maybeString + (!isEmpty(end) ? end : "");
    }

    private <T extends Node<?>> void block(StringBuilder out, List<T> nodes) {
        if (isEmpty(nodes)) {
            return;
        }
        if (compactMode) {
            out.append('{');
            joinTight(out, nodes, " ", "", "");
            out.append('}');
        } else {
            int offset = out.length();
            out.append("{\n");
            join(out, nodes, "\n");
            indent(out, offset);
            out.append("\n}");
        }
    }

    private static void indent(StringBuilder maybeString, int offset) {
        for (int i = offset; i < maybeString.length(); i++) {
            char c = maybeString.charAt(i);
            if (c == '\n') {
                maybeString.replace(i, i + 1, "\n  ");
                i += 3;
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    String wrap(String start, Node maybeNode, String end) {
        if (maybeNode == null) {
            return "";
        }
        return start + node(maybeNode) + (isEmpty(end) ? "" : end);
    }

    /**
     * This will pretty print the AST node in graphql language format
     *
     * @param node the AST node to print
     *
     * @return the printed node in graphql language format
     */
    public static String printAst(Node node) {
        StringBuilder builder = new StringBuilder();
        printAstTo(node, builder);
        return builder.toString();
    }

    /**
     * This will pretty print the AST node in graphql language format to the given Appendable
     *
     * @param node       the AST node to print
     * @param appendable the Appendable to write the output to
     *
     */
    public static void printAstTo(Node<?> node, Appendable appendable) {
        if (appendable instanceof StringBuilder) {
            printImpl((StringBuilder) appendable, node, false);
        } else if (appendable instanceof Writer) {
            printAst((Writer) appendable, node);
        } else {
            StringBuilder builder = new StringBuilder();
            printImpl(builder, node, false);
            try {
                appendable.append(builder);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * This will pretty print the AST node in graphql language format
     *
     * @param writer the place to put the output
     * @param node   the AST node to print
     */
    public static void printAst(Writer writer, Node node) {
        String ast = printAst(node);
        try {
            writer.write(ast);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * This will print the Ast node in graphql language format in a compact manner, with no new lines
     * and descriptions stripped out of the text.
     *
     * @param node the AST node to print
     *
     * @return the printed node in a compact graphql language format
     */
    public static String printAstCompact(Node node) {
        StringBuilder builder = new StringBuilder();
        printImpl(builder, node, true);
        return builder.toString();
    }

    private static void printImpl(StringBuilder writer, Node<?> node, boolean compactMode) {
        AstPrinter astPrinter = compactMode ? compact() : full();
        NodePrinter<Node> printer = astPrinter._findPrinter(node);
        printer.print(writer, node);
    }

    /**
     * These print nodes into output writers
     *
     * @param <T> the type of node
     */
    interface NodePrinter<T extends Node> {
        void print(StringBuilder out, T node);
    }

    /**
     * Allow subclasses to replace a printer for a specific {@link Node}
     *
     * @param nodeClass   the class of the {@link Node}
     * @param nodePrinter the custom {@link NodePrinter}
     */
    void replacePrinter(Class<? extends Node> nodeClass, NodePrinter<? extends Node> nodePrinter) {
        this.printers.put(nodeClass, nodePrinter);
    }
}
