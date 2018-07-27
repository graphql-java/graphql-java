package graphql.language;


import graphql.Internal;
import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/*
 * This is provided to a DataFetcher, therefore it is a public API.
 * This might change in the future.
 */
@PublicApi
public class Field extends AbstractNode<Field> implements Selection<Field>, SelectionSetContainer<Field>, DirectivesContainer<Field> {

    private final String name;
    private final String alias;
    private final List<Argument> arguments;
    private final List<Directive> directives;
    private final SelectionSet selectionSet;

    @Internal
    protected Field(String name,
                  String alias,
                  List<Argument> arguments,
                  List<Directive> directives,
                  SelectionSet selectionSet,
                  SourceLocation sourceLocation,
                  List<Comment> comments) {
        super(sourceLocation, comments);
        this.name = name;
        this.alias = alias;
        this.arguments = arguments;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }


    /**
     * alternative to using a Builder for convenience
     */
    public Field(String name) {
        this(name, null, new ArrayList<>(), new ArrayList<>(), null, null, new ArrayList<>());
    }

    /**
     * alternative to using a Builder for convenience
     *
     */
    public Field(String name, List<Argument> arguments) {
        this(name, null, arguments, new ArrayList<>(), null, null, new ArrayList<>());
    }

    /**
     * alternative to using a Builder for convenience
     */
    public Field(String name, List<Argument> arguments, SelectionSet selectionSet) {
        this(name, null, arguments, new ArrayList<>(), selectionSet, null, new ArrayList<>());
    }

    /**
     * alternative to using a Builder for convenience
     */
    public Field(String name, SelectionSet selectionSet) {
        this(name, null, new ArrayList<>(), new ArrayList<>(), selectionSet, null, new ArrayList<>());
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(arguments);
        result.addAll(directives);
        if (selectionSet != null) result.add(selectionSet);
        return result;
    }


    @Override
    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public List<Argument> getArguments() {
        return new ArrayList<>(arguments);
    }

    @Override
    public List<Directive> getDirectives() {
        return new ArrayList<>(directives);
    }

    @Override
    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field that = (Field) o;

        return NodeUtil.isEqualTo(this.name, that.name) && NodeUtil.isEqualTo(this.alias, that.alias);
    }

    @Override
    public Field deepCopy() {
        return new Field(name,
                alias,
                deepCopy(arguments),
                deepCopy(directives),
                deepCopy(selectionSet),
                getSourceLocation(),
                getComments()
        );
    }

    @Override
    public String toString() {
        return "Field{" +
                "name='" + name + '\'' +
                ", alias='" + alias + '\'' +
                ", arguments=" + arguments +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitField(this, context);
    }

    public static Builder newField() {
        return new Builder();
    }

    public static Builder newField(String name) {
        return new Builder().name(name);
    }

    public static Builder newField(String name, SelectionSet selectionSet) {
        return new Builder().name(name).selectionSet(selectionSet);
    }

    public Field transform(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private String alias;
        private List<Argument> arguments = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();
        private SelectionSet selectionSet;

        private Builder() {
        }

        private Builder(Field existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.alias = existing.getAlias();
            this.arguments = existing.getArguments();
            this.directives = existing.getDirectives();
            this.selectionSet = existing.getSelectionSet();
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder arguments(List<Argument> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder selectionSet(SelectionSet selectionSet) {
            this.selectionSet = selectionSet;
            return this;
        }

        public Field build() {
            Field field = new Field(name, alias, arguments, directives, selectionSet, sourceLocation, comments);
            return field;
        }
    }
}
