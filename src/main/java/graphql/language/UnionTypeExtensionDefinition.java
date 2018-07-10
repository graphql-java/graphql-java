package graphql.language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnionTypeExtensionDefinition extends UnionTypeDefinition {

    public UnionTypeExtensionDefinition(String name) {
        super(name);
    }

    public UnionTypeExtensionDefinition(String name, List<Directive> directives, List<Type> memberTypes) {
        super(name, directives, memberTypes);
    }

    @Override
    public UnionTypeExtensionDefinition deepCopy() {
        return new UnionTypeExtensionDefinition(getName(),
                deepCopy(getDirectives()),
                deepCopy(getMemberTypes())
        );
    }

    @Override
    public String toString() {
        return "UnionTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                "directives=" + getDirectives() +
                ", memberTypes=" + getMemberTypes() +
                '}';
    }

    public static Builder newUnionTypeExtensionDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();
        private List<Type> memberTypes = new ArrayList<>();

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

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder memberTypes(List<Type> memberTypes) {
            this.memberTypes = memberTypes;
            return this;
        }

        public UnionTypeExtensionDefinition build() {
            UnionTypeExtensionDefinition unionTypeDefinition = new UnionTypeExtensionDefinition(name, directives, memberTypes);
            unionTypeDefinition.setSourceLocation(sourceLocation);
            unionTypeDefinition.setComments(comments);
            unionTypeDefinition.setDescription(description);
            return unionTypeDefinition;
        }
    }
}
