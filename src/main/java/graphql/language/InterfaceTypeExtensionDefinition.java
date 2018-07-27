package graphql.language;

import graphql.Internal;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class InterfaceTypeExtensionDefinition extends InterfaceTypeDefinition {

    @Internal
    protected InterfaceTypeExtensionDefinition(String name,
                                     List<FieldDefinition> definitions,
                                     List<Directive> directives,
                                     Description description,
                                     SourceLocation sourceLocation,
                                     List<Comment> comments) {
        super(name, definitions, directives, description, sourceLocation, comments);
    }

    @Override
    public InterfaceTypeExtensionDefinition deepCopy() {
        return new InterfaceTypeExtensionDefinition(getName(),
                deepCopy(getFieldDefinitions()),
                deepCopy(getDirectives()),
                getDescription(),
                getSourceLocation(),
                getComments()
        );
    }

    @Override
    public String toString() {
        return "InterfaceTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", fieldDefinitions=" + getFieldDefinitions() +
                ", directives=" + getDirectives() +
                '}';

    }

    public static Builder newInterfaceTypeExtensionDefinition() {
        return new Builder();
    }

    public InterfaceTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<FieldDefinition> definitions = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();

        private Builder() {
        }

        private Builder(InterfaceTypeExtensionDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
            this.definitions = existing.getFieldDefinitions();
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

        public Builder definitions(List<FieldDefinition> definitions) {
            this.definitions = definitions;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public InterfaceTypeExtensionDefinition build() {
            InterfaceTypeExtensionDefinition interfaceTypeDefinition = new InterfaceTypeExtensionDefinition(name,
                    definitions,
                    directives,
                    description,
                    sourceLocation,
                    comments);
            return interfaceTypeDefinition;
        }
    }
}
