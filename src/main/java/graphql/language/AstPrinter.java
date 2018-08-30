package graphql.language;

import graphql.AssertException;
import graphql.PublicApi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertTrue;
import static java.lang.String.valueOf;
import static java.util.stream.Collectors.joining;

/**
 * This can take graphql language AST and print it out as a string
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@PublicApi
public class AstPrinter {

    private static final Map<Class<? extends Node>, NodePrinter<? extends Node>> printers = new LinkedHashMap<>();

    static {
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
        printers.put(SelectionSet.class, selectionSet());
        printers.put(StringValue.class, value());
        printers.put(TypeName.class, type());
        printers.put(UnionTypeDefinition.class, unionTypeDefinition());
        printers.put(UnionTypeExtensionDefinition.class, unionTypeExtensionDefinition());
        printers.put(VariableDefinition.class, variableDefinition());
        printers.put(VariableReference.class, variableReference());
    }

    private static NodePrinter<Argument> argument() {
        return (out, node) -> out.printf("%s: %s", node.getName(), value(node.getValue()));
    }

    private static NodePrinter<Document> document() {
        return (out, node) -> out.printf("%s\n", join(node.getDefinitions(), "\n\n"));
    }

    private static NodePrinter<Directive> directive() {
        return (out, node) -> {
            String arguments = wrap("(", join(node.getArguments(), ", "), ")");
            out.printf("@%s%s", node.getName(), arguments);
        };
    }

    private static NodePrinter<DirectiveDefinition> directiveDefinition() {
        return (out, node) -> {
            String arguments = wrap("(", join(node.getInputValueDefinitions(), ", "), ")");
            String locations = join(node.getDirectiveLocations(), " | ");
            out.printf("directive @%s%s on %s", node.getName(), arguments, locations);
        };
    }

    private static NodePrinter<DirectiveLocation> directiveLocation() {
        return (out, node) -> out.print(node.getName());
    }

    private static NodePrinter<EnumTypeDefinition> enumTypeDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s",
                    spaced(
                            "enum",
                            node.getName(),
                            directives(node.getDirectives()),
                            block(node.getEnumValueDefinitions())
                    ));
        };
    }

    private static NodePrinter<EnumValue> enumValue() {
        return (out, node) -> out.printf("%s", node.getName());
    }

    private static NodePrinter<EnumValueDefinition> enumValueDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s",
                    spaced(
                            node.getName(),
                            directives(node.getDirectives())
                    ));
        };
    }

    private static NodePrinter<Field> field() {
        return (out, node) -> {
            String alias = wrap("", node.getAlias(), ": ");
            String name = node.getName();
            String arguments = wrap("(", join(node.getArguments(), ", "), ")");
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            out.printf("%s", spaced(
                    alias + name + arguments,
                    directives,
                    selectionSet
            ));
        };
    }


    private static NodePrinter<FieldDefinition> fieldDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            String args;
            if (hasComments(node.getInputValueDefinitions())) {
                args = join(node.getInputValueDefinitions(), "\n");
                out.printf("%s", node.getName() +
                        wrap("(\n", args, "\n)") +
                        ": " +
                        spaced(
                                type(node.getType()),
                                directives(node.getDirectives())
                        )
                );
            } else {
                args = join(node.getInputValueDefinitions(), ", ");
                out.printf("%s", node.getName() +
                        wrap("(", args, ")") +
                        ": " +
                        spaced(
                                type(node.getType()),
                                directives(node.getDirectives())
                        )
                );
            }
        };
    }

    private static boolean hasComments(List<? extends Node> nodes) {
        return nodes.stream().anyMatch(it -> it.getComments().size() > 0);
    }

    private static NodePrinter<FragmentDefinition> fragmentDefinition() {
        return (out, node) -> {
            String name = node.getName();
            String typeCondition = type(node.getTypeCondition());
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            out.printf("fragment %s on %s ", name, typeCondition);
            out.printf("%s", directives + selectionSet);
        };
    }

    private static NodePrinter<FragmentSpread> fragmentSpread() {
        return (out, node) -> {
            String name = node.getName();
            String directives = directives(node.getDirectives());

            out.printf("...%s%s", name, directives);
        };
    }

    private static NodePrinter<InlineFragment> inlineFragment() {
        return (out, node) -> {
            String typeCondition = wrap("on ", type(node.getTypeCondition()), "");
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "...",
                    typeCondition,
                    directives,
                    selectionSet
            ));
        };
    }

    private static NodePrinter<InputObjectTypeDefinition> inputObjectTypeDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "input",
                    node.getName(),
                    directives(node.getDirectives()),
                    block(node.getInputValueDefinitions())
                    )
            );
        };
    }

    private static NodePrinter<InputValueDefinition> inputValueDefinition() {
        return (out, node) -> {
            Value defaultValue = node.getDefaultValue();
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    node.getName() + ": " + type(node.getType()),
                    wrap("= ", defaultValue, ""),
                    directives(node.getDirectives())
                    )
            );
        };
    }

    private static NodePrinter<InterfaceTypeDefinition> interfaceTypeDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "interface",
                    node.getName(),
                    directives(node.getDirectives()),
                    block(node.getFieldDefinitions())
                    )
            );
        };
    }

    private static NodePrinter<ObjectField> objectField() {
        return (out, node) -> out.printf("%s : %s", node.getName(), value(node.getValue()));
    }


    private static NodePrinter<OperationDefinition> operationDefinition() {
        return (out, node) -> {
            String op = node.getOperation().toString().toLowerCase();
            String name = node.getName();
            String varDefinitions = wrap("(", join(nvl(node.getVariableDefinitions()), ", "), ")");
            String directives = directives(node.getDirectives());
            String selectionSet = node(node.getSelectionSet());

            // Anonymous queries with no directives or variable definitions can use
            // the query short form.
            if (isEmpty(name) && isEmpty(directives) && isEmpty(varDefinitions) && op.equals("QUERY")) {
                out.printf("%s", selectionSet);
            } else {
                out.printf("%s", spaced(op, smooshed(name, varDefinitions), directives, selectionSet));
            }
        };
    }

    private static NodePrinter<OperationTypeDefinition> operationTypeDefinition() {
        return (out, node) -> out.printf("%s: %s", node.getName(), type(node.getType()));
    }

    private static NodePrinter<ObjectTypeDefinition> objectTypeDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "type",
                    node.getName(),
                    wrap("implements ", join(node.getImplements(), " & "), ""),
                    directives(node.getDirectives()),
                    block(node.getFieldDefinitions())
            ));
        };
    }

    private static NodePrinter<SelectionSet> selectionSet() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", block(node.getSelections()));
        };
    }

    private static NodePrinter<ScalarTypeDefinition> scalarTypeDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "scalar",
                    node.getName(),
                    directives(node.getDirectives())));
        };
    }


    private static NodePrinter<SchemaDefinition> schemaDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "schema",
                    directives(node.getDirectives()),
                    block(node.getOperationTypeDefinitions())

            ));
        };
    }


    private static NodePrinter<Type> type() {
        return (out, node) -> out.print(type(node));
    }

    static private String type(Type type) {
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

    private static NodePrinter<ObjectTypeExtensionDefinition> objectTypeExtensionDefinition() {
        return (out, node) -> out.printf("extend %s", node(node, ObjectTypeDefinition.class));
    }

    private static NodePrinter<EnumTypeExtensionDefinition> enumTypeExtensionDefinition() {
        return (out, node) -> out.printf("extend %s", node(node, EnumTypeDefinition.class));
    }

    private static NodePrinter<InterfaceTypeDefinition> interfaceTypeExtensionDefinition() {
        return (out, node) -> out.printf("extend %s", node(node, InterfaceTypeDefinition.class));
    }

    private static NodePrinter<UnionTypeExtensionDefinition> unionTypeExtensionDefinition() {
        return (out, node) -> out.printf("extend %s", node(node, UnionTypeDefinition.class));
    }

    private static NodePrinter<ScalarTypeExtensionDefinition> scalarTypeExtensionDefinition() {
        return (out, node) -> out.printf("extend %s", node(node, ScalarTypeDefinition.class));
    }

    private static NodePrinter<InputObjectTypeExtensionDefinition> inputObjectTypeExtensionDefinition() {
        return (out, node) -> out.printf("extend %s", node(node, InputObjectTypeDefinition.class));
    }

    private static NodePrinter<UnionTypeDefinition> unionTypeDefinition() {
        return (out, node) -> {
            out.printf("%s", comments(node));
            out.printf("%s", spaced(
                    "union",
                    node.getName(),
                    directives(node.getDirectives()),
                    "= " + join(node.getMemberTypes(), " | ")
            ));
        };
    }

    private static NodePrinter<VariableDefinition> variableDefinition() {
        return (out, node) -> out.printf("$%s: %s%s",
                node.getName(),
                type(node.getType()),
                wrap(" = ", node.getDefaultValue(), "")
        );
    }

    private static NodePrinter<VariableReference> variableReference() {
        return (out, node) -> out.printf("$%s", node.getName());
    }

    static private String node(Node node) {
        return node(node, null);
    }

    static private String node(Node node, Class startClass) {
        if (startClass != null) {
            assertTrue(startClass.isInstance(node), "The starting class must be in the inherit tree");
        }
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        NodePrinter<Node> printer = _findPrinter(node, startClass);
        printer.print(out, node);
        return sw.toString();
    }

    @SuppressWarnings("unchecked")
    static private <T extends Node> NodePrinter<T> _findPrinter(Node node) {
        return _findPrinter(node, null);
    }

    static private <T extends Node> NodePrinter<T> _findPrinter(Node node, Class startClass) {
        if (node == null) {
            return (out, type) -> {
            };
        }
        Class clazz = startClass != null ? startClass : node.getClass();
        while (clazz != Object.class) {
            NodePrinter nodePrinter = printers.get(clazz);
            if (nodePrinter != null) {
                return nodePrinter;
            }
            clazz = clazz.getSuperclass();
        }
        throw new AssertException(String.format("We have a missing printer implementation for %s : report a bug!", clazz));
    }

    static private <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    static private boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    static private <T> List<T> nvl(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private static NodePrinter<Value> value() {
        return (out, node) -> out.print(value(node));
    }

    static private String value(Value value) {
        if (value instanceof IntValue) {
            return valueOf(((IntValue) value).getValue());
        } else if (value instanceof FloatValue) {
            return valueOf(((FloatValue) value).getValue());
        } else if (value instanceof StringValue) {
            return wrap("\"", valueOf(((StringValue) value).getValue()), "\"");
        } else if (value instanceof EnumValue) {
            return valueOf(((EnumValue) value).getName());
        } else if (value instanceof BooleanValue) {
            return valueOf(((BooleanValue) value).isValue());
        } else if (value instanceof NullValue) {
            return "null";
        } else if (value instanceof ArrayValue) {
            return "[" + join(((ArrayValue) value).getValues(), ", ") + "]";
        } else if (value instanceof ObjectValue) {
            return "{" + join(((ObjectValue) value).getObjectFields(), ", ") + "}";
        } else if (value instanceof VariableReference) {
            return "$" + ((VariableReference) value).getName();
        }
        return "";
    }

    static private String comments(Node<?> node) {
        List<Comment> comments = nvl(node.getComments());
        if (isEmpty(comments)) {
            return "";
        }
        String s = comments.stream().map(c -> "#" + c.getContent()).collect(joining("\n", "", "\n"));
        return s;
    }


    private static String directives(List<Directive> directives) {
        return join(nvl(directives), " ");
    }

    static private <T extends Node> String join(List<T> nodes, String delim) {
        return join(nodes, delim, "", "");
    }

    @SuppressWarnings("SameParameterValue")
    static private <T extends Node> String join(List<T> nodes, String delim, String prefix, String suffix) {
        String s = nvl(nodes).stream().map(AstPrinter::node).collect(joining(delim, prefix, suffix));
        return s;
    }

    static private String spaced(String... args) {
        return join(" ", args);
    }

    static private String smooshed(String... args) {
        return join("", args);
    }

    static private String join(String delim, String... args) {
        String s = Arrays.stream(args).filter(arg -> !isEmpty(arg)).collect(joining(delim));
        return s;
    }

    static String wrap(String start, String maybeString, String end) {
        if (isEmpty(maybeString)) {
            if (start.equals("\"") && end.equals("\"")) {
                return "\"\"";
            }
            return "";
        }
        return start + maybeString + (!isEmpty(end) ? end : "");
    }

    private static <T extends Node> String block(List<T> nodes) {
        if (isEmpty(nodes)) {
            return "{}";
        }
        return indent("{\n"
                + join(nodes, "\n"))
                + "\n}";
    }

    private static String indent(String maybeString) {
        if (isEmpty(maybeString)) {
            return "";
        }
        maybeString = maybeString.replaceAll("\\n", "\n  ");
        return maybeString;
    }

    @SuppressWarnings("SameParameterValue")
    static String wrap(String start, Node maybeNode, String end) {
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
        StringWriter sw = new StringWriter();
        printAst(sw, node);
        return sw.toString();
    }

    /**
     * This will print the Ast node in graphql language format.
     * The format is derived from the pretty print version by replacing
     * all newlines and indentations through single space.
     *
     * @param node the AST node to print
     *
     * @return the printed node in graphql language format
     */
    public static String printAstCompact(Node node) {
        StringWriter sw = new StringWriter();
        printAst(sw, node);
        return sw.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * This will pretty print the AST node in graphql language format
     *
     * @param writer the place to put the output
     * @param node   the AST node to print
     */
    public static void printAst(Writer writer, Node node) {
        NodePrinter<Node> printer = _findPrinter(node);
        printer.print(new PrintWriter(writer), node);
    }

    /**
     * These print nodes into output writers
     *
     * @param <T> the type of node
     */
    private interface NodePrinter<T extends Node> {
        void print(PrintWriter out, T node);
    }
}
