package graphql.introspection;


import com.google.common.collect.ImmutableSet;
import graphql.Assert;
import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.execution.ExecutionContext;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.ValuesResolver;
import graphql.language.AstPrinter;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
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
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static graphql.Assert.assertTrue;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.FieldCoordinates.systemCoordinates;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAllAs;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

/**
 * GraphQl has a unique capability called <a href="https://spec.graphql.org/October2021/#sec-Introspection">Introspection</a> that allow
 * consumers to inspect the system and discover the fields and types available and makes the system self documented.
 * <p>
 * Some security recommendations such as <a href="https://owasp.org/www-chapter-vancouver/assets/presentations/2020-06_GraphQL_Security.pdf">OWASP</a>
 * recommend that introspection be disabled in production.  The {@link Introspection#enabledJvmWide(boolean)} method can be used to disable
 * introspection for the whole JVM or you can place {@link Introspection#INTROSPECTION_DISABLED} into the {@link GraphQLContext} of a request
 * to disable introspection for that request.
 */
@PublicApi
public class Introspection {


    /**
     * Placing a boolean value under this key in the per request {@link GraphQLContext} will enable
     * or disable Introspection on that request.
     */
    public static final String INTROSPECTION_DISABLED = "INTROSPECTION_DISABLED";
    private static final AtomicBoolean INTROSPECTION_ENABLED_STATE = new AtomicBoolean(true);

    /**
     * This static method will enable / disable Introspection at a JVM wide level.
     *
     * @param enabled the flag indicating the desired enabled state
     *
     * @return the previous state of enablement
     */
    public static boolean enabledJvmWide(boolean enabled) {
        return INTROSPECTION_ENABLED_STATE.getAndSet(enabled);
    }

    /**
     * @return true if Introspection is enabled at a JVM wide level or false otherwise
     */
    public static boolean isEnabledJvmWide() {
        return INTROSPECTION_ENABLED_STATE.get();
    }

    /**
     * This will look in to the field selection set and see if there are introspection fields,
     * and if there is,it checks if introspection should run, and if not it will return an errored {@link ExecutionResult}
     * that can be returned to the user.
     *
     * @param mergedSelectionSet the fields to be executed
     * @param executionContext   the execution context in play
     *
     * @return an optional error result
     */
    public static Optional<ExecutionResult> isIntrospectionSensible(MergedSelectionSet mergedSelectionSet, ExecutionContext executionContext) {
        GraphQLContext graphQLContext = executionContext.getGraphQLContext();

        for (String key : mergedSelectionSet.getKeys()) {
            String fieldName = mergedSelectionSet.getSubField(key).getName();
            if (fieldName.equals(SchemaMetaFieldDef.getName())
                    || fieldName.equals(TypeMetaFieldDef.getName())) {
                if (!isIntrospectionEnabled(graphQLContext)) {
                    return mkDisabledError(mergedSelectionSet.getSubField(key));
                }
                break;
            }
        }
        return Optional.empty();
    }

    @NonNull
    private static Optional<ExecutionResult> mkDisabledError(MergedField schemaField) {
        IntrospectionDisabledError error = new IntrospectionDisabledError(schemaField.getSingleField().getSourceLocation());
        return Optional.of(ExecutionResult.newExecutionResult().addError(error).build());
    }

    private static boolean isIntrospectionEnabled(GraphQLContext graphQlContext) {
        if (!isEnabledJvmWide()) {
            return false;
        }
        return !graphQlContext.getBoolean(INTROSPECTION_DISABLED, false);
    }

    private static final Map<FieldCoordinates, IntrospectionDataFetcher<?>> introspectionDataFetchers = new LinkedHashMap<>();

    private static void register(GraphQLFieldsContainer parentType, String fieldName, IntrospectionDataFetcher<?> introspectionDataFetcher) {
        introspectionDataFetchers.put(coordinates(parentType.getName(), fieldName), introspectionDataFetcher);
    }

    /**
     * To help runtimes such as graalvm, we make sure we have an explicit data fetchers rather then use {@link graphql.schema.PropertyDataFetcher}
     * and its reflective mechanisms.  This is not reflective because we have the class
     *
     * @param parentType  the containing parent type
     * @param fieldName   the field name
     * @param targetClass the target class of the getter
     * @param getter      the function to call to get a value of T
     * @param <T>         for two
     */
    private static <T> void register(GraphQLFieldsContainer parentType, String fieldName, Class<T> targetClass, Function<T, Object> getter) {
        IntrospectionDataFetcher<?> dataFetcher = env -> {
            Object source = env.getSource();
            if (targetClass.isInstance(source)) {
                return getter.apply(targetClass.cast(source));
            }
            return null;
        };
        introspectionDataFetchers.put(coordinates(parentType.getName(), fieldName), dataFetcher);
    }

    @Internal
    public static void addCodeForIntrospectionTypes(GraphQLCodeRegistry.Builder codeRegistry) {
        // place the system __ fields into the mix.  They have no parent types
        codeRegistry.dataFetcherIfAbsent(systemCoordinates(SchemaMetaFieldDef.getName()), SchemaMetaFieldDefDataFetcher);
        codeRegistry.dataFetcherIfAbsent(systemCoordinates(TypeNameMetaFieldDef.getName()), TypeNameMetaFieldDefDataFetcher);
        codeRegistry.dataFetcherIfAbsent(systemCoordinates(TypeMetaFieldDef.getName()), TypeMetaFieldDefDataFetcher);

        introspectionDataFetchers.forEach(codeRegistry::dataFetcherIfAbsent);
    }


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
            .value("SCALAR", TypeKind.SCALAR, "Indicates this type is a scalar. `specifiedByURL` is a valid field")
            .value("OBJECT", TypeKind.OBJECT, "Indicates this type is an object. `fields` and `interfaces` are valid fields.")
            .value("INTERFACE", TypeKind.INTERFACE, "Indicates this type is an interface. `fields` and `possibleTypes` are valid fields.")
            .value("UNION", TypeKind.UNION, "Indicates this type is a union. `possibleTypes` is a valid field.")
            .value("ENUM", TypeKind.ENUM, "Indicates this type is an enum. `enumValues` is a valid field.")
            .value("INPUT_OBJECT", TypeKind.INPUT_OBJECT, "Indicates this type is an input object. `inputFields` is a valid field.")
            .value("LIST", TypeKind.LIST, "Indicates this type is a list. `ofType` is a valid field.")
            .value("NON_NULL", TypeKind.NON_NULL, "Indicates this type is a non-null. `ofType` is a valid field.")
            .build();

    private static final IntrospectionDataFetcher<?> kindDataFetcher = environment -> {
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
    private static final IntrospectionDataFetcher<?> nameDataFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLNamedSchemaElement) {
            return ((GraphQLNamedSchemaElement) type).getName();
        }
        return null;
    };
    private static final IntrospectionDataFetcher<?> descriptionDataFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLNamedSchemaElement) {
            return ((GraphQLNamedSchemaElement) type).getDescription();
        }
        return null;
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
                    .type(nonNull(typeRef("__Type"))))
            .field(newFieldDefinition()
                    .name("defaultValue")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("isDeprecated")
                    .type(GraphQLBoolean))
            .field(newFieldDefinition()
                    .name("deprecationReason")
                    .type(GraphQLString))
            .build();

    static {
        IntrospectionDataFetcher<?> defaultValueDataFetcher = environment -> {
            Object type = environment.getSource();
            if (type instanceof GraphQLArgument) {
                GraphQLArgument inputField = (GraphQLArgument) type;
                return inputField.hasSetDefaultValue()
                        ? printDefaultValue(inputField.getArgumentDefaultValue(), inputField.getType(), environment.getGraphQlContext(), environment.getLocale())
                        : null;
            } else if (type instanceof GraphQLInputObjectField) {
                GraphQLInputObjectField inputField = (GraphQLInputObjectField) type;
                return inputField.hasSetDefaultValue()
                        ? printDefaultValue(inputField.getInputFieldDefaultValue(), inputField.getType(), environment.getGraphQlContext(), environment.getLocale())
                        : null;
            }
            return null;
        };
        IntrospectionDataFetcher<?> isDeprecatedDataFetcher = environment -> {
            Object type = environment.getSource();
            if (type instanceof GraphQLArgument) {
                return ((GraphQLArgument) type).isDeprecated();
            } else if (type instanceof GraphQLInputObjectField) {
                return ((GraphQLInputObjectField) type).isDeprecated();
            }
            return null;
        };
        IntrospectionDataFetcher<?> typeDataFetcher = environment -> {
            Object type = environment.getSource();
            if (type instanceof GraphQLArgument) {
                return ((GraphQLArgument) type).getType();
            } else if (type instanceof GraphQLInputObjectField) {
                return ((GraphQLInputObjectField) type).getType();
            }
            return null;
        };
        IntrospectionDataFetcher<?> deprecationReasonDataFetcher = environment -> {
            Object type = environment.getSource();
            if (type instanceof GraphQLArgument) {
                return ((GraphQLArgument) type).getDeprecationReason();
            } else if (type instanceof GraphQLInputObjectField) {
                return ((GraphQLInputObjectField) type).getDeprecationReason();
            }
            return null;
        };

        register(__InputValue, "name", nameDataFetcher);
        register(__InputValue, "description", descriptionDataFetcher);
        register(__InputValue, "type", typeDataFetcher);
        register(__InputValue, "defaultValue", defaultValueDataFetcher);
        register(__InputValue, "isDeprecated", isDeprecatedDataFetcher);
        register(__InputValue, "deprecationReason", deprecationReasonDataFetcher);
    }

    private static String printDefaultValue(InputValueWithState inputValueWithState, GraphQLInputType type, GraphQLContext graphQLContext, Locale locale) {
        return AstPrinter.printAst(ValuesResolver.valueToLiteral(inputValueWithState, type, graphQLContext, locale));
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
                    .argument(newArgument()
                            .name("includeDeprecated")
                            .type(GraphQLBoolean)
                            .defaultValueProgrammatic(false)))
            .field(newFieldDefinition()
                    .name("type")
                    .type(nonNull(typeRef("__Type"))))
            .field(newFieldDefinition()
                    .name("isDeprecated")
                    .type(nonNull(GraphQLBoolean)))
            .field(newFieldDefinition()
                    .name("deprecationReason")
                    .type(GraphQLString))
            .build();

    static {
        IntrospectionDataFetcher<?> argsDataFetcher = environment -> {
            Object type = environment.getSource();
            GraphQLFieldDefinition fieldDef = (GraphQLFieldDefinition) type;
            Boolean includeDeprecated = environment.getArgument("includeDeprecated");
            return ImmutableKit.filter(fieldDef.getArguments(),
                    arg -> includeDeprecated || !arg.isDeprecated());
        };
        register(__Field, "name", nameDataFetcher);
        register(__Field, "description", descriptionDataFetcher);
        register(__Field, "args", argsDataFetcher);
        register(__Field, "type", GraphQLFieldDefinition.class, GraphQLFieldDefinition::getType);
        register(__Field, "isDeprecated", GraphQLFieldDefinition.class, GraphQLFieldDefinition::isDeprecated);
        register(__Field, "deprecationReason", GraphQLFieldDefinition.class, GraphQLFieldDefinition::getDeprecationReason);
    }


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
                    .type(nonNull(GraphQLBoolean)))
            .field(newFieldDefinition()
                    .name("deprecationReason")
                    .type(GraphQLString))
            .build();

    static {
        register(__EnumValue, "name", nameDataFetcher);
        register(__EnumValue, "description", descriptionDataFetcher);
        register(__EnumValue, "isDeprecated", GraphQLEnumValueDefinition.class, GraphQLEnumValueDefinition::isDeprecated);
        register(__EnumValue, "deprecationReason", GraphQLEnumValueDefinition.class, GraphQLEnumValueDefinition::getDeprecationReason);
    }


    private static final IntrospectionDataFetcher<?> fieldsFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLFieldsContainer) {
            GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) type;
            Boolean includeDeprecated = environment.getArgument("includeDeprecated");
            List<GraphQLFieldDefinition> fieldDefinitions = environment
                    .getGraphQLSchema()
                    .getCodeRegistry()
                    .getFieldVisibility()
                    .getFieldDefinitions(fieldsContainer);
            if (includeDeprecated) {
                return fieldDefinitions;
            }
            return ImmutableKit.filter(fieldDefinitions,
                    field -> !field.isDeprecated());
        }
        return null;
    };


    private static final IntrospectionDataFetcher<?> interfacesFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) type).getInterfaces();
        }
        if (type instanceof GraphQLInterfaceType) {
            return ((GraphQLInterfaceType) type).getInterfaces();
        }
        return null;
    };

    private static final IntrospectionDataFetcher<?> possibleTypesFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLInterfaceType) {
            return environment.getGraphQLSchema().getImplementations((GraphQLInterfaceType) type);
        }
        if (type instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) type).getTypes();
        }
        return null;
    };

    private static final IntrospectionDataFetcher<?> enumValuesTypesFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLEnumType) {
            Boolean includeDeprecated = environment.getArgument("includeDeprecated");
            List<GraphQLEnumValueDefinition> values = ((GraphQLEnumType) type).getValues();
            if (includeDeprecated) {
                return values;
            }
            return ImmutableKit.filter(values,
                    enumValue -> !enumValue.isDeprecated());
        }
        return null;
    };

    private static final IntrospectionDataFetcher<?> inputFieldsFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLInputObjectType) {
            Boolean includeDeprecated = environment.getArgument("includeDeprecated");
            List<GraphQLInputObjectField> inputFields = environment
                    .getGraphQLSchema()
                    .getCodeRegistry()
                    .getFieldVisibility()
                    .getFieldDefinitions((GraphQLInputObjectType) type);
            if (includeDeprecated) {
                return inputFields;
            }
            return ImmutableKit.filter(inputFields,
                    inputField -> !inputField.isDeprecated());
        }
        return null;
    };

    private static final IntrospectionDataFetcher<?> OfTypeFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLModifiedType) {
            return unwrapOne((GraphQLModifiedType) type);
        }
        return null;
    };

    private static final IntrospectionDataFetcher<?> specifiedByUrlDataFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getSpecifiedByUrl();
        }
        return null;
    };

    private static final IntrospectionDataFetcher<?> isOneOfFetcher = environment -> {
        Object type = environment.getSource();
        if (type instanceof GraphQLInputObjectType) {
            return ((GraphQLInputObjectType) type).isOneOf();
        }
        return null;
    };

    public static final GraphQLObjectType __Type = newObject()
            .name("__Type")
            .field(newFieldDefinition()
                    .name("kind")
                    .type(nonNull(__TypeKind)))
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
                            .defaultValueProgrammatic(false)))
            .field(newFieldDefinition()
                    .name("interfaces")
                    .type(list(nonNull(typeRef("__Type")))))
            .field(newFieldDefinition()
                    .name("possibleTypes")
                    .type(list(nonNull(typeRef("__Type")))))
            .field(newFieldDefinition()
                    .name("enumValues")
                    .type(list(nonNull(__EnumValue)))
                    .argument(newArgument()
                            .name("includeDeprecated")
                            .type(GraphQLBoolean)
                            .defaultValueProgrammatic(false)))
            .field(newFieldDefinition()
                    .name("inputFields")
                    .type(list(nonNull(__InputValue)))
                    .argument(newArgument()
                            .name("includeDeprecated")
                            .type(GraphQLBoolean)
                            .defaultValueProgrammatic(false)))
            .field(newFieldDefinition()
                    .name("ofType")
                    .type(typeRef("__Type")))
            .field(newFieldDefinition()
                    .name("isOneOf")
                    .description("This field is considered experimental because it has not yet been ratified in the graphql specification")
                    .type(GraphQLBoolean))
            .field(newFieldDefinition()
                    .name("specifiedByURL")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("specifiedByUrl")
                    .type(GraphQLString)
                    .deprecate("This legacy name has been replaced by `specifiedByURL`")
            )
            .build();

    static {
        register(__Type, "kind", kindDataFetcher);
        register(__Type, "name", nameDataFetcher);
        register(__Type, "description", descriptionDataFetcher);
        register(__Type, "fields", fieldsFetcher);
        register(__Type, "interfaces", interfacesFetcher);
        register(__Type, "possibleTypes", possibleTypesFetcher);
        register(__Type, "enumValues", enumValuesTypesFetcher);
        register(__Type, "inputFields", inputFieldsFetcher);
        register(__Type, "ofType", OfTypeFetcher);
        register(__Type, "isOneOf", isOneOfFetcher);
        register(__Type, "specifiedByURL", specifiedByUrlDataFetcher);
        register(__Type, "specifiedByUrl", specifiedByUrlDataFetcher); // note that this field is deprecated
    }


    public enum DirectiveLocation {
        QUERY,
        MUTATION,
        SUBSCRIPTION,
        FIELD,
        FRAGMENT_DEFINITION,
        FRAGMENT_SPREAD,
        INLINE_FRAGMENT,
        VARIABLE_DEFINITION,
        //
        // schema SDL places
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
            .value("SUBSCRIPTION", DirectiveLocation.SUBSCRIPTION, "Indicates the directive is valid on subscriptions.")
            .value("FIELD", DirectiveLocation.FIELD, "Indicates the directive is valid on fields.")
            .value("FRAGMENT_DEFINITION", DirectiveLocation.FRAGMENT_DEFINITION, "Indicates the directive is valid on fragment definitions.")
            .value("FRAGMENT_SPREAD", DirectiveLocation.FRAGMENT_SPREAD, "Indicates the directive is valid on fragment spreads.")
            .value("INLINE_FRAGMENT", DirectiveLocation.INLINE_FRAGMENT, "Indicates the directive is valid on inline fragments.")
            .value("VARIABLE_DEFINITION", DirectiveLocation.VARIABLE_DEFINITION, "Indicates the directive is valid on variable definitions.")
            //
            // from schema SDL PR  https://github.com/facebook/graphql/pull/90
            //
            .value("SCHEMA", DirectiveLocation.SCHEMA, "Indicates the directive is valid on a schema SDL definition.")
            .value("SCALAR", DirectiveLocation.SCALAR, "Indicates the directive is valid on a scalar SDL definition.")
            .value("OBJECT", DirectiveLocation.OBJECT, "Indicates the directive is valid on an object SDL definition.")
            .value("FIELD_DEFINITION", DirectiveLocation.FIELD_DEFINITION, "Indicates the directive is valid on a field SDL definition.")
            .value("ARGUMENT_DEFINITION", DirectiveLocation.ARGUMENT_DEFINITION, "Indicates the directive is valid on a field argument SDL definition.")
            .value("INTERFACE", DirectiveLocation.INTERFACE, "Indicates the directive is valid on an interface SDL definition.")
            .value("UNION", DirectiveLocation.UNION, "Indicates the directive is valid on an union SDL definition.")
            .value("ENUM", DirectiveLocation.ENUM, "Indicates the directive is valid on an enum SDL definition.")
            .value("ENUM_VALUE", DirectiveLocation.ENUM_VALUE, "Indicates the directive is valid on an enum value SDL definition.")
            .value("INPUT_OBJECT", DirectiveLocation.INPUT_OBJECT, "Indicates the directive is valid on an input object SDL definition.")
            .value("INPUT_FIELD_DEFINITION", DirectiveLocation.INPUT_FIELD_DEFINITION, "Indicates the directive is valid on an input object field SDL definition.")
            .build();


    public static final GraphQLObjectType __Directive = newObject()
            .name("__Directive")
            .field(newFieldDefinition()
                    .name("name")
                    .description("The __Directive type represents a Directive that a server supports.")
                    .type(nonNull(GraphQLString)))
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("isRepeatable")
                    .type(nonNull(GraphQLBoolean)))
            .field(newFieldDefinition()
                    .name("locations")
                    .type(nonNull(list(nonNull(__DirectiveLocation)))))
            .field(newFieldDefinition()
                    .name("args")
                    .type(nonNull(list(nonNull(__InputValue))))
                    .argument(newArgument()
                            .name("includeDeprecated")
                            .type(GraphQLBoolean)
                            .defaultValueProgrammatic(false)))
            .build();

    static {
        IntrospectionDataFetcher<?> locationsDataFetcher = environment -> {
            GraphQLDirective directive = environment.getSource();
            return new ArrayList<>(directive.validLocations());
        };
        IntrospectionDataFetcher<?> argsDataFetcher = environment -> {
            GraphQLDirective directive = environment.getSource();
            Boolean includeDeprecated = environment.getArgument("includeDeprecated");
            return ImmutableKit.filter(directive.getArguments(),
                    arg -> includeDeprecated || !arg.isDeprecated());
        };
        register(__Directive, "name", nameDataFetcher);
        register(__Directive, "description", descriptionDataFetcher);
        register(__Directive, "isRepeatable", GraphQLDirective.class, GraphQLDirective::isRepeatable);
        register(__Directive, "locations", locationsDataFetcher);
        register(__Directive, "args", argsDataFetcher);
    }

    public static final GraphQLObjectType __Schema = newObject()
            .name("__Schema")
            .description("A GraphQL Introspection defines the capabilities" +
                    " of a GraphQL server. It exposes all available types and directives on " +
                    "the server, the entry points for query, mutation, and subscription operations.")
            .field(newFieldDefinition()
                    .name("description")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("types")
                    .description("A list of all types supported by this server.")
                    .type(nonNull(list(nonNull(__Type)))))
            .field(newFieldDefinition()
                    .name("queryType")
                    .description("The type that query operations will be rooted at.")
                    .type(nonNull(__Type)))
            .field(newFieldDefinition()
                    .name("mutationType")
                    .description("If this server supports mutation, the type that mutation operations will be rooted at.")
                    .type(__Type))
            .field(newFieldDefinition()
                    .name("directives")
                    .description("A list of all directives supported by this server.")
                    .type(nonNull(list(nonNull(__Directive)))))
            .field(newFieldDefinition()
                    .name("subscriptionType")
                    .description("If this server support subscription, the type that subscription operations will be rooted at.")
                    .type(__Type))
            .build();

    static {
        IntrospectionDataFetcher<?> descriptionsDataFetcher = environment -> environment.getGraphQLSchema().getDescription();

        register(__Schema, "description", descriptionsDataFetcher);
        register(__Schema, "types", GraphQLSchema.class, GraphQLSchema::getAllTypesAsList);
        register(__Schema, "queryType", GraphQLSchema.class, GraphQLSchema::getQueryType);
        register(__Schema, "mutationType", GraphQLSchema.class, GraphQLSchema::getMutationType);
        register(__Schema, "directives", GraphQLSchema.class, GraphQLSchema::getDirectives);
        register(__Schema, "subscriptionType", GraphQLSchema.class, GraphQLSchema::getSubscriptionType);
    }

    public static final GraphQLFieldDefinition SchemaMetaFieldDef = buildSchemaField(__Schema);
    public static final GraphQLFieldDefinition TypeMetaFieldDef = buildTypeField(__Schema);
    public static final GraphQLFieldDefinition TypeNameMetaFieldDef = newFieldDefinition()
            .name("__typename")
            .type(nonNull(GraphQLString))
            .description("The name of the current Object type at runtime.")
            .build();

    public static final Set<String> INTROSPECTION_SYSTEM_FIELDS = ImmutableSet.of(
            Introspection.SchemaMetaFieldDef.getName(),
            Introspection.TypeMetaFieldDef.getName(),
            Introspection.TypeNameMetaFieldDef.getName()
    );

    public static final IntrospectionDataFetcher<?> SchemaMetaFieldDefDataFetcher = IntrospectionDataFetchingEnvironment::getGraphQLSchema;

    public static final IntrospectionDataFetcher<?> TypeMetaFieldDefDataFetcher = environment -> {
        String name = environment.getArgument("name");
        return environment.getGraphQLSchema().getType(name);
    };

    // __typename is always available
    public static final IntrospectionDataFetcher<?> TypeNameMetaFieldDefDataFetcher = environment -> simplePrint(environment.getParentType());

    @Internal
    public static GraphQLFieldDefinition buildSchemaField(GraphQLObjectType introspectionSchemaType) {
        return newFieldDefinition()
                .name("__schema")
                .type(nonNull(introspectionSchemaType))
                .description("Access the current type schema of this server.")
                .build();
    }

    @Internal
    public static GraphQLFieldDefinition buildTypeField(GraphQLObjectType introspectionSchemaType) {

        GraphQLOutputType fieldType = introspectionSchemaType.getFieldDefinition("types").getType();
        GraphQLObjectType underscoreType = unwrapAllAs(fieldType);
        return newFieldDefinition()
                .name("__type")
                .type(underscoreType)
                .description("Request the type information of a single type.")
                .argument(newArgument()
                        .name("name")
                        .type(nonNull(GraphQLString)))
                .build();
    }

    private static final Set<String> introspectionTypes = new HashSet<>();

    static {
        GraphQLObjectType IntrospectionQuery = newObject()
                .name("IntrospectionQuery")
                .field(SchemaMetaFieldDef)
                .field(TypeMetaFieldDef)
                .field(TypeNameMetaFieldDef)
                .build();

        introspectionTypes.add(__DirectiveLocation.getName());
        introspectionTypes.add(__TypeKind.getName());
        introspectionTypes.add(__Type.getName());
        introspectionTypes.add(__Schema.getName());
        introspectionTypes.add(__InputValue.getName());
        introspectionTypes.add(__Field.getName());
        introspectionTypes.add(__EnumValue.getName());
        introspectionTypes.add(__Directive.getName());

        // make sure all TypeReferences are resolved.
        // note: it is important to put this on the bottom of static code block.
        GraphQLSchema.newSchema().query(IntrospectionQuery).build();
    }

    public static boolean isIntrospectionTypes(GraphQLNamedType type) {
        return introspectionTypes.contains(type.getName());
    }

    public static boolean isIntrospectionTypes(String typeName) {
        return introspectionTypes.contains(typeName);
    }

    /**
     * This will look up a field definition by name, and understand that fields like __typename and __schema are special
     * and take precedence in field resolution.  If the parent type is a union type, then the only field allowed
     * is `__typename`.
     *
     * @param schema     the schema to use
     * @param parentType the type of the parent object
     * @param fieldName  the field to look up
     *
     * @return a field definition otherwise throws an assertion exception if it's null
     */
    public static GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLCompositeType parentType, String fieldName) {

        GraphQLFieldDefinition fieldDefinition = getSystemFieldDef(schema, parentType, fieldName);
        if (fieldDefinition != null) {
            return fieldDefinition;
        }

        assertTrue(parentType instanceof GraphQLFieldsContainer, "should not happen : parent type must be an object or interface %s", parentType);
        GraphQLFieldsContainer fieldsContainer = (GraphQLFieldsContainer) parentType;
        fieldDefinition = schema.getCodeRegistry().getFieldVisibility().getFieldDefinition(fieldsContainer, fieldName);
        assertTrue(fieldDefinition != null, "Unknown field '%s' for type %s", fieldName, fieldsContainer.getName());
        return fieldDefinition;
    }

    /**
     * This will look up a field definition by name, and understand that fields like __typename and __schema are special
     * and take precedence in field resolution
     *
     * @param schema     the schema to use
     * @param parentType the type of the parent {@link GraphQLFieldsContainer}
     * @param fieldName  the field to look up
     *
     * @return a field definition otherwise throws an assertion exception if it's null
     */
    public static GraphQLFieldDefinition getFieldDefinition(GraphQLSchema schema, GraphQLFieldsContainer parentType, String fieldName) {
        // this method is optimized to look up the most common case first (type for field) and hence suits the hot path of the execution engine
        // and as a small benefit does not allocate any assertions unless it completely failed
        GraphQLFieldDefinition fieldDefinition = schema.getCodeRegistry().getFieldVisibility().getFieldDefinition(parentType, fieldName);
        if (fieldDefinition == null) {
            // we look up system fields second because they are less likely to be the field in question
            fieldDefinition = getSystemFieldDef(schema, parentType, fieldName);
            if (fieldDefinition == null) {
                Assert.assertShouldNeverHappen(String.format("Unknown field '%s' for type %s", fieldName, parentType.getName()));
            }
        }
        return fieldDefinition;
    }

    private static GraphQLFieldDefinition getSystemFieldDef(GraphQLSchema schema, GraphQLCompositeType parentType, String fieldName) {
        if (schema.getQueryType() == parentType) {
            if (fieldName.equals(schema.getIntrospectionSchemaFieldDefinition().getName())) {
                return schema.getIntrospectionSchemaFieldDefinition();
            }
            if (fieldName.equals(schema.getIntrospectionTypeFieldDefinition().getName())) {
                return schema.getIntrospectionTypeFieldDefinition();
            }
        }
        if (fieldName.equals(schema.getIntrospectionTypenameFieldDefinition().getName())) {
            return schema.getIntrospectionTypenameFieldDefinition();
        }
        return null;
    }
}
