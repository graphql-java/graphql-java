package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ObjectTypeDefinition extends AbstractNode<ObjectTypeDefinition> implements TypeDefinition<ObjectTypeDefinition>, DirectivesContainer<ObjectTypeDefinition> {
    private String name;
    private Description description;
    private final List<Type> implementz;
    private final List<Directive> directives;
    private final List<FieldDefinition> fieldDefinitions;

    public ObjectTypeDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public ObjectTypeDefinition(String name, List<Type> implementz, List<Directive> directives, List<FieldDefinition> fieldDefinitions) {
        this.name = name;
        this.implementz = implementz;
        this.directives = directives;
        this.fieldDefinitions = fieldDefinitions;
    }

    public List<Type> getImplements() {
        return implementz;
    }

    @Override
    public List<Directive> getDirectives() {
        return directives;
    }

    public List<FieldDefinition> getFieldDefinitions() {
        return fieldDefinitions;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        result.addAll(implementz);
        result.addAll(directives);
        result.addAll(fieldDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectTypeDefinition that = (ObjectTypeDefinition) o;
        return NodeUtil.isEqualTo(this.name, that.name);
    }

    @Override
    public ObjectTypeDefinition deepCopy() {
        return new ObjectTypeDefinition(name,
                deepCopy(implementz),
                deepCopy(directives),
                deepCopy(fieldDefinitions)
        );
    }

    @Override
    public String toString() {
        return "ObjectTypeDefinition{" +
                "name='" + name + '\'' +
                ", implements=" + implementz +
                ", directives=" + directives +
                ", fieldDefinitions=" + fieldDefinitions +
                '}';
    }

    @Override
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitObjectTypeDefinition(this, context);
    }

    public static Builder newObjectTypeDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<Type> implementz;
        private List<Directive> directives;
        private List<FieldDefinition> fieldDefinitions;

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

        public Builder implementz(List<Type> implementz) {
            this.implementz = implementz;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder fieldDefinitions(List<FieldDefinition> fieldDefinitions) {
            this.fieldDefinitions = fieldDefinitions;
            return this;
        }

        public ObjectTypeDefinition build() {
            ObjectTypeDefinition objectTypeDefinition = new ObjectTypeDefinition(name, implementz, directives, fieldDefinitions);
            objectTypeDefinition.setSourceLocation(sourceLocation);
            objectTypeDefinition.setComments(comments);
            objectTypeDefinition.setDescription(description);
            return objectTypeDefinition;
        }
    }
}
