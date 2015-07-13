package graphql.validation;


import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class ValidationContext {

    private GraphQLSchema schema;
    private Document document;
    private Map<String,FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<>();

    private TypeInfo typeInfo;
//    _fragments: {[name: string]: FragmentDefinition};


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

    public GraphQLFieldArgument getArgument() {
        return typeInfo.getArgument();
    }
}
