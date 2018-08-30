package graphql.language;

import graphql.Internal;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class UnionTypeExtensionDefinition extends UnionTypeDefinition {

    @Internal
    protected UnionTypeExtensionDefinition(String name,
                                         List<Directive> directives,
                                         List<Type> memberTypes,
                                         Description description,
                                         SourceLocation sourceLocation,
                                         List<Comment> comments) {
        super(name,
                directives,
                memberTypes,
                description,
                sourceLocation,
                comments);
    }

    @Override
    public UnionTypeExtensionDefinition deepCopy() {
        return new UnionTypeExtensionDefinition(getName(),
                deepCopy(getDirectives()),
                deepCopy(getMemberTypes()),
                getDescription(),
                getSourceLocation(),
                getComments());
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

    public UnionTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<Directive> directives = new ArrayList<>();
        private List<Type> memberTypes = new ArrayList<>();

        private Builder() {
        }


        private Builder(UnionTypeExtensionDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
            this.memberTypes = existing.getMemberTypes();
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
            UnionTypeExtensionDefinition unionTypeDefinition = new UnionTypeExtensionDefinition(name,
                    directives,
                    memberTypes,
                    description,
                    sourceLocation,
                    comments
            );
            return unionTypeDefinition;
        }
    }
}
