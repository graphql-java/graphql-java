package graphql.language;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

@PublicApi
@NullMarked
public class UnionTypeExtensionDefinition extends UnionTypeDefinition implements SDLExtensionDefinition {

    @Internal
    protected UnionTypeExtensionDefinition(String name,
                                           List<Directive> directives,
                                           List<Type> memberTypes,
                                           @Nullable Description description,
                                           @Nullable SourceLocation sourceLocation,
                                           List<Comment> comments,
                                           IgnoredChars ignoredChars,
                                           Map<String, String> additionalData) {
        super(name,
                directives,
                memberTypes,
                description,
                sourceLocation,
                comments,
                ignoredChars,
                additionalData);
    }

    @Override
    public UnionTypeExtensionDefinition deepCopy() {
        return new UnionTypeExtensionDefinition(getName(),
                assertNotNull(deepCopy(getDirectives())),
                assertNotNull(deepCopy(getMemberTypes())),
                getDescription(),
                getSourceLocation(),
                getComments(),
                getIgnoredChars(), getAdditionalData());
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

    @Override
    public UnionTypeExtensionDefinition withNewChildren(NodeChildrenContainer newChildren) {
        return transformExtension(builder -> builder
                .directives(newChildren.getChildren(CHILD_DIRECTIVES))
                .memberTypes(newChildren.getChildren(CHILD_MEMBER_TYPES))
        );
    }

    public UnionTypeExtensionDefinition transformExtension(Consumer<Builder> builderConsumer) {
        Builder builder = new Builder(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @NullUnmarked
    public static final class Builder implements NodeDirectivesBuilder {
        private SourceLocation sourceLocation;
        private ImmutableList<Comment> comments = emptyList();
        private String name;
        private Description description;
        private ImmutableList<Directive> directives = emptyList();
        private ImmutableList<Type> memberTypes = emptyList();
        private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
        private Map<String, String> additionalData = new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(UnionTypeDefinition existing) {
            this.sourceLocation = existing.getSourceLocation();
            this.comments = ImmutableList.copyOf(existing.getComments());
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.directives = ImmutableList.copyOf(existing.getDirectives());
            this.memberTypes = ImmutableList.copyOf(existing.getMemberTypes());
            this.ignoredChars = existing.getIgnoredChars();
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = ImmutableList.copyOf(comments);
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

        @Override
        public Builder directives(List<Directive> directives) {
            this.directives = ImmutableList.copyOf(directives);
            return this;
        }

        public Builder directive(Directive directive) {
            this.directives = ImmutableKit.addToList(directives, directive);
            return this;
        }

        public Builder memberTypes(List<Type> memberTypes) {
            this.memberTypes = ImmutableList.copyOf(memberTypes);
            return this;
        }

        public Builder memberType(Type memberType) {
            this.memberTypes = ImmutableKit.addToList(memberTypes, memberType);
            return this;
        }

        public Builder ignoredChars(IgnoredChars ignoredChars) {
            this.ignoredChars = ignoredChars;
            return this;
        }

        public Builder additionalData(Map<String, String> additionalData) {
            this.additionalData = assertNotNull(additionalData);
            return this;
        }

        public Builder additionalData(String key, String value) {
            this.additionalData.put(key, value);
            return this;
        }


        public UnionTypeExtensionDefinition build() {
            return new UnionTypeExtensionDefinition(name,
                    directives,
                    memberTypes,
                    description,
                    sourceLocation,
                    comments,
                    ignoredChars,
                    additionalData);
        }
    }
}
