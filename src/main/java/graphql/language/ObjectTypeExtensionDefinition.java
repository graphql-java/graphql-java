package graphql.language;


import graphql.Internal;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@PublicApi
public class ObjectTypeExtensionDefinition extends ObjectTypeDefinition {

    @Internal
    protected ObjectTypeExtensionDefinition(String name,
                                          List<Type> implementz,
                                          List<Directive> directives,
                                          List<FieldDefinition> fieldDefinitions,
                                          Description description,
                                          SourceLocation sourceLocation,
                                          List<Comment> comments) {
        super(name, implementz, directives, fieldDefinitions,
                description, sourceLocation, comments);
    }

    /**
     * alternative to using a Builder for convenience
     */
    public ObjectTypeExtensionDefinition(String name) {
        this(name, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null, null, new ArrayList<>());
    }

    @Override
    public ObjectTypeExtensionDefinition deepCopy() {
        return new ObjectTypeExtensionDefinition(getName(),
                deepCopy(getImplements()),
                deepCopy(getDirectives()),
                deepCopy(getFieldDefinitions()),
                getDescription(),
                getSourceLocation(),
                getComments()
        );
    }


    @Override
    public String toString() {
        return "ObjectTypeExtensionDefinition{" +
                "name='" + getName() + '\'' +
                ", implements=" + getImplements() +
                ", directives=" + getDirectives() +
                ", fieldDefinitions=" + getFieldDefinitions() +
                '}';
    }

    public static Builder newObjectTypeExtensionDefinition() {
        return new Builder();
    }

    public ObjectTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = new ArrayList<>();
        private String name;
        private Description description;
        private List<Type> implementz = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();
        private List<FieldDefinition> fieldDefinitions = new ArrayList<>();

        private Builder() {
        }

        private Builder(ObjectTypeExtensionDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = existing.getComments();
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = existing.getDirectives();
            this.implementz = existing.getImplements();
            this.fieldDefinitions = existing.getFieldDefinitions();
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

        public Builder implementz(Type implementz) {
            this.implementz.add(implementz);
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives.add(directive);
            return this;
        }

        public Builder fieldDefinitions(List<FieldDefinition> fieldDefinitions) {
            this.fieldDefinitions = fieldDefinitions;
            return this;
        }

        public Builder fieldDefinition(FieldDefinition fieldDefinition) {
            this.fieldDefinitions.add(fieldDefinition);
            return this;
        }

        public ObjectTypeExtensionDefinition build() {
            ObjectTypeExtensionDefinition objectTypeDefinition = new ObjectTypeExtensionDefinition(name,
                    implementz,
                    directives,
                    fieldDefinitions,
                    description,
                    sourceLocation,
                    comments);
            return objectTypeDefinition;
        }
    }
}
