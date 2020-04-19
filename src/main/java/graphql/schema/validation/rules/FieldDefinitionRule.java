package graphql.schema.validation.rules;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.exception.SchemaValidationErrorType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.schema.GraphQLTypeUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FieldDefinitionRule implements SchemaValidationRule  {

    //后期不使用白名单，而是直接判断是否是内省查询
    private static final Set<String> instrospectionQuery=new HashSet<>();
    static {
        instrospectionQuery.add("IntrospectionQuery");
    }

    @Override
    public void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        GraphQLObjectType rootType = schema.getQueryType();
        if(instrospectionQuery.contains(rootType.getName())){
            return;
        }

        List<GraphQLFieldDefinition> fieldDefinitions = rootType.getFieldDefinitions();
        travalGraphQLFieldDefinition(fieldDefinitions,validationErrorCollector);
    }

    private void travalGraphQLFieldDefinition(List<GraphQLFieldDefinition> fieldDefinitions, SchemaValidationErrorCollector validationErrorCollector) {
        if(fieldDefinitions==null||fieldDefinitions.isEmpty()){
            return;
        }

        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            String fieldDefinitionName = fieldDefinition.getName();
            if(fieldDefinitionName.length()>=2&&fieldDefinitionName.startsWith("__")){
                SchemaValidationError schemaValidationError= new SchemaValidationError(SchemaValidationErrorType.FieldDefinitionError,
                        String.format("Field \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.",fieldDefinitionName));
                validationErrorCollector.addError(schemaValidationError);
            }

            GraphQLOutputType type = fieldDefinition.getType();
            GraphQLUnmodifiedType graphQLUnmodifiedType = GraphQLTypeUtil.unwrapAll(type);
            if(graphQLUnmodifiedType instanceof GraphQLObjectType){
                List<GraphQLFieldDefinition> subFieldDefinitions = ((GraphQLObjectType) graphQLUnmodifiedType).getFieldDefinitions();
                travalGraphQLFieldDefinition(subFieldDefinitions,validationErrorCollector);
            }
        }

    }
}