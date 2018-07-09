package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FieldDefinition extends AbstractNode<FieldDefinition> implements DirectivesContainer<FieldDefinition> {
    private final String name;
    private Type type;
    private Description description;
    private final List<InputValueDefinition> inputValueDefinitions;
    private final List<Directive> directives;

    public FieldDefinition(String name) {
        this(name, null, new ArrayList<>(), new ArrayList<>());
    }

    public FieldDefinition(String name, Type type) {
        this(name, type, new ArrayList<>(), new ArrayList<>());
    }

    public FieldDefinition(String name, Type type, List<InputValueDefinition> inputValueDefinitions, List<Directive> directives) {
        this.name = name;
        this.type = type;
        this.inputValueDefinitions = inputValueDefinitions;
        this.directives = directives;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
    }

    public void setDescription(Description description) {
        this.description = description;
    }

    public List<InputValueDefinition> getInputValueDefinitions() {
        return inputValueDefinitions;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        result.addAll(inputValueDefinitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FieldDefinition that = (FieldDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public FieldDefinition deepCopy() {
        return new FieldDefinition(name,
                deepCopy(type),
                deepCopy(inputValueDefinitions),
                deepCopy(directives)
        );
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitFieldDefinition(this, context);
    }

    public static Builder newFieldDefintion() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private String name;
        private List<Comment> comments = Collections.emptyList();
        private Type type;
        private Description description;
        private List<InputValueDefinition> inputValueDefinitions;
        private List<Directive> directives;

        private Builder() {
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder inputValueDefinitions(List<InputValueDefinition> inputValueDefinitions) {
            this.inputValueDefinitions = inputValueDefinitions;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public FieldDefinition build() {
            FieldDefinition fieldDefinition = new FieldDefinition(name, type, inputValueDefinitions, directives);
            fieldDefinition.setSourceLocation(sourceLocation);
            fieldDefinition.setComments(comments);
            fieldDefinition.setDescription(description);
            return fieldDefinition;
        }
    }
}
