package graphql.schema.idl;

import graphql.PublicApi;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLOutputType;

import java.util.List;

@PublicApi
public class FieldWiringEnvironment extends WiringEnvironment {

    private final FieldDefinition fieldDefinition;
    private final TypeDefinition parentType;
    private final GraphQLOutputType fieldType;
    private final List<GraphQLDirective> directives;
    private final List<GraphQLAppliedDirective> appliedDirectives;

    FieldWiringEnvironment(TypeDefinitionRegistry registry, TypeDefinition parentType, FieldDefinition fieldDefinition, GraphQLOutputType fieldType, List<GraphQLDirective> directives, List<GraphQLAppliedDirective> appliedDirectives) {
        super(registry);
        this.fieldDefinition = fieldDefinition;
        this.parentType = parentType;
        this.fieldType = fieldType;
        this.directives = directives;
        this.appliedDirectives = appliedDirectives;
    }

    public FieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public TypeDefinition getParentType() {
        return parentType;
    }

    public GraphQLOutputType getFieldType() {
        return fieldType;
    }

    public List<GraphQLDirective> getDirectives() {
        return directives;
    }

    public List<GraphQLAppliedDirective> getAppliedDirectives() {
        return appliedDirectives;
    }
}