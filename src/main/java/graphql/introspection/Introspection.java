package graphql.introspection;


import graphql.Assert;
import graphql.language.AstPrinter;
import graphql.language.AstValueHelper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import graphql.schema.SchemaUtil;

import java.util.ArrayList;
import java.util.List;

import static graphql.Assert.assertTrue;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;

public class Introspection {

    public enum TypeKind {
        SCALAR,
        OBJECT,
        INTERFACE,
        UNION,
        ENUM,
        INPUT_OBJECT,
        LIST,
        NON_NULL
    }

    public static final GraphQLEnumType __TypeKind = GraphQLEnumType.newEnum()
            .name("__TypeKind")
            .description("An enum describing what kind of type a given __Type is")
            .value("SCALAR", TypeKind.SCALAR, "Indicates this type is a scalar.")
            .value("OBJECT", TypeKind.OBJECT, "Indicates this type is an object. `fields` and `interfaces` are valid fields.")
            .value("INTERFACE", TypeKind.INTERFACE, "Indicates this type is an interface. `fields` and `possibleTypes` are valid fields.")
            .value("UNION", TypeKind.UNION, "Indicates this type is a union. `possibleTypes` is a valid field.")
            .value("ENUM", TypeKind.ENUM, "Indicates this type is an enum. `enumValues` is a valid field.")
            .value("INPUT_OBJECT", TypeKind.INPUT_OBJECT, "Indicates this type is an input object. `inputFields` is a valid field.")
            .value("LIST", TypeKind.LIST, "Indicates this type is a list. `ofType` is a valid field.")
            .value("NON_NULL", TypeKind.NON_NULL, "Indicates this type is a non-null. `ofType` is a valid field.")
            .build();

    public static final DataFetcher kindDataFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLScalarType) {
            return TypeKind.SCALAR;
        } else if (type instanceof GraphQLObjectType) {
            return TypeKind.OBJECT;
        } else if (type instanceof GraphQLInterfaceType) {
            return TypeKind.INTERFACE;
        } else if (type instanceof GraphQLUnionType) {
            return TypeKind.UNION;
        } else if (type instanceof GraphQLEnumType) {
            return TypeKind.ENUM;
        } else if (type instanceof GraphQLInputObjectType) {
            return TypeKind.INPUT_OBJECT;
        } else if (type instanceof GraphQLList) {
            return TypeKind.LIST;
        } else if (type instanceof GraphQLNonNull) {
            return TypeKind.NON_NULL;
        } else {
            return Assert.assertShouldNeverHappen("Unknown kind of type: %s", type);
        }
    };

    public static final GraphQLObjectType __InputValue = newObject()
            .name("__InputValue")
            .field(newFieldDefinition()
                    .name("name")
                    .type(nonNull(GraphQLString)))
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("type")
                    .type(nonNull(new GraphQLTypeReference("__Type"))))
            .field(newFieldDefinition()
                    .name("defaultValue")
                    .type(GraphQLString)
                    .dataFetcher(environment -> {
                        if (environment.getSource() instanceof GraphQLArgument) {
                            GraphQLArgument inputField = environment.getSource();
                            return inputField.getDefaultValue() != null ? print(inputField.getDefaultValue(), inputField.getType()) : null;
                        } else if (environment.getSource() instanceof GraphQLInputObjectField) {
                            GraphQLInputObjectField inputField = environment.getSource();
                            return inputField.getDefaultValue() != null ? print(inputField.getDefaultValue(), inputField.getType()) : null;
                        }
                        return null;
                    }))
            .build();

    private static String print(Object value, GraphQLInputType type) {
        return AstPrinter.printAst(AstValueHelper.astFromValue(value, type));
    }


    public static final GraphQLObjectType __Field = newObject()
            .name("__Field")
            .field(newFieldDefinition()
                    .name("name")
                    .type(nonNull(GraphQLString)))
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("args")
                    .type(nonNull(list(nonNull(__InputValue))))
                    .dataFetcher(environment -> {
                        Object type = environment.getSource();
                        return ((GraphQLFieldDefinition) type).getArguments();
                    }))
            .field(newFieldDefinition()
                    .name("type")
                    .type(nonNull(new GraphQLTypeReference("__Type"))))
            .field(newFieldDefinition()
                    .name("isDeprecated")
                    .type(nonNull(GraphQLBoolean))
                    .dataFetcher(environment -> {
                        Object type = environment.getSource();
                        return ((GraphQLFieldDefinition) type).isDeprecated();
                    }))
            .field(newFieldDefinition()
                    .name("deprecationReason")
                    .type(GraphQLString))
            .build();


    public static final GraphQLObjectType __EnumValue = newObject()
            .name("__EnumValue")
            .field(newFieldDefinition()
                    .name("name")
                    .type(nonNull(GraphQLString)))
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("isDeprecated")
                    .type(nonNull(GraphQLBoolean))
                    .dataFetcher(environment -> {
                        GraphQLEnumValueDefinition enumValue = environment.getSource();
                        return enumValue.isDeprecated();
                    }))
            .field(newFieldDefinition()
                    .name("deprecationReason")
                    .type(GraphQLString))
            .build();

    public static final DataFetcher fieldsFetcher = environment -> {
        Object type = environment.getSource();
        Boolean includeDeprecated = environment.getArgument("includeDeprecated");
        if (type instanceof GraphQLFieldsContainer) {
            GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) type;
            List<GraphQLFieldDefinition> fieldDefinitions = environment
                    .getGraphQLSchema()
                    .getFieldVisibility()
                    .getFieldDefinitions(fieldsContainer);
            if (includeDeprecated) return fieldDefinitions;
            List<GraphQLFieldDefinition> filtered = new ArrayList<>(fieldDefinitions);
            for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
                if (fieldDefinition.isDeprecated()) filtered.remove(fieldDefinition);
            }
            return filtered;
        }
        return null;
    };


    public static final DataFetcher interfacesFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) type).getInterfaces();
        }
        return null;
    };

    public static final DataFetcher possibleTypesFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLInterfaceType) {
            return new SchemaUtil().findImplementations(environment.getGraphQLSchema(), (GraphQLInterfaceType) type);
        }
        if (type instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) type).getTypes();
        }
        return null;
    };

    public static final DataFetcher enumValuesTypesFetcher = environment -> {
        Object type = environment.getSource();
        Boolean includeDeprecated = environment.getArgument("includeDeprecated");
        if (type instanceof GraphQLEnumType) {
            List<GraphQLEnumValueDefinition> values = ((GraphQLEnumType) type).getValues();
            if (includeDeprecated) return values;
            List<GraphQLEnumValueDefinition> filtered = new ArrayList<>(values);
            for (GraphQLEnumValueDefinition valueDefinition : values) {
                if (valueDefinition.isDeprecated()) filtered.remove(valueDefinition);
            }
            return filtered;
        }
        return null;
    };

    public static final DataFetcher inputFieldsFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLInputObjectType) {
            return ((GraphQLInputObjectType) type).getFields();
        }
        return null;
    };

    public static final DataFetcher OfTypeFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLList) {
            return ((GraphQLList) type).getWrappedType();
        }
        if (type instanceof GraphQLNonNull) {
            return ((GraphQLNonNull) type).getWrappedType();
        }
        return null;
    };


    public static final GraphQLObjectType __Type = newObject()
            .name("__Type")
            .field(newFieldDefinition()
                    .name("kind")
                    .type(nonNull(__TypeKind))
                    .dataFetcher(kindDataFetcher))
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("fields")
                    .type(list(nonNull(__Field)))
                    .argument(newArgument()
                            .name("includeDeprecated")
                            .type(GraphQLBoolean)
                            .defaultValue(false))
                    .dataFetcher(fieldsFetcher))
            .field(newFieldDefinition()
                    .name("interfaces")
                    .type(list(nonNull(new GraphQLTypeReference("__Type"))))
                    .dataFetcher(interfacesFetcher))
            .field(newFieldDefinition()
                    .name("possibleTypes")
                    .type(list(nonNull(new GraphQLTypeReference("__Type"))))
                    .dataFetcher(possibleTypesFetcher))
            .field(newFieldDefinition()
                    .name("enumValues")
                    .type(list(nonNull(__EnumValue)))
                    .argument(newArgument()
                            .name("includeDeprecated")
                            .type(GraphQLBoolean)
                            .defaultValue(false))
                    .dataFetcher(enumValuesTypesFetcher))
            .field(newFieldDefinition()
                    .name("inputFields")
                    .type(list(nonNull(__InputValue)))
                    .dataFetcher(inputFieldsFetcher))
            .field(newFieldDefinition()
                    .name("ofType")
                    .type(new GraphQLTypeReference("__Type"))
                    .dataFetcher(OfTypeFetcher))
            .build();

    public enum DirectiveLocation {
        QUERY,
        MUTATION,
        FIELD,
        FRAGMENT_DEFINITION,
        FRAGMENT_SPREAD,
        INLINE_FRAGMENT,
        //
        // schema IDL places
        //
        SCHEMA,
        SCALAR,
        OBJECT,
        FIELD_DEFINITION,
        ARGUMENT_DEFINITION,
        INTERFACE,
        UNION,
        ENUM,
        ENUM_VALUE,
        INPUT_OBJECT,
        INPUT_FIELD_DEFINITION
    }

    public static final GraphQLEnumType __DirectiveLocation = GraphQLEnumType.newEnum()
            .name("__DirectiveLocation")
            .description("An enum describing valid locations where a directive can be placed")
            .value("QUERY", DirectiveLocation.QUERY, "Indicates the directive is valid on queries.")
            .value("MUTATION", DirectiveLocation.MUTATION, "Indicates the directive is valid on mutations.")
            .value("FIELD", DirectiveLocation.FIELD, "Indicates the directive is valid on fields.")
            .value("FRAGMENT_DEFINITION", DirectiveLocation.FRAGMENT_DEFINITION, "Indicates the directive is valid on fragment definitions.")
            .value("FRAGMENT_SPREAD", DirectiveLocation.FRAGMENT_SPREAD, "Indicates the directive is valid on fragment spreads.")
            .value("INLINE_FRAGMENT", DirectiveLocation.INLINE_FRAGMENT, "Indicates the directive is valid on inline fragments.")
            //
            // from schema IDL PR  https://github.com/facebook/graphql/pull/90
            //
            .value("SCHEMA", DirectiveLocation.SCHEMA, "Indicates the directive is valid on a schema IDL definition.")
            .value("SCALAR", DirectiveLocation.SCALAR, "Indicates the directive is valid on a scalar IDL definition.")
            .value("OBJECT", DirectiveLocation.OBJECT, "Indicates the directive is valid on an object IDL definition.")
            .value("FIELD_DEFINITION", DirectiveLocation.FIELD_DEFINITION, "Indicates the directive is valid on a field IDL definition.")
            .value("ARGUMENT_DEFINITION", DirectiveLocation.ARGUMENT_DEFINITION, "Indicates the directive is valid on a field argument IDL definition.")
            .value("INTERFACE", DirectiveLocation.INTERFACE, "Indicates the directive is valid on an interface IDL definition.")
            .value("UNION", DirectiveLocation.UNION, "Indicates the directive is valid on an union IDL definition.")
            .value("ENUM", DirectiveLocation.ENUM, "Indicates the directive is valid on an enum IDL definition.")
            .value("INPUT_OBJECT", DirectiveLocation.INPUT_OBJECT, "Indicates the directive is valid on an input object IDL definition.")
            .value("INPUT_FIELD_DEFINITION", DirectiveLocation.INPUT_FIELD_DEFINITION, "Indicates the directive is valid on an input object field IDL definition.")

            .build();

    @SuppressWarnings("deprecation") // because graphql spec still has the deprecated fields
    public static final GraphQLObjectType __Directive = newObject()
            .name("__Directive")
            .field(newFieldDefinition()
                    .name("name")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("locations")
                    .type(list(nonNull(__DirectiveLocation)))
                    .dataFetcher(environment -> {
                        GraphQLDirective directive = environment.getSource();
                        return new ArrayList<>(directive.validLocations());
                    }))
            .field(newFieldDefinition()
                    .name("args")
                    .type(nonNull(list(nonNull(__InputValue))))
                    .dataFetcher(environment -> {
                        GraphQLDirective directive = environment.getSource();
                        return directive.getArguments();
                    }))
            .field(newFieldDefinition()
                    .name("onOperation")
                    .type(GraphQLBoolean)
                    .deprecate("Use `locations`.")
                    .dataFetcher(environment -> {
                        GraphQLDirective directive = environment.getSource();
                        return directive.isOnOperation();
                    }))
            .field(newFieldDefinition()
                    .name("onFragment")
                    .type(GraphQLBoolean)
                    .deprecate("Use `locations`.")
                    .dataFetcher(environment -> {
                        GraphQLDirective directive = environment.getSource();
                        return directive.isOnFragment() ||
                                (directive.validLocations().contains(DirectiveLocation.INLINE_FRAGMENT)
                                        && directive.validLocations().contains(DirectiveLocation.FRAGMENT_SPREAD));
                    }))
            .field(newFieldDefinition()
                    .name("onField")
                    .type(GraphQLBoolean)
                    .deprecate("Use `locations`.")
                    .dataFetcher(environment -> {
                        GraphQLDirective directive = environment.getSource();
                        return directive.isOnField() ||
                                directive.validLocations().contains(DirectiveLocation.FIELD);
                    }))
            .build();

    public static final GraphQLObjectType __Schema = newObject()
            .name("__Schema")
            .description("A GraphQL Introspection defines the capabilities" +
                    " of a GraphQL server. It exposes all available types and directives on " +
                    "the server, the entry points for query, mutation, and subscription operations.")
            .field(newFieldDefinition()
                    .name("types")
                    .description("A list of all types supported by this server.")
                    .type(nonNull(list(nonNull(__Type))))
                    .dataFetcher(environment -> {
                        GraphQLSchema schema = environment.getSource();
                        return schema.getAllTypesAsList();
                    }))
            .field(newFieldDefinition()
                    .name("queryType")
                    .description("The type that query operations will be rooted at.")
                    .type(nonNull(__Type))
                    .dataFetcher(environment -> {
                        GraphQLSchema schema = environment.getSource();
                        return schema.getQueryType();
                    }))
            .field(newFieldDefinition()
                    .name("mutationType")
                    .description("If this server supports mutation, the type that mutation operations will be rooted at.")
                    .type(__Type)
                    .dataFetcher(environment -> {
                        GraphQLSchema schema = environment.getSource();
                        return schema.getMutationType();
                    }))
            .field(newFieldDefinition()
                    .name("directives")
                    .description("'A list of all directives supported by this server.")
                    .type(nonNull(list(nonNull(__Directive))))
                    .dataFetcher(environment -> environment.getGraphQLSchema().getDirectives()))
            .field(newFieldDefinition()
                    .name("subscriptionType")
                    .description("'If this server support subscription, the type that subscription operations will be rooted at.")
                    .type(__Type)
                    .dataFetcher(environment -> {
                        GraphQLSchema schema = environment.getSource();
                        return schema.getSubscriptionType();
                    }))
            .build();


    public static final GraphQLFieldDefinition SchemaMetaFieldDef = newFieldDefinition()
            .name("__schema")
            .type(nonNull(__Schema))
            .description("Access the current type schema of this server.")
            .dataFetcher(DataFetchingEnvironment::getGraphQLSchema).build();

    public static final GraphQLFieldDefinition TypeMetaFieldDef = newFieldDefinition()
            .name("__type")
            .type(__Type)
            .description("Request the type information of a single type.")
            .argument(newArgument()
                    .name("name")
                    .type(nonNull(GraphQLString)))
            .dataFetcher(environment -> {
                String name = environment.getArgument("name");
                return environment.getGraphQLSchema().getType(name);
            }).build();

    public static final GraphQLFieldDefinition TypeNameMetaFieldDef = newFieldDefinition()
            .name("__typename")
            .type(nonNull(GraphQLString))
            .description("The name of the current Object type at runtime.")
            .dataFetcher(environment -> environment.getParentType().getName())
            .build();


    static {
        // make sure all TypeReferences are resolved
        GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject()
                        .name("IntrospectionQuery")
                        .field(SchemaMetaFieldDef)
                        .field(TypeMetaFieldDef)
                        .field(TypeNameMetaFieldDef)
                        .build())
                .build();
    }

    /**
     * This will look up a field definition by name, and understand that fields like __typename and __schema are special
     * and take precedence in field resolution
     *
     * @param schema     the schema to use
     * @param parentType the type of the parent object
     * @param fieldName  the field to look up
     *
     * @return a field definition otherwise throws an assertion exception if its null
     */
    public static GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLCompositeType parentType, String fieldName) {

        if (schema.getQueryType() == parentType) {
            if (fieldName.equals(SchemaMetaFieldDef.getName())) {
                return SchemaMetaFieldDef;
            }
            if (fieldName.equals(TypeMetaFieldDef.getName())) {
                return TypeMetaFieldDef;
            }
        }
        if (fieldName.equals(TypeNameMetaFieldDef.getName())) {
            return TypeNameMetaFieldDef;
        }

        assertTrue(parentType instanceof GraphQLFieldsContainer, "should not happen : parent type must be an object or interface %s", parentType);
        GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) parentType;
        GraphQLFieldDefinition fieldDefinition = schema.getFieldVisibility().getFieldDefinition(fieldsContainer, fieldName);
        Assert.assertTrue(fieldDefinition != null, "Unknown field '%s'", fieldName);
        return fieldDefinition;
    }
}
