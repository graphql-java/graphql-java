package graphql.validation;


import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValidationContext {

    private final GraphQLSchema schema;
    private final Document document;
    private Map<String, FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<>();

    private final List<ValidationError> validationErrors = new ArrayList<>();


    private TypeInfo typeInfo;
//    _fragments: {[name: string]: FragmentDefinition};


    public ValidationContext(GraphQLSchema schema, Document document) {
        this.schema = schema;
        this.document = document;
        this.typeInfo = new TypeInfo(schema);
    }
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }


    public void addError(ValidationError validationError) {
        validationErrors.add(validationError);
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public GraphQLCompositeType getParentType() {
        return typeInfo.getParentType();
    }

    public GraphQLInputType getInputType() {
        return typeInfo.getInputType();
    }

    public GraphQLFieldDefinition getFieldDef() {
        return typeInfo.getFieldDef();
    }

    public GraphQLDirective getDirective() {
        return typeInfo.getDirective();
    }

    public GraphQLArgument getArgument() {
        return typeInfo.getArgument();
    }
}
