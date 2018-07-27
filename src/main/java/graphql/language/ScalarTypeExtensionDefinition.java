package graphql.language;

import graphql.Internal;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class ScalarTypeExtensionDefinition extends ScalarTypeDefinition {

    @Internal
    protected ScalarTypeExtensionDefinition(String name,
                                          List<Directive> directives,
                                          Description description,
                                          SourceLocation sourceLocation,
                                          List<Comment> comments) {
        super(name, directives, description, sourceLocation, comments);
    }

    @Override
    public ScalarTypeExtensionDefinition deepCopy() {
        return new ScalarTypeExtensionDefinition(getName(), deepCopy(getDirectives()), getDescription(), getSourceLocation(), getComments());
    }

    @Override
    public String toString() {
        return "ScalarTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", directives=" + getDirectives() +
                '}';

    }

    public static Builder newScalarTypeExtensionDefinition() {
        return new Builder();
    }

    public ScalarTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
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

        private Builder() {
        }


        private Builder(ScalarTypeExtensionDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
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

        public ScalarTypeExtensionDefinition build() {
            ScalarTypeExtensionDefinition scalarTypeDefinition = new ScalarTypeExtensionDefinition(name,
                    directives,
                    description,
                    sourceLocation,
                    comments);
            return scalarTypeDefinition;
        }
    }
}
