package graphql.validation;


import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.schema.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class ValidationContext {

    private final GraphQLSchema schema;
    private final Document document;
    private Map<String, FragmentDefinition> fragmentDefinitionMap = new LinkedHashMap<>();


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

    public GraphQLSchema getSchema() {
        return schema;
    }

    public Document getDocument() {
        return document;
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

    public GraphQLOutputType getType() {
        return typeInfo.getType();
    }
}
