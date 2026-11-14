# NormalizedDocument and lookahead

`NormalizedDocument` is an immutable selection graph built during document
validation. It describes every operation and retains shared fragment bodies,
source locations, aliases, argument AST values, directives and type conditions.
It contains no execution variables, resolved arguments or mutable parent pointers.

## Enabling creation

Creation is **off by default**. Enable it before validation for requests whose
data fetchers or type resolvers use lookahead:

```java
ExecutionInput input = ExecutionInput.newExecutionInput(query)
        .graphQLContext(Map.of(NormalizedDocument.ENABLED, true))
        .build();
ExecutionResult result = graphQL.execute(input);
```

The same option works with `ParseAndValidate.parseAndValidate(schema, input)`.
`ParseAndValidateResult.getNormalizedDocument()` returns the graph after
successful validation, or null when creation is disabled or validation fails.
`ExecutionContext.getNormalizedDocument()` exposes it during execution.

Calling a lookahead inspection method (`getFields`, `getImmediateFields`,
`contains`, or their variants) without this option and artifact throws
`IllegalStateException`. This also applies to scalar fields and empty patterns.
Merely obtaining `DataFetchingEnvironment.getSelectionSet()` does not throw.
There is no lazy construction or revalidation fallback. As with other data
fetcher exceptions, the normal execution exception handler can turn this into a
field error; type resolver exceptions follow the existing execution error path.

## Stored graph

`getOperations()` contains operations in source order. Each operation has a root
selection set ID. IDs index `getSelectionSets()`. Each selection set contains
ordered `NormalizedField` and `NormalizedSelectionScope` entries:

- A field retains its original AST node and declared parent type. Its child ID
  is `-1` for a leaf.
- A scope retains an inline fragment or fragment spread, its type condition and
  its target selection set ID. Named scopes also retain the fragment definition.
- A named fragment body is stored once. Conditions belong to each reference,
  and remain symbolic AST expressions. No combinations of variable values are
  enumerated.

For example:

```graphql
query($a: Boolean!, $b: Boolean!) {
  box { ...Details @include(if: $a) ...Details @include(if: $b) }
}
fragment Details on Box { name }
```

The `box` selection set has **two scopes referencing one shared body**. That
body stores **one field**. Expanding both spreads would produce two logical
occurrences, but does not allocate two stored fields. During lookahead the two
active contributions collect into one `name` entry. If both conditions are
false, there is no entry. Nested scope conditions all have to hold.

Validation records fields using its existing resolved parent types. It does not
coerce arguments or construct execution trees. A final pass freezes the recorded
selection sets and their direct references. Validation's merge checks and query
complexity limits continue to use their existing semantics.

## Collection and behavior changes

Lookahead evaluates `@skip`, `@include` and custom `ConditionalNodeDecision`
callbacks for the current execution. It collects immediate fields by **concrete
parent object type and response key** (alias, or field name). Children are
collected when requested. `getFields()` traverses those child views in preorder.

### Separate concrete types

Given a `Pet` interface implemented by `Cat` and `Dog`:

```graphql
{ pet { name } }
```

Previously identical selections could become one entry with object types
`[Cat, Dog]`. Now `getImmediateFields()` returns two entries, `Cat.name` and
`Dog.name`. Each has one object type and one field definition. Their result key
is `name`; `getFieldsGroupedByResultKey().get("name")` contains both. Do not use
that group's size as the number of fields in one concrete response object.

### Aliases on mutually exclusive types

```graphql
{
  pet {
    ... on Cat { label: name }
    ... on Dog { label: nickname }
  }
}
```

There are two entries: `Cat.name` and `Dog.nickname`, both with result key
`label`. They may have different arguments and field definitions. Result-key
grouping alone does not imply identical arguments across concrete types.

### Combining children one level at a time

```graphql
{
  pet {
    ... on Cat { child { name } child { lives } }
    ... on Dog { child { nickname } }
  }
}
```

Immediate lookahead returns `Cat.child` and `Dog.child`. The two active Cat
occurrences contribute to one Cat child view; inspecting it returns `name` and
`lives`. The Dog view contains `nickname`. Identical trees on different parent
types are no longer recursively merged together.

### Ordering, paths and conditions

Concrete types are visited in type-name order. Within each type, fields retain
first-active-occurrence order. Thus a source ordering of Dog then Cat still
returns the Cat group first. Aliases distinguish entries but field paths keep
using schema field names: `child/name` and `Cat.child/Cat.name`.

Immediate fields have no parent within the data fetcher's lookahead view.
Descendants point to their parent in that view. A selected field's own
`getSelectionSet()` uses relative paths again. Levels remain absolute field
levels in the operation, excluding list indices.

`isConditional()` means this entry belongs to one of several possible parent
object types at its level. In `{ pet { name } }`, both entries are conditional,
even though every Pet type provides `name`. Variable conditions have already
been evaluated; an included field on a concrete object is not marked conditional
just because it had `@include`.

Arguments are resolved only when `SelectedField.getArguments()` is called,
using the concrete field definition, execution variables, context and locale.
Defaults can therefore differ between concrete implementations. They are never
stored in the cached document. Source `@defer` directives remain accessible in
the graph; lookahead includes their selected fields but does not expose a
separate normalized deferred-execution model.

## Preparsed caches

Cache and return the **whole entry** produced by the supplied parse/validate
function. Custom preparation can also use:

```java
ParseAndValidateResult prepared = ParseAndValidate.parseAndValidate(schema, input);
// Handle syntax failure separately: its document is null.
PreparsedDocumentEntry entry = new PreparsedDocumentEntry(
        prepared.getDocument(), prepared.getErrors(), prepared.getNormalizedDocument());
```

The graph and AST must be the same objects produced by validation. The entry
checks this association and requires an error-free document when a graph is
supplied. The graph is serializable with the entry, under the entry's existing
same-version serialization contract.

Cache keys must distinguish the schema/version and any validation options that
change the cached result, including `NormalizedDocument.ENABLED`. A cached
AST-only entry will not gain a graph when reused by an enabled request.
Invalidate or repopulate existing caches when enabling creation. A graph
can be shared across requests with different variables; collected fields and
resolved arguments belong to each execution. AST-transforming instrumentation
must retain or regenerate the corresponding validation result.

## Removed APIs and migration

This removes `ExecutableNormalizedField`, `ExecutableNormalizedOperation`,
`ExecutableNormalizedOperationFactory`, `ExecutionContext.getNormalizedQueryTree()`,
`ExecutionStrategy.getNormalizedField()`,
the old `graphql.normalized.nf` package, and
`graphql.normalized.incremental.NormalizedDeferredExecution`. It also removes `ExecutableNormalizedOperationToAstCompiler`, the
old normalized-operation compiler and `VariablePredicate` **without replacement**.

Use the opt-in validated graph for static inspection and the selection-set API
for execution lookahead. Custom execution strategies must remove overrides or
calls to the removed normalized-field hook. Update assumptions about type grouping, result counts,
ordering, conditional flags and parents as illustrated above. Consumers that
need to compile normalized operations back to AST must supply that capability
outside this API. `NormalizedInputValue`, variable normalization and execution
query-directive APIs remain available independently of document creation.

For resource protection, continue to use parser limits and
`QueryComplexityLimits` during validation. `NormalizedDocument.getFields().size()`
counts stored AST fields, not expanded occurrences or merged execution fields.

## Open draft question: flat lookahead paths

The stored graph stays shared, but full `getFields()` enumeration can expand
many concrete-type paths through nested interfaces. With two implementations
at every level, the number of such paths can grow exponentially with depth.
The current draft has not added an enumeration budget. The remaining choice is
whether to bound these compatibility methods with the configured occurrence
limit or replace them with an API that exposes shared graph views.
