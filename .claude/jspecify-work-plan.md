# JSpecify Annotation Work Plan

This document tracks the remaining classes that need JSpecify annotations, organized into batches for parallel PRs.

---

## PR 1: `graphql.language` Package (~64 classes)

All AST/language nodes and related classes.

- [ ] `AbstractDescribedNode`
- [ ] `AstNodeAdapter`
- [ ] `AstPrinter`
- [ ] `AstSignature`
- [ ] `AstSorter`
- [ ] `AstTransformer`
- [ ] `Comment`
- [ ] `Definition`
- [ ] `DescribedNode`
- [ ] `Description`
- [ ] `Directive`
- [ ] `DirectiveDefinition`
- [ ] `DirectiveLocation`
- [ ] `DirectivesContainer`
- [ ] `Document`
- [ ] `EnumTypeDefinition`
- [ ] `EnumTypeExtensionDefinition`
- [ ] `EnumValueDefinition`
- [ ] `Field`
- [ ] `FieldDefinition`
- [ ] `FragmentDefinition`
- [ ] `FragmentSpread`
- [ ] `IgnoredChar`
- [ ] `IgnoredChars`
- [ ] `ImplementingTypeDefinition`
- [ ] `InlineFragment`
- [ ] `InputObjectTypeDefinition`
- [ ] `InputObjectTypeExtensionDefinition`
- [ ] `InputValueDefinition`
- [ ] `InterfaceTypeDefinition`
- [ ] `InterfaceTypeExtensionDefinition`
- [ ] `ListType`
- [ ] `Node`
- [ ] `NodeChildrenContainer`
- [ ] `NodeDirectivesBuilder`
- [ ] `NodeParentTree`
- [ ] `NodeTraverser`
- [ ] `NodeVisitor`
- [ ] `NodeVisitorStub`
- [ ] `NonNullType`
- [ ] `ObjectField`
- [ ] `ObjectTypeDefinition`
- [ ] `ObjectTypeExtensionDefinition`
- [ ] `OperationDefinition`
- [ ] `OperationTypeDefinition`
- [ ] `PrettyAstPrinter`
- [ ] `SDLDefinition`
- [ ] `SDLExtensionDefinition`
- [ ] `SDLNamedDefinition`
- [ ] `ScalarTypeDefinition`
- [ ] `ScalarTypeExtensionDefinition`
- [ ] `SchemaDefinition`
- [ ] `SchemaExtensionDefinition`
- [ ] `Selection`
- [ ] `SelectionSet`
- [ ] `SelectionSetContainer`
- [ ] `SourceLocation`
- [ ] `Type`
- [ ] `TypeDefinition`
- [ ] `TypeKind`
- [ ] `TypeName`
- [ ] `UnionTypeDefinition`
- [ ] `UnionTypeExtensionDefinition`
- [ ] `VariableDefinition`

---

## PR 2: `graphql.schema` Core Types (~45 classes)

Schema definition classes and type system.

- [ ] `AsyncDataFetcher`
- [ ] `CoercingParseLiteralException`
- [ ] `CoercingParseValueException`
- [ ] `CoercingSerializeException`
- [ ] `DataFetcherFactories`
- [ ] `DataFetcherFactoryEnvironment`
- [ ] `DataFetchingFieldSelectionSet`
- [ ] `DefaultGraphqlTypeComparatorRegistry`
- [ ] `DelegatingDataFetchingEnvironment`
- [ ] `FieldCoordinates`
- [ ] `GraphQLAppliedDirectiveArgument`
- [ ] `GraphQLArgument`
- [ ] `GraphQLCompositeType`
- [ ] `GraphQLDirective`
- [ ] `GraphQLDirectiveContainer`
- [ ] `GraphQLEnumValueDefinition`
- [ ] `GraphQLFieldDefinition`
- [ ] `GraphQLFieldsContainer`
- [ ] `GraphQLImplementingType`
- [ ] `GraphQLInputFieldsContainer`
- [ ] `GraphQLInputObjectField`
- [ ] `GraphQLInputObjectType`
- [ ] `GraphQLInputSchemaElement`
- [ ] `GraphQLInputType`
- [ ] `GraphQLInputValueDefinition`
- [ ] `GraphQLInterfaceType`
- [ ] `GraphQLModifiedType`
- [ ] `GraphQLNamedOutputType`
- [ ] `GraphQLNamedSchemaElement`
- [ ] `GraphQLNamedType`
- [ ] `GraphQLNonNull`
- [ ] `GraphQLNullableType`
- [ ] `GraphQLObjectType`
- [ ] `GraphQLOutputType`
- [ ] `GraphQLSchemaElement`
- [ ] `GraphQLTypeReference`
- [ ] `GraphQLTypeVisitor`
- [ ] `GraphQLTypeVisitorStub`
- [ ] `GraphQLUnmodifiedType`
- [ ] `GraphqlElementParentTree`
- [ ] `GraphqlTypeComparatorEnvironment`
- [ ] `GraphqlTypeComparatorRegistry`
- [ ] `InputValueWithState`
- [ ] `SchemaElementChildrenContainer`
- [ ] `SchemaTransformer`
- [ ] `SchemaTraverser`
- [ ] `SelectedField`
- [ ] `StaticDataFetcher`

---

## PR 3: `graphql.schema` Subpackages (~35 classes)

Schema IDL, diff, visibility, validation, transform, usage, visitor.

### `graphql.schema.diff`
- [ ] `DiffCategory`
- [ ] `DiffEvent`
- [ ] `DiffLevel`
- [ ] `DiffSet`
- [ ] `SchemaDiffSet`

### `graphql.schema.diff.reporting`
- [ ] `CapturingReporter`
- [ ] `ChainedReporter`
- [ ] `PrintStreamReporter`

### `graphql.schema.diffing`
- [ ] `SchemaGraph`

### `graphql.schema.idl`
- [ ] `CombinedWiringFactory`
- [ ] `DirectiveInfo`
- [ ] `MapEnumValuesProvider`
- [ ] `NaturalEnumValuesProvider`
- [ ] `RuntimeWiring`
- [ ] `SchemaDirectiveWiring`
- [ ] `SchemaDirectiveWiringEnvironment`
- [ ] `SchemaGenerator`
- [ ] `SchemaPrinter`
- [ ] `TypeRuntimeWiring`

### `graphql.schema.idl.errors`
- [ ] `SchemaProblem`
- [ ] `StrictModeWiringException`

### `graphql.schema.transform`
- [ ] `FieldVisibilitySchemaTransformation`
- [ ] `VisibleFieldPredicateEnvironment`

### `graphql.schema.usage`
- [ ] `SchemaUsage`
- [ ] `SchemaUsageSupport`

### `graphql.schema.validation`
- [ ] `OneOfInputObjectRules`
- [ ] `SchemaValidationErrorClassification`

### `graphql.schema.visibility`
- [ ] `BlockedFields`
- [ ] `DefaultGraphqlFieldVisibility`
- [ ] `GraphqlFieldVisibility`
- [ ] `NoIntrospectionGraphqlFieldVisibility`

### `graphql.schema.visitor`
- [ ] `GraphQLSchemaTraversalControl`

---

## PR 4: `graphql.execution` Package (~55 classes)

Execution engine, strategies, instrumentation.

### Core Execution
- [ ] `AbortExecutionException`
- [ ] `AsyncExecutionStrategy`
- [ ] `AsyncSerialExecutionStrategy`
- [ ] `CoercedVariables`
- [ ] `DataFetcherExceptionHandlerParameters`
- [ ] `DataFetcherExceptionHandlerResult`
- [ ] `DefaultValueUnboxer`
- [ ] `ExecutionContext`
- [ ] `ExecutionId`
- [ ] `ExecutionStepInfo`
- [ ] `ExecutionStrategyParameters`
- [ ] `FetchedValue`
- [ ] `FieldValueInfo`
- [ ] `InputMapDefinesTooManyFieldsException`
- [ ] `MergedSelectionSet`
- [ ] `MissingRootTypeException`
- [ ] `NonNullableValueCoercedAsNullException`
- [ ] `NormalizedVariables`
- [ ] `OneOfNullValueException`
- [ ] `OneOfTooManyKeysException`
- [ ] `ResponseMapFactory`
- [ ] `ResultNodesInfo`
- [ ] `ResultPath`
- [ ] `SimpleDataFetcherExceptionHandler`
- [ ] `SubscriptionExecutionStrategy`
- [ ] `UnknownOperationException`
- [ ] `UnresolvedTypeException`

### `graphql.execution.conditional`
- [ ] `ConditionalNodeDecision`

### `graphql.execution.directives`
- [ ] `QueryAppliedDirective`
- [ ] `QueryAppliedDirectiveArgument`
- [ ] `QueryDirectives`

### `graphql.execution.incremental`
- [ ] `DeferredExecution`

### `graphql.execution.instrumentation`
- [ ] `ChainedInstrumentation`
- [ ] `DocumentAndVariables`
- [ ] `NoContextChainedInstrumentation`
- [ ] `SimpleInstrumentation`
- [ ] `SimpleInstrumentationContext`
- [ ] `SimplePerformantInstrumentation`

### `graphql.execution.instrumentation.fieldvalidation`
- [ ] `FieldAndArguments`
- [ ] `FieldValidationEnvironment`
- [ ] `FieldValidationInstrumentation`
- [ ] `SimpleFieldValidation`

### `graphql.execution.instrumentation.parameters`
- [ ] `InstrumentationCreateStateParameters`
- [ ] `InstrumentationExecuteOperationParameters`
- [ ] `InstrumentationExecutionParameters`
- [ ] `InstrumentationExecutionStrategyParameters`
- [ ] `InstrumentationFieldCompleteParameters`
- [ ] `InstrumentationFieldFetchParameters`
- [ ] `InstrumentationFieldParameters`
- [ ] `InstrumentationValidationParameters`

### `graphql.execution.instrumentation.tracing`
- [ ] `TracingInstrumentation`
- [ ] `TracingSupport`

### `graphql.execution.preparsed`
- [ ] `PreparsedDocumentEntry`

### `graphql.execution.preparsed.persisted`
- [ ] `ApolloPersistedQuerySupport`
- [ ] `InMemoryPersistedQueryCache`
- [ ] `PersistedQueryCacheMiss`
- [ ] `PersistedQueryIdInvalid`
- [ ] `PersistedQueryNotFound`

### `graphql.execution.reactive`
- [ ] `DelegatingSubscription`
- [ ] `SubscriptionPublisher`

---

## PR 5: `graphql.analysis`, `graphql.normalized`, `graphql.util` (~40 classes)

Query analysis, normalized operations, and utilities.

### `graphql.analysis`
- [ ] `QueryComplexityCalculator`
- [ ] `QueryComplexityInfo`
- [ ] `QueryDepthInfo`
- [ ] `QueryReducer`
- [ ] `QueryTransformer`
- [ ] `QueryTraversalOptions`
- [ ] `QueryTraverser`
- [ ] `QueryVisitor`
- [ ] `QueryVisitorFieldArgumentEnvironment`
- [ ] `QueryVisitorFieldArgumentInputValue`
- [ ] `QueryVisitorFieldArgumentValueEnvironment`
- [ ] `QueryVisitorFieldEnvironment`
- [ ] `QueryVisitorFragmentDefinitionEnvironment`
- [ ] `QueryVisitorFragmentSpreadEnvironment`
- [ ] `QueryVisitorInlineFragmentEnvironment`
- [ ] `QueryVisitorStub`

### `graphql.analysis.values`
- [ ] `ValueTraverser`

### `graphql.normalized`
- [ ] `ExecutableNormalizedField`
- [ ] `ExecutableNormalizedOperation`
- [ ] `ExecutableNormalizedOperationFactory`
- [ ] `ExecutableNormalizedOperationToAstCompiler`
- [ ] `NormalizedInputValue`

### `graphql.normalized.incremental`
- [ ] `NormalizedDeferredExecution`

### `graphql.normalized.nf`
- [ ] `NormalizedDocument`
- [ ] `NormalizedDocumentFactory`
- [ ] `NormalizedField`
- [ ] `NormalizedOperation`
- [ ] `NormalizedOperationToAstCompiler`

### `graphql.util`
- [ ] `CyclicSchemaAnalyzer`
- [ ] `NodeAdapter`
- [ ] `NodeLocation`
- [ ] `NodeMultiZipper`
- [ ] `NodeZipper`
- [ ] `TraversalControl`
- [ ] `TraverserContext`
- [ ] `TreeTransformer`
- [ ] `TreeTransformerUtil`

### `graphql.util.querygenerator`
- [ ] `QueryGenerator`
- [ ] `QueryGeneratorOptions`
- [ ] `QueryGeneratorOptions$QueryGeneratorOptionsBuilder`
- [ ] `QueryGeneratorResult`

---

## PR 6: Remaining Classes (~30 classes)

Introspection, incremental/defer, parser, extensions, validation.

### `graphql.introspection`
- [ ] `GoodFaithIntrospection`
- [ ] `Introspection`
- [ ] `IntrospectionQuery`
- [ ] `IntrospectionQueryBuilder`
- [ ] `IntrospectionResultToSchema`
- [ ] `IntrospectionWithDirectivesSupport`
- [ ] `IntrospectionWithDirectivesSupport$DirectivePredicateEnvironment`

### `graphql.incremental`
- [ ] `DeferPayload`
- [ ] `DelayedIncrementalPartialResult`
- [ ] `DelayedIncrementalPartialResultImpl`
- [ ] `IncrementalExecutionResult`
- [ ] `IncrementalExecutionResultImpl`
- [ ] `IncrementalPayload`
- [ ] `StreamPayload`

### `graphql.parser`
- [ ] `InvalidSyntaxException`
- [ ] `MultiSourceReader`
- [ ] `Parser`
- [ ] `ParserEnvironment`
- [ ] `ParserOptions`

### `graphql.extensions`
- [ ] `ExtensionsBuilder`

### `graphql.validation.rules`
- [ ] `DeferDirectiveLabel`
- [ ] `DeferDirectiveOnRootLevel`
- [ ] `DeferDirectiveOnValidOperation`

---

## Summary

| PR | Focus | Classes |
|----|-------|---------|
| 1 | `graphql.language` (AST/Language) | ~64 |
| 2 | `graphql.schema` core types | ~45 |
| 3 | `graphql.schema` subpackages | ~35 |
| 4 | `graphql.execution` | ~55 |
| 5 | `graphql.analysis` + `graphql.normalized` + `graphql.util` | ~40 |
| 6 | Remaining (introspection, incremental, parser, extensions) | ~30 |

**Total: ~269 classes in 6 PRs**
