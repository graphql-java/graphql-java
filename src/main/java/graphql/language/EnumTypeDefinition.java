package graphql.language;

import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnumTypeDefinition extends AbstractNode<EnumTypeDefinition> implements TypeDefinition<EnumTypeDefinition>, DirectivesContainer<EnumTypeDefinition> {
    private final String name;
    private Description description;
    private final List<EnumValueDefinition> enumValueDefinitions;
    private final List<Directive> directives;

    public EnumTypeDefinition(String name) {
        this(name, null);
    }

    public EnumTypeDefinition(String name, List<Directive> directives) {
        this(name, new ArrayList<>(), directives);
    }

    public EnumTypeDefinition(String name, List<EnumValueDefinition> enumValueDefinitions, List<Directive> directives) {
        this.name = name;
        this.directives = (null == directives) ? new ArrayList<>() : directives;
        this.enumValueDefinitions = enumValueDefinitions;
    }

    public List<EnumValueDefinition> getEnumValueDefinitions() {
        return enumValueDefinitions;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives;
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

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(enumValueDefinitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumTypeDefinition that = (EnumTypeDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public EnumTypeDefinition deepCopy() {
        return new EnumTypeDefinition(name,
                deepCopy(enumValueDefinitions),
                deepCopy(directives)
        );
    }

    @Override
    public String toString() {
        return "EnumTypeDefinition{" +
                "name='" + name + '\'' +
                ", enumValueDefinitions=" + enumValueDefinitions +
                ", directives=" + directives +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitEnumTypeDefinition(this, context);
    }

    public static Builder newEnumTypeDefinition() {
        return new Builder();
    }


    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<EnumValueDefinition> enumValueDefinitions;
        private List<Directive> directives;

        private Builder() {
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

        public Builder description(Description description) {
            this.description = description;
            return this;
        }

        public Builder enumValueDefinitions(List<EnumValueDefinition> enumValueDefinitions) {
            this.enumValueDefinitions = enumValueDefinitions;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public EnumTypeDefinition build() {
            EnumTypeDefinition enumTypeDefinition = new EnumTypeDefinition(name, enumValueDefinitions, directives);
            enumTypeDefinition.setSourceLocation(sourceLocation);
            enumTypeDefinition.setComments(comments);
            enumTypeDefinition.setDescription(description);
            return enumTypeDefinition;
        }
    }
}
