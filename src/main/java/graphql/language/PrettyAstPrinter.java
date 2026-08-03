package graphql.language;

import graphql.ExperimentalApi;
import graphql.collect.ImmutableKit;
import graphql.parser.CommentParser;
import graphql.parser.NodeToRuleCapturingParser;
import graphql.parser.ParserEnvironment;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.Assert.assertTrue;
import static graphql.parser.ParserEnvironment.newParserEnvironment;

/**
 * A printer that acts as a code formatter.
 *
 * This printer will preserve pretty much all elements from the source text, even those that are not part of the AST
 * (and are thus discarded by the {@link AstPrinter}), like comments.
 *
 * @see AstPrinter
 */
@ExperimentalApi
@NullMarked
public class PrettyAstPrinter extends AstPrinter {
    private final CommentParser commentParser;
    private final PrettyPrinterOptions options;

    public PrettyAstPrinter(NodeToRuleCapturingParser.ParserContext parserContext) {
        this(parserContext, PrettyPrinterOptions.defaultOptions);
    }

    public PrettyAstPrinter(NodeToRuleCapturingParser.ParserContext parserContext, PrettyPrinterOptions options) {
        super(false);
        this.commentParser = new CommentParser(parserContext);
        this.options = options;

        this.replacePrinter(DirectiveDefinition.class, directiveDefinition());
        this.replacePrinter(Document.class, document());
        this.replacePrinter(EnumTypeDefinition.class, enumTypeDefinition("enum"));
        this.replacePrinter(EnumTypeExtensionDefinition.class, enumTypeDefinition("extend enum"));
        this.replacePrinter(EnumValueDefinition.class, enumValueDefinition());
        this.replacePrinter(FieldDefinition.class, fieldDefinition());
        this.replacePrinter(InputObjectTypeDefinition.class, inputObjectTypeDefinition("input"));
        this.replacePrinter(InputObjectTypeExtensionDefinition.class, inputObjectTypeDefinition("extend input"));
        this.replacePrinter(InputValueDefinition.class, inputValueDefinition());
        this.replacePrinter(InterfaceTypeDefinition.class, implementingTypeDefinition("interface"));
        this.replacePrinter(InterfaceTypeExtensionDefinition.class, implementingTypeDefinition("extend interface"));
        this.replacePrinter(ObjectTypeDefinition.class, implementingTypeDefinition("type"));
        this.replacePrinter(ObjectTypeExtensionDefinition.class, implementingTypeDefinition("extend type"));
        this.replacePrinter(ScalarTypeDefinition.class, scalarTypeDefinition("scalar"));
        this.replacePrinter(ScalarTypeExtensionDefinition.class, scalarTypeDefinition("extend scalar"));
        this.replacePrinter(UnionTypeDefinition.class, unionTypeDefinition("union"));
        this.replacePrinter(UnionTypeExtensionDefinition.class, unionTypeDefinition("extend union"));
    }

    public String print(Node node) {
        StringBuilder builder = new StringBuilder();

        NodePrinter<Node> nodePrinter = this._findPrinter(node);
        nodePrinter.print(builder, node);

        return builder.toString();
    }

    public static String print(String schemaDefinition, PrettyPrinterOptions options) {
        NodeToRuleCapturingParser parser = new NodeToRuleCapturingParser();
        ParserEnvironment parserEnvironment = newParserEnvironment().document(schemaDefinition).build();
        Document document = parser.parseDocument(parserEnvironment);

        return new PrettyAstPrinter(parser.getParserContext(), options).print(document);
    }

    private NodePrinter<Document> document() {
        return (out, node) -> {
            String firstLineComment = commentParser.getCommentOnFirstLineOfDocument(node)
                    .map(this::comment)
                    .map(append("\n"))
                    .orElse("");

            out.append(firstLineComment);
            out.append(join(node.getDefinitions(), "\n\n")).append("\n");

            String endComments = comments(commentParser.getCommentsAfterAllDefinitions(node), "\n");

            out.append(endComments);
        };
    }

    private NodePrinter<DirectiveDefinition> directiveDefinition() {
        return (out, node) -> {
            out.append(outset(node));
            String locations = join(node.getDirectiveLocations(), " | ");
            String repeatable = node.isRepeatable() ? "repeatable " : "";
            out.append("directive @")
                    .append(node.getName())
                    .append(block(node.getInputValueDefinitions(), node, "(", ")", "\n", ", ", ""))
                    .append(" ")
                    .append(repeatable)
                    .append("on ")
                    .append(locations);
        };
    }

    private NodePrinter<EnumTypeDefinition> enumTypeDefinition(String nodeName) {
        return (out, node) -> {
            out.append(outset(node));
            out.append(spaced(
                    nodeName,
                    node.getName(),
                    directives(node.getDirectives()),
                    block(node.getEnumValueDefinitions(), node, "{", "}", "\n", null, null)
            ));
        };
    }

    private NodePrinter<EnumValueDefinition> enumValueDefinition() {
        return (out, node) -> {
            out.append(outset(node));
            out.append(spaced(
                    node.getName(),
                    directives(node.getDirectives())
            ));
        };
    }

    private NodePrinter<FieldDefinition> fieldDefinition() {
        return (out, node) -> {
            out.append(outset(node));
            out.append(node.getName())
                    .append(block(node.getInputValueDefinitions(), node, "(", ")", "\n", ", ", ""))
                    .append(": ")
                    .append(spaced(
                            type(node.getType()),
                            directives(node.getDirectives())
                    ));
        };
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

    private NodePrinter<InputObjectTypeDefinition> inputObjectTypeDefinition(String nodeName) {
        return (out, node) -> {
            out.append(outset(node));
            out.append(spaced(
                    nodeName,
                    node.getName(),
                    directives(node.getDirectives()),
                    block(node.getInputValueDefinitions(), node, "{", "}", "\n", null, null)
            ));
        };
    }

    private NodePrinter<InputValueDefinition> inputValueDefinition() {
        String nameTypeSep = ": ";
        String defaultValueEquals = "= ";
        return (out, node) -> {
            Value defaultValue = node.getDefaultValue();
            out.append(outset(node));
            out.append(spaced(
                    node.getName() + nameTypeSep + type(node.getType()),
                    wrap(defaultValueEquals, defaultValue, ""),
                    directives(node.getDirectives())
            ));
        };
    }


    private <T extends ImplementingTypeDefinition<?>> NodePrinter<T> implementingTypeDefinition(String nodeName) {
        return (out, node) -> {
            out.append(outset(node));
            out.append(spaced(
                    nodeName,
                    node.getName(),
                    wrap("implements ", block(node.getImplements(), node, "", "", " &\n", " & ", ""), ""),
                    directives(node.getDirectives()),
                    block(node.getFieldDefinitions(), node, "{", "}", "\n", null, null)
            ));
        };
    }

    private NodePrinter<ScalarTypeDefinition> scalarTypeDefinition(String nodeName) {
        return (out, node) -> {
            out.append(outset(node));
            out.append(spaced(
                    nodeName,
                    node.getName(),
                    directives(node.getDirectives())));
        };
    }

    private NodePrinter<UnionTypeDefinition> unionTypeDefinition(String nodeName) {
        String barSep = " | ";
        String equals = "= ";
        return (out, node) -> {
            out.append(outset(node));
            out.append(spaced(
                    nodeName,
                    node.getName(),
                    directives(node.getDirectives()),
                    equals + join(node.getMemberTypes(), barSep)
            ));
        };
    }

    private String node(Node node, @Nullable Class startClass) {
        if (startClass != null) {
            assertTrue(startClass.isInstance(node), "The starting class must be in the inherit tree");
        }
        StringBuilder builder = new StringBuilder();

        String comments = comments(commentParser.getLeadingComments(node), "\n");
        builder.append(comments);

        NodePrinter<Node> printer = _findPrinter(node, startClass);
        printer.print(builder, node);

        commentParser.getTrailingComment(node)
                .map(this::comment)
                .map(prepend(" "))
                .ifPresent(builder::append);

        return builder.toString();
    }

    private <T> boolean isEmpty(@Nullable List<T> list) {
        return list == null || list.isEmpty();
    }

    private boolean isEmpty(@Nullable String s) {
        return s == null || s.isBlank();
    }

    private <T> List<T> nvl(@Nullable List<T> list) {
        return list != null ? list : ImmutableKit.emptyList();
    }

    // Description and comments positioned before the node
    private String outset(Node<?> node) {
        String description = description(node);
        String commentsAfter = comments(commentParser.getCommentsAfterDescription(node), "\n");

        return description + commentsAfter;
    }

    private String description(Node<?> node) {
        Description description = ((AbstractDescribedNode<?>) node).getDescription();
        if (description == null || description.getContent() == null) {
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

    private String comment(Comment comment) {
        return comments(Collections.singletonList(comment));
    }

    private String comments(List<Comment> comments) {
        return comments(comments, "");
    }

    private String comments(List<Comment> comments, String suffix) {
        return comments(comments, "", suffix);
    }

    private String comments(List<Comment> comments, String prefix, String suffix) {
        if (comments.isEmpty()) {
            return "";
        }

        return comments.stream()
                .map(Comment::getContent)
                .map(content -> "#" + content)
                .collect(Collectors.joining("\n", prefix, suffix));
    }

    private String directives(List<Directive> directives) {
        return join(nvl(directives), " ");
    }

    private <T extends Node> String join(List<T> nodes, String delim) {
        return join(nodes, delim, "", "");
    }

    private <T extends Node> String join(List<T> nodes, String delim, String prefix, String suffix) {
        StringJoiner joiner = new StringJoiner(delim, prefix, suffix);

        for (T node : nodes) {
            joiner.add(node(node));
        }

        return joiner.toString();
    }

    private String node(Node node) {
        return node(node, null);
    }

    private String spaced(@Nullable String... args) {
        return join(" ", args);
    }

    private Function<String, String> prepend(String prefix) {
        return text -> prefix + text;
    }

    private Function<String, String> append(String suffix) {
        return text -> text + suffix;
    }

    private String join(String delim, @Nullable String... args) {
        StringJoiner joiner = new StringJoiner(delim);

        for (final String arg : args) {
            if (!isEmpty(arg)) {
                joiner.add(arg);
            }
        }

        return joiner.toString();
    }

    private <T extends Node> String block(List<T> nodes, Node parentNode, String prefix, String suffix, String separatorMultiline, @Nullable String separatorSingleLine, @Nullable String whenEmpty) {
        if (isEmpty(nodes)) {
            return whenEmpty != null ? whenEmpty : prefix + suffix;
        }

        boolean hasDescriptions = nodes.stream()
                .filter(node -> node instanceof AbstractDescribedNode)
                .map(node -> (AbstractDescribedNode) node)
                .map(AbstractDescribedNode::getDescription)
                .anyMatch(Objects::nonNull);

        boolean hasTrailingComments = nodes.stream()
                .map(commentParser::getTrailingComment)
                .anyMatch(Optional::isPresent);

        boolean hasLeadingComments = nodes.stream()
                .mapToLong(node -> commentParser.getLeadingComments(node).size())
                .sum() > 0;

        boolean isMultiline = hasDescriptions || hasTrailingComments || hasLeadingComments || separatorSingleLine == null;

        String appliedSeparator = isMultiline ? separatorMultiline : separatorSingleLine;

        String blockStart = commentParser.getBeginningOfBlockComment(parentNode, prefix)
                .map(this::comment)
                .map(commentText -> prefix + " " + commentText + "\n")
                .orElseGet(() -> prefix + (isMultiline ? "\n" : ""));

        String blockEndComments = comments(commentParser.getEndOfBlockComments(parentNode, suffix), "\n", "");
        String blockEnd = (isMultiline ? "\n" : "") + suffix;

        String content = nodes.stream().map(this::node).collect(Collectors.joining(appliedSeparator));
        String possiblyIndentedContent = isMultiline ? indent(content + blockEndComments) : content + blockEndComments;

        return blockStart + possiblyIndentedContent + blockEnd;
    }

    private String indent(String text) {
        return indent(new StringBuilder(text)).toString();
    }

    private StringBuilder indent(StringBuilder stringBuilder) {
        final String indentText = options.indentText;

        for (int i = 0; i < stringBuilder.length(); i++) {
            char c = stringBuilder.charAt(i);
            if (i == 0) {
                stringBuilder.replace(i, i, indentText);
                i += 2;
            }
            if (c == '\n') {
                stringBuilder.replace(i, i + 1, "\n" + indentText);
                i += 3;
            }
        }
        return stringBuilder;
    }

    /**
     * Contains options that modify how a document is printed.
     */
    public static class PrettyPrinterOptions {
        private final String indentText;
        private static final PrettyPrinterOptions defaultOptions = new PrettyPrinterOptions(IndentType.SPACE, 2);

        private PrettyPrinterOptions(IndentType indentType, int indentWidth) {
            this.indentText = String.join("", Collections.nCopies(indentWidth, indentType.character));
        }

        public static PrettyPrinterOptions defaultOptions() {
            return defaultOptions;
        }

        public static Builder builder() {
            return new Builder();
        }

        public enum IndentType {
            TAB("\t"), SPACE(" ");

            private final String character;

            IndentType(String character) {
                this.character = character;
            }
        }

        @NullUnmarked
        public static class Builder {
            private IndentType indentType;
            private int indentWidth = 1;

            public Builder indentType(IndentType indentType) {
                this.indentType = indentType;
                return this;
            }

            public Builder indentWith(int indentWidth) {
                this.indentWidth = indentWidth;
                return this;
            }

            public PrettyPrinterOptions build() {
                return new PrettyPrinterOptions(indentType, indentWidth);
            }
        }
    }
}
