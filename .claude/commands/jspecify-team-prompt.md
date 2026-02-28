# JSpecify Annotation Team Orchestrator Prompt

You are an orchestrator agent responsible for coordinating a team of worker
agents to add JSpecify nullability annotations to public API classes in the
graphql-java project.

## Your Job

1. Read the current exemption list from:
   `src/test/groovy/graphql/archunit/JSpecifyAnnotationsCheck.groovy`
   Before building batch assignments, check which classes from the lists below
   are still present in the exemption list. Only assign classes that remain.
   This makes the orchestrator safe to re-run after interruption.

2. Group the remaining classes into batches (see batches below).

3. Launch worker agents in **waves of 3** to manage token usage.
   After each wave completes, launch the reviewer before starting the next wave.
   Only proceed to the next wave if the reviewer reports no NullAway errors.

4. After all waves are complete, launch the validator.

## Execution Flow

### Wave 1
Launch Workers 1, 2, 3 in parallel. Wait for all to complete.
Launch Reviewer for Wave 1. Wait for completion and NullAway confirmation.

### Wave 2
Launch Workers 5, 6, 7 in parallel. Wait for all to complete.
Launch Reviewer for Wave 2. Wait for completion and NullAway confirmation.

### Wave 3
Launch Workers 8, 9 in parallel. Wait for all to complete.
Launch Reviewer for Wave 3. Wait for completion and NullAway confirmation.

### Final Step
Launch the validator to remove completed classes from
`JSpecifyAnnotationsCheck.groovy` and push the branch.

---

## Batch Assignments

### Worker 1 — graphql.analysis (16 classes)
```
graphql.analysis.QueryComplexityCalculator
graphql.analysis.QueryComplexityInfo
graphql.analysis.QueryDepthInfo
graphql.analysis.QueryReducer
graphql.analysis.QueryTransformer
graphql.analysis.QueryTraversalOptions
graphql.analysis.QueryVisitor
graphql.analysis.QueryVisitorFieldArgumentEnvironment
graphql.analysis.QueryVisitorFieldArgumentInputValue
graphql.analysis.QueryVisitorFieldArgumentValueEnvironment
graphql.analysis.QueryVisitorFieldEnvironment
graphql.analysis.QueryVisitorFragmentDefinitionEnvironment
graphql.analysis.QueryVisitorFragmentSpreadEnvironment
graphql.analysis.QueryVisitorInlineFragmentEnvironment
graphql.analysis.QueryVisitorStub
graphql.analysis.values.ValueTraverser
```

### Worker 2 — graphql.execution core (26 classes)
```
graphql.execution.AbortExecutionException
graphql.execution.AsyncExecutionStrategy
graphql.execution.AsyncSerialExecutionStrategy
graphql.execution.CoercedVariables
graphql.execution.DataFetcherExceptionHandlerParameters
graphql.execution.DataFetcherExceptionHandlerResult
graphql.execution.DefaultValueUnboxer
graphql.execution.ExecutionContext
graphql.execution.ExecutionId
graphql.execution.ExecutionStepInfo
graphql.execution.ExecutionStrategyParameters
graphql.execution.FetchedValue
graphql.execution.FieldValueInfo
graphql.execution.InputMapDefinesTooManyFieldsException
graphql.execution.MergedSelectionSet
graphql.execution.MissingRootTypeException
graphql.execution.NonNullableValueCoercedAsNullException
graphql.execution.NormalizedVariables
graphql.execution.OneOfNullValueException
graphql.execution.OneOfTooManyKeysException
graphql.execution.ResultNodesInfo
graphql.execution.ResultPath
graphql.execution.SimpleDataFetcherExceptionHandler
graphql.execution.SubscriptionExecutionStrategy
graphql.execution.UnknownOperationException
graphql.execution.UnresolvedTypeException
```

### Worker 3 — graphql.execution sub-packages (24 classes)
```
graphql.execution.conditional.ConditionalNodeDecision
graphql.execution.directives.QueryAppliedDirective
graphql.execution.directives.QueryAppliedDirectiveArgument
graphql.execution.directives.QueryDirectives
graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation
graphql.execution.instrumentation.fieldvalidation.SimpleFieldValidation
graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
graphql.execution.instrumentation.tracing.TracingInstrumentation
graphql.execution.instrumentation.tracing.TracingSupport
graphql.execution.preparsed.PreparsedDocumentEntry
graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport
graphql.execution.preparsed.persisted.InMemoryPersistedQueryCache
graphql.execution.preparsed.persisted.PersistedQueryCacheMiss
graphql.execution.preparsed.persisted.PersistedQueryIdInvalid
graphql.execution.preparsed.persisted.PersistedQueryNotFound
graphql.execution.reactive.DelegatingSubscription
graphql.execution.reactive.SubscriptionPublisher
```

### Worker 5 — graphql.language (remaining) + graphql.normalized (23 classes)
```
graphql.language.ScalarTypeDefinition
graphql.language.ScalarTypeExtensionDefinition
graphql.language.SchemaDefinition
graphql.language.SchemaExtensionDefinition
graphql.language.Selection
graphql.language.SelectionSet
graphql.language.SelectionSetContainer
graphql.language.TypeKind
graphql.language.TypeName
graphql.language.UnionTypeDefinition
graphql.language.UnionTypeExtensionDefinition
graphql.language.VariableDefinition
graphql.normalized.ExecutableNormalizedField
graphql.normalized.ExecutableNormalizedOperation
graphql.normalized.ExecutableNormalizedOperationFactory
graphql.normalized.ExecutableNormalizedOperationToAstCompiler
graphql.normalized.NormalizedInputValue
graphql.normalized.incremental.NormalizedDeferredExecution
graphql.normalized.nf.NormalizedDocument
graphql.normalized.nf.NormalizedDocumentFactory
graphql.normalized.nf.NormalizedField
graphql.normalized.nf.NormalizedOperation
graphql.normalized.nf.NormalizedOperationToAstCompiler
```

### Worker 6 — graphql.schema core types (38 classes)
```
graphql.schema.AsyncDataFetcher
graphql.schema.CoercingParseLiteralException
graphql.schema.CoercingParseValueException
graphql.schema.CoercingSerializeException
graphql.schema.DataFetcherFactories
graphql.schema.DataFetcherFactoryEnvironment
graphql.schema.DataFetchingFieldSelectionSet
graphql.schema.DefaultGraphqlTypeComparatorRegistry
graphql.schema.DelegatingDataFetchingEnvironment
graphql.schema.FieldCoordinates
graphql.schema.GraphQLAppliedDirectiveArgument
graphql.schema.GraphQLArgument
graphql.schema.GraphQLCompositeType
graphql.schema.GraphQLDirective
graphql.schema.GraphQLDirectiveContainer
graphql.schema.GraphQLEnumValueDefinition
graphql.schema.GraphQLFieldDefinition
graphql.schema.GraphQLFieldsContainer
graphql.schema.GraphQLImplementingType
graphql.schema.GraphQLInputFieldsContainer
graphql.schema.GraphQLInputObjectField
graphql.schema.GraphQLInputObjectType
graphql.schema.GraphQLInputSchemaElement
graphql.schema.GraphQLInputType
graphql.schema.GraphQLInputValueDefinition
graphql.schema.GraphQLInterfaceType
graphql.schema.GraphQLModifiedType
graphql.schema.GraphQLNamedOutputType
graphql.schema.GraphQLNamedSchemaElement
graphql.schema.GraphQLNamedType
graphql.schema.GraphQLNonNull
graphql.schema.GraphQLNullableType
graphql.schema.GraphQLObjectType
graphql.schema.GraphQLOutputType
graphql.schema.GraphQLSchemaElement
graphql.schema.GraphQLTypeReference
graphql.schema.GraphQLTypeVisitor
graphql.schema.GraphQLTypeVisitorStub
graphql.schema.GraphQLUnmodifiedType
```

### Worker 7 — graphql.schema utilities + sub-packages (41 classes)
```
graphql.schema.GraphqlElementParentTree
graphql.schema.GraphqlTypeComparatorEnvironment
graphql.schema.GraphqlTypeComparatorRegistry
graphql.schema.InputValueWithState
graphql.schema.SchemaElementChildrenContainer
graphql.schema.SchemaTransformer
graphql.schema.SchemaTraverser
graphql.schema.SelectedField
graphql.schema.StaticDataFetcher
graphql.schema.diff.DiffCategory
graphql.schema.diff.DiffEvent
graphql.schema.diff.DiffLevel
graphql.schema.diff.DiffSet
graphql.schema.diff.SchemaDiffSet
graphql.schema.diff.reporting.CapturingReporter
graphql.schema.diff.reporting.ChainedReporter
graphql.schema.diff.reporting.PrintStreamReporter
graphql.schema.idl.CombinedWiringFactory
graphql.schema.idl.MapEnumValuesProvider
graphql.schema.idl.NaturalEnumValuesProvider
graphql.schema.idl.RuntimeWiring
graphql.schema.idl.SchemaDirectiveWiring
graphql.schema.idl.SchemaDirectiveWiringEnvironment
graphql.schema.idl.SchemaGenerator
graphql.schema.idl.SchemaPrinter
graphql.schema.idl.TypeRuntimeWiring
graphql.schema.idl.errors.SchemaProblem
graphql.schema.idl.errors.StrictModeWiringException
graphql.schema.transform.FieldVisibilitySchemaTransformation
graphql.schema.transform.VisibleFieldPredicateEnvironment
graphql.schema.usage.SchemaUsage
graphql.schema.usage.SchemaUsageSupport
graphql.schema.validation.OneOfInputObjectRules
graphql.schema.validation.SchemaValidationErrorClassification
graphql.schema.visibility.BlockedFields
graphql.schema.visibility.DefaultGraphqlFieldVisibility
graphql.schema.visibility.GraphqlFieldVisibility
graphql.schema.visibility.NoIntrospectionGraphqlFieldVisibility
graphql.schema.visitor.GraphQLSchemaTraversalControl
```

### Worker 8 — graphql.extensions + graphql.incremental + graphql.introspection (15 classes)
```
graphql.extensions.ExtensionsBuilder
graphql.incremental.DeferPayload
graphql.incremental.DelayedIncrementalPartialResult
graphql.incremental.DelayedIncrementalPartialResultImpl
graphql.incremental.IncrementalExecutionResult
graphql.incremental.IncrementalExecutionResultImpl
graphql.incremental.IncrementalPayload
graphql.incremental.StreamPayload
graphql.introspection.GoodFaithIntrospection
graphql.introspection.Introspection
graphql.introspection.IntrospectionQuery
graphql.introspection.IntrospectionQueryBuilder
graphql.introspection.IntrospectionResultToSchema
graphql.introspection.IntrospectionWithDirectivesSupport
graphql.introspection.IntrospectionWithDirectivesSupport$DirectivePredicateEnvironment
```

### Worker 9 — graphql.parser + graphql.util (18 classes)
```
graphql.parser.InvalidSyntaxException
graphql.parser.MultiSourceReader
graphql.parser.Parser
graphql.parser.ParserEnvironment
graphql.parser.ParserOptions
graphql.util.CyclicSchemaAnalyzer
graphql.util.NodeAdapter
graphql.util.NodeLocation
graphql.util.NodeMultiZipper
graphql.util.NodeZipper
graphql.util.querygenerator.QueryGenerator
graphql.util.querygenerator.QueryGeneratorOptions
graphql.util.querygenerator.QueryGeneratorOptions$QueryGeneratorOptionsBuilder
graphql.util.querygenerator.QueryGeneratorResult
graphql.util.TraversalControl
graphql.util.TraverserContext
graphql.util.TreeTransformer
graphql.util.TreeTransformerUtil
```

---

## Instructions for Each Worker Agent

Each worker agent must follow these rules for every class in its batch:

### Per-class steps
1. Find the class at `src/main/java/<package/ClassName>.java`
2. Add `@NullMarked` at the class level
3. Remove redundant `@NonNull` annotations added by IntelliJ
4. Check Javadoc `@param` tags mentioning "null", "nullable", "may be null"
5. Check Javadoc `@return` tags mentioning "null", "optional", "if available"
6. Check method bodies that return null or check for null
7. Consult the GraphQL spec (https://spec.graphql.org/draft/) for the
   relevant concept — prioritize spec nullability over IntelliJ inference
8. If the class has a static Builder inner class, annotate it `@NullUnmarked`
   and skip further annotation of that inner class
9. Delete unused imports from the class after editing
10. Do NOT modify `JSpecifyAnnotationsCheck.groovy`

### Generics rules
- `<T>` does NOT allow `@Nullable` type arguments (`Box<@Nullable String>` illegal)
- `<T extends @Nullable Object>` DOES allow `@Nullable` type arguments
- Use `<T extends @Nullable Object>` only when callers genuinely need nullable types

### After each class
- Commit with message: `"Add JSpecify annotations to ClassName"`
- `git pull` before starting the next class to pick up any concurrent changes

### If a class has NullAway errors
- Make the smallest possible fix
- Use `Objects.requireNonNull(x, "message")` or `assertNotNull(x, "message")`
  only as a last resort
- Do NOT run the full build — leave that for the reviewer

---

## Reviewer Agent Instructions

After each wave of workers completes, launch a single reviewer agent.
The reviewer must NOT read any worker agent output or context — it works
only from the git diff.

### Step 1 — Get the diffs

For each class annotated in this wave, get its diff:
```
git diff origin/main...HEAD -- <path/to/ClassName.java>
```

### Step 2 — Review each diff independently

For each changed file, check:

**JSpecify correctness:**
- `@NullMarked` is present at the class level
- No redundant `@NonNull` annotations remain
- `@Nullable` is used where null is genuinely possible, not speculatively
- `<T extends @Nullable Object>` only used when callers truly need nullable
  type arguments
- Builder inner classes are marked `@NullUnmarked` and nothing more

**GraphQL spec compliance:**
Fetch https://spec.graphql.org/draft/ for the relevant concept.
`MUST` = non-null. Conditional/IF = nullable.
Does the annotation match the spec?

**Unused imports:**
Are there leftover imports (e.g. `org.jetbrains.annotations.NotNull`)?

### Step 3 — Fix any issues found

Make the minimal fix directly in the file.
Do NOT revert the whole annotation — fix only the specific problem.
Do NOT modify `JSpecifyAnnotationsCheck.groovy`.

If you make any fixes, commit with:
```
"Review fixes: correct JSpecify annotations for ClassName"
```

### Step 4 — Run the compile check

```
./gradlew compileJava
```

If it passes, report success and stop.

If it fails:
- Read the NullAway error output carefully
- Make the smallest possible fix (prefer `assertNotNull(x, "message")`
  over restructuring code)
- Re-run `./gradlew compileJava`
- Repeat until it passes or you have tried 3 times
- If still failing after 3 attempts, report the error details to the
  orchestrator so it can decide whether to continue to the next wave

---

## Validator Agent Instructions

All waves are complete and the reviewer has confirmed `compileJava` passes
for each wave. Your job is cleanup and push only.

1. Read the current exemption list from `JSpecifyAnnotationsCheck.groovy`
2. For each class that now has `@NullMarked` in its source file, remove it
   from the `JSPECIFY_EXEMPTION_LIST` in `JSpecifyAnnotationsCheck.groovy`
3. Run `./gradlew compileJava` one final time to confirm the full build passes
4. Commit: `"Remove annotated classes from JSpecify exemption list"`
5. Push: `git push -u origin claude/agent-team-jspecify-Frd74`

---

## Repository Details
- Working directory: `/home/user/graphql-java`
- Branch: `claude/agent-team-jspecify-Frd74`
- NullAway compile check: `./gradlew compileJava`
