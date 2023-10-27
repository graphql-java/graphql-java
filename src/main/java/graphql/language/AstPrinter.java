package graphql.language;

import graphql.AssertException;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertTrue;
import static graphql.util.EscapeUtil.escapeJsonString;
import static java.lang.String.valueOf;

/**
 * This can take graphql language AST and print it out as a string
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@PublicApi
public class AstPrinter {
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
            return (out, node) -> out.append(node.getName()).append(':').append(value(node.getValue()));
        }
        return (out, node) -> out.append(node.getName()).append(": ").append(value(node.getValue()));
    }

    private NodePrinter<Document> document() {
        if (compactMode) {
            return (out, node) -> out.append(join(node.getDefinitions(), " "));
        }
        return (out, node) -> out.append(join(node.getDefinitions(), "\n\n")).append("\n");
    }

    private NodePrinter<Directive> directive() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            String arguments = wrap("(", join(node.getArguments(), argSep), ")");
            out.append('@').append(node.getName()).append(arguments);
        };
    }

    private NodePrinter<DirectiveDefinition> directiveDefinition() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            out.append(description(node));
            String arguments = wrap("(", join(node.getInputValueDefinitions(), argSep), ")");
            String locations = join(node.getDirectiveLocations(), " | ");
            String repeatable = node.isRepeatable() ? "repeatable " : "";
            out.append("directive @")
                    .append(node.getName())
                    .append(arguments)
                    .append(" ")
                    .append(repeatable)
                    .append("on ")
                    .append(locations);
        };
    }

    private NodePrinter<DirectiveLocation> directiveLocation() {
        return (out, node) -> out.append(node.getName());
    }

    private NodePrinter<EnumTypeDefinition> enumTypeDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "enum",
                    node.getName(),
                    directives(node.getDirectives()),
                    block(node.getEnumValueDefinitions())
            ));
        };
    }

    private NodePrinter<EnumValue> enumValue() {
        return (out, node) -> out.append(node.getName());
    }

    private NodePrinter<EnumValueDefinition> enumValueDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    node.getName(),
                    directives(node.getDirectives())
            ));
        };
    }

    private NodePrinter<Field> field() {
        final String argSep = compactMode ? "," : ", ";
        final String aliasSuffix = compactMode ? ":" : ": ";
        return (out, node) -> {
            String alias = wrap("", node.getAlias(), aliasSuffix);
            String name = node.getName();
            String arguments = wrap("(", join(node.getArguments(), argSep), ")");
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            if (compactMode) {
                out.append(spaced(
                        alias + name + arguments,
                        directives
                ));
                out.append(selectionSet);
            } else {
                out.append(spaced(
                        alias + name + arguments,
                        directives,
                        selectionSet
                ));
            }
        };
    }


    private NodePrinter<FieldDefinition> fieldDefinition() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            String args;
            if (hasDescription(Collections.singletonList(node)) && !compactMode) {
                out.append(description(node));
                args = join(node.getInputValueDefinitions(), "\n");
                out.append(node.getName())
                        .append(wrap("(\n", args, ")"))
                        .append(": ")
                        .append(spaced(
                                type(node.getType()),
                                directives(node.getDirectives())
                        ));
            } else {
                args = join(node.getInputValueDefinitions(), argSep);
                out.append(node.getName())
                        .append(wrap("(", args, ")"))
                        .append(": ")
                        .append(spaced(
                                type(node.getType()),
                                directives(node.getDirectives())
                        ));
            }
        };
    }

    private boolean hasDescription(List<? extends Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof AbstractDescribedNode) {
                AbstractDescribedNode<?> describedNode = (AbstractDescribedNode<?>) node;
                if (describedNode.getDescription() != null) {
                    return true;
                }
            }
        }

        return false;
    }

    private NodePrinter<FragmentDefinition> fragmentDefinition() {
        return (out, node) -> {
            String name = node.getName();
            String typeCondition = type(node.getTypeCondition());
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            out.append("fragment ").append(name).append(" on ").append(typeCondition)
                    .append(' ')
                    .append(directives)
                    .append(selectionSet);
        };
    }

    private NodePrinter<FragmentSpread> fragmentSpread() {
        return (out, node) -> {
            String name = node.getName();
            String directives = directives(node.getDirectives());

            out.append("...").append(name).append(directives);
        };
    }

    private NodePrinter<InlineFragment> inlineFragment() {
        return (out, node) -> {
            TypeName typeName = node.getTypeCondition();
            //Inline fragments may not have a type condition
            String typeCondition = typeName == null ? "" : wrap("on ", type(typeName), "");
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            if (compactMode) {
                // believe it or not but "...on Foo" is valid syntax
                out.append("...");
                out.append(spaced(
                        typeCondition,
                        directives
                ));
                out.append(selectionSet);
            } else {
                out.append(spaced(
                        "...",
                        typeCondition,
                        directives,
                        selectionSet
                ));
            }
        };
    }

    private NodePrinter<InputObjectTypeDefinition> inputObjectTypeDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "input",
                    node.getName(),
                    directives(node.getDirectives()),
                    block(node.getInputValueDefinitions())
            ));
        };
    }

    private NodePrinter<InputValueDefinition> inputValueDefinition() {
        String nameTypeSep = compactMode ? ":" : ": ";
        String defaultValueEquals = compactMode ? "=" : "= ";
        return (out, node) -> {
            Value defaultValue = node.getDefaultValue();
            out.append(description(node));
            out.append(spaced(
                    node.getName() + nameTypeSep + type(node.getType()),
                    wrap(defaultValueEquals, defaultValue, ""),
                    directives(node.getDirectives())
            ));
        };
    }

    private NodePrinter<InterfaceTypeDefinition> interfaceTypeDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "interface",
                    node.getName(),
                    wrap("implements ", join(node.getImplements(), " & "), ""),
                    directives(node.getDirectives()),
                    block(node.getFieldDefinitions())
            ));
        };
    }

    private NodePrinter<ObjectField> objectField() {
        String nameValueSep = compactMode ? ":" : " : ";
        return (out, node) -> out.append(node.getName()).append(nameValueSep).append(value(node.getValue()));
    }

    private NodePrinter<OperationDefinition> operationDefinition() {
        final String argSep = compactMode ? "," : ", ";
        return (out, node) -> {
            String op = node.getOperation().toString().toLowerCase();
            String name = node.getName();
            String varDefinitions = wrap("(", join(nvl(node.getVariableDefinitions()), argSep), ")");
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            // Anonymous queries with no directives or variable definitions can use
            // the query short form.
            if (isEmpty(name) && isEmpty(directives) && isEmpty(varDefinitions) && op.equals("query")) {
                out.append(selectionSet);
            } else {
                if (compactMode) {
                    out.append(spaced(op, smooshed(name, varDefinitions), directives));
                    out.append(selectionSet);
                } else {
                    out.append(spaced(op, smooshed(name, varDefinitions), directives, selectionSet));
                }
            }
        };
    }

    private NodePrinter<OperationTypeDefinition> operationTypeDefinition() {
        String nameTypeSep = compactMode ? ":" : ": ";
        return (out, node) -> out.append(node.getName()).append(nameTypeSep).append(type(node.getTypeName()));
    }

    private NodePrinter<ObjectTypeDefinition> objectTypeDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "type",
                    node.getName(),
                    wrap("implements ", join(node.getImplements(), " & "), ""),
                    directives(node.getDirectives()),
                    block(node.getFieldDefinitions())
            ));
        };
    }

    private NodePrinter<SelectionSet> selectionSet() {
        return (out, node) -> {
            String block = block(node.getSelections());
            out.append(block);
        };
    }

    private NodePrinter<ScalarTypeDefinition> scalarTypeDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "scalar",
                    node.getName(),
                    directives(node.getDirectives())));
        };
    }


    private NodePrinter<SchemaDefinition> schemaDefinition() {
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "schema",
                    directives(node.getDirectives()),
                    block(node.getOperationTypeDefinitions())
            ));
        };
    }


    private NodePrinter<Type> type() {
        return (out, node) -> out.append(type(node));
    }

    private String type(Type type) {
        if (type instanceof NonNullType) {
            NonNullType inner = (NonNullType) type;
            return wrap("", type(inner.getType()), "!");
        } else if (type instanceof ListType) {
            ListType inner = (ListType) type;
            return wrap("[", type(inner.getType()), "]");
        } else {
            TypeName inner = (TypeName) type;
            return inner.getName();
        }
    }

    private NodePrinter<ObjectTypeExtensionDefinition> objectTypeExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, ObjectTypeDefinition.class));
    }

    private NodePrinter<EnumTypeExtensionDefinition> enumTypeExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, EnumTypeDefinition.class));
    }

    private NodePrinter<InterfaceTypeDefinition> interfaceTypeExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, InterfaceTypeDefinition.class));
    }

    private NodePrinter<UnionTypeExtensionDefinition> unionTypeExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, UnionTypeDefinition.class));
    }

    private NodePrinter<ScalarTypeExtensionDefinition> scalarTypeExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, ScalarTypeDefinition.class));
    }

    private NodePrinter<InputObjectTypeExtensionDefinition> inputObjectTypeExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, InputObjectTypeDefinition.class));
    }

    private NodePrinter<SchemaExtensionDefinition> schemaExtensionDefinition() {
        return (out, node) -> out.append("extend ").append(node(node, SchemaDefinition.class));
    }

    private NodePrinter<UnionTypeDefinition> unionTypeDefinition() {
        String barSep = compactMode ? "|" : " | ";
        String equals = compactMode ? "=" : "= ";
        return (out, node) -> {
            out.append(description(node));
            out.append(spaced(
                    "union",
                    node.getName(),
                    directives(node.getDirectives()),
                    equals + join(node.getMemberTypes(), barSep)
            ));
        };
    }

    private NodePrinter<VariableDefinition> variableDefinition() {
        String nameTypeSep = compactMode ? ":" : ": ";
        String defaultValueEquals = compactMode ? "=" : " = ";
        return (out, node) -> out.append('$')
                .append(node.getName())
                .append(nameTypeSep)
                .append(type(node.getType()))
                .append(wrap(defaultValueEquals, node.getDefaultValue(), ""))
                .append(directives(node.getDirectives()));
    }

    private NodePrinter<VariableReference> variableReference() {
        return (out, node) -> out.append('$').append(node.getName());
    }

    private String node(Node node) {
        return node(node, null);
    }

    private String node(Node node, Class startClass) {
        if (startClass != null) {
            assertTrue(startClass.isInstance(node), () -> "The starting class must be in the inherit tree");
        }
        StringBuilder builder = new StringBuilder();
        NodePrinter<Node> printer = _findPrinter(node, startClass);
        printer.print(builder, node);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    <T extends Node> NodePrinter<T> _findPrinter(Node node) {
        return _findPrinter(node, null);
    }

    <T extends Node> NodePrinter<T> _findPrinter(Node node, Class startClass) {
        if (node == null) {
            return (out, type) -> {
            };
        }
        Class clazz = startClass != null ? startClass : node.getClass();
        while (clazz != Object.class) {
            NodePrinter nodePrinter = printers.get(clazz);
            if (nodePrinter != null) {
                //noinspection unchecked
                return nodePrinter;
            }
            clazz = clazz.getSuperclass();
        }
        throw new AssertException(String.format("We have a missing printer implementation for %s : report a bug!", clazz));
    }

    private <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    private <T> List<T> nvl(List<T> list) {
        return list != null ? list : ImmutableKit.emptyList();
    }

    private NodePrinter<Value> value() {
        return (out, node) -> out.append(value(node));
    }

    private String value(Value value) {
        String argSep = compactMode ? "," : ", ";
        if (value instanceof IntValue) {
            return valueOf(((IntValue) value).getValue());
        } else if (value instanceof FloatValue) {
            return valueOf(((FloatValue) value).getValue());
        } else if (value instanceof StringValue) {
            return "\"" + escapeJsonString(((StringValue) value).getValue()) + "\"";
        } else if (value instanceof EnumValue) {
            return valueOf(((EnumValue) value).getName());
        } else if (value instanceof BooleanValue) {
            return valueOf(((BooleanValue) value).isValue());
        } else if (value instanceof NullValue) {
            return "null";
        } else if (value instanceof ArrayValue) {
            return "[" + join(((ArrayValue) value).getValues(), argSep) + "]";
        } else if (value instanceof ObjectValue) {
            return "{" + join(((ObjectValue) value).getObjectFields(), argSep) + "}";
        } else if (value instanceof VariableReference) {
            return "$" + ((VariableReference) value).getName();
        }
        return "";
    }

    private String description(Node<?> node) {
        Description description = ((AbstractDescribedNode) node).getDescription();
        if (description == null || description.getContent() == null || compactMode) {
            return "";
        }
        String s;
        boolean startNewLine = description.getContent().length() > 0 && description.getContent().charAt(0) == '\n';
        if (description.isMultiLine()) {
            s = "\"\"\"" + (startNewLine ? "" : "\n") + description.getContent() + "\n\"\"\"\n";
        } else {
            s = "\"" + description.getContent() + "\"\n";
        }
        return s;
    }

    private String directives(List<Directive> directives) {
        return join(nvl(directives), compactMode? "" : " ");
    }

    private <T extends Node> String join(List<T> nodes, String delim) {
        return join(nodes, delim, "", "");
    }

    /*
     * Some joined nodes don't need delimiters between them and some do
     * This encodes that knowledge of those that don't require delimiters
     */
    @SuppressWarnings("SameParameterValue")
    private <T extends Node> String joinTight(List<T> nodes, String delim, String prefix, String suffix) {
        StringBuilder joined = new StringBuilder();
        joined.append(prefix);

        String lastNodeText = "";
        boolean first = true;
        for (T node : nodes) {
            if (first) {
                first = false;
            } else {
                boolean canButtTogether = lastNodeText.endsWith("}");
                if (! canButtTogether) {
                    joined.append(delim);
                }
            }
            String nodeText = this.node(node);
            lastNodeText = nodeText;
            joined.append(nodeText);
        }

        joined.append(suffix);
        return joined.toString();
    }

    private <T extends Node> String join(List<T> nodes, String delim, String prefix, String suffix) {
        StringBuilder joined = new StringBuilder();
        joined.append(prefix);

        boolean first = true;
        for (T node : nodes) {
            if (first) {
                first = false;
            } else {
                joined.append(delim);
            }
            joined.append(this.node(node));
        }

        joined.append(suffix);
        return joined.toString();
    }

    private String spaced(String... args) {
        return join(" ", args);
    }

    private String smooshed(String... args) {
        return join("", args);
    }

    private String join(String delim, String... args) {
        StringBuilder builder = new StringBuilder();

        boolean first = true;
        for (final String arg : args) {
            if (isEmpty(arg)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                builder.append(delim);
            }
            builder.append(arg);
        }

        return builder.toString();
    }

    String wrap(String start, String maybeString, String end) {
        if (isEmpty(maybeString)) {
            if (start.equals("\"") && end.equals("\"")) {
                return "\"\"";
            }
            return "";
        }
        return new StringBuilder().append(start).append(maybeString).append(!isEmpty(end) ? end : "").toString();
    }

    private <T extends Node> String block(List<T> nodes) {
        if (isEmpty(nodes)) {
            return "{}";
        }
        if (compactMode) {
            String joinedNodes = joinTight(nodes, " ", "", "");
            return new StringBuilder().append("{").append(joinedNodes).append("}").toString();
        }
        return indent(new StringBuilder().append("{\n").append(join(nodes, "\n")))
                + "\n}";
    }

    private StringBuilder indent(StringBuilder maybeString) {
        for (int i = 0; i < maybeString.length(); i++) {
            char c = maybeString.charAt(i);
            if (c == '\n') {
                maybeString.replace(i, i + 1, "\n  ");
                i += 3;
            }
        }
        return maybeString;
    }

    @SuppressWarnings("SameParameterValue")
    String wrap(String start, Node maybeNode, String end) {
        if (maybeNode == null) {
            return "";
        }
        return new StringBuilder().append(start).append(node(maybeNode)).append(isEmpty(end) ? "" : end).toString();
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
        printImpl(builder, node, false);
        return builder.toString();
    }

    /**
     * This will pretty print the AST node in graphql language format
     *
     * @param writer the place to put the output
     * @param node   the AST node to print
     */
    public static void printAst(Writer writer, Node node) {
        String ast = printAst(node);
        PrintWriter printer = new PrintWriter(writer);
        printer.write(ast);
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

    private static void printImpl(StringBuilder writer, Node node, boolean compactMode) {
        AstPrinter astPrinter = new AstPrinter(compactMode);
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
     * @param nodeClass the class of the {@link Node}
     * @param nodePrinter the custom {@link NodePrinter}
     */
    void replacePrinter(Class<? extends Node> nodeClass, NodePrinter<? extends Node> nodePrinter) {
        this.printers.put(nodeClass, nodePrinter);
    }
}
