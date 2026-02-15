# Fix 23 Test Failures from JSpecify Annotation Changes

The JSpecify PR added `assertNotNull()` calls in several Builder `build()` methods for non-nullable fields. Many Groovy tests construct incomplete objects (e.g., `Field.newField().build()` without setting `name`), which now crash at runtime.

## Root Cause

Builder `build()` methods in the following classes now call `assertNotNull()` on fields that tests leave null:

| Source class | `assertNotNull` fields in `build()` | Test impact |
|---|---|---|
| `Field.Builder` | `name` | 12 occurrences of `Field.newField().build()` across 3 test files |
| `FieldDefinition.Builder` | `name`, `type` | 2 occurrences in `SchemaPrinterTest` |
| `EnumTypeDefinition.Builder` | `name` | 1 occurrence in `SchemaPrinterTest` |
| `EnumValueDefinition.Builder` | `name` | 1 occurrence in `SchemaPrinterTest` |
| `FragmentDefinition.Builder` | `name`, `typeCondition`, `selectionSet` | 2 in `TraversalContextTest`, 1 in `FragmentsOnCompositeTypeTest` |

## Proposed Changes

### Test Fixes — Supply Required Fields

The fix is to update test code to supply the required fields that builders now enforce. These are minimal changes — adding `.name("dummy")` or similar placeholder values.

---

### ExecutionStrategyTest

#### [MODIFY] [ExecutionStrategyTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/execution/ExecutionStrategyTest.groovy)

Replace all `Field.newField().build()` with `Field.newField().name("dummy").build()` at lines: 143, 145, 773, 774, 797, 798, 821, 822, 945, 946, 969, 970.

---

### QueryTraverserTest

#### [MODIFY] [QueryTraverserTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/analysis/QueryTraverserTest.groovy)

Replace `Field.newField().build()` with `Field.newField().name("dummy").build()` at lines: 1528-1536, 1547.

---

### NodeParentTreeTest

#### [MODIFY] [NodeParentTreeTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/language/NodeParentTreeTest.groovy)

Replace `FieldDefinition.newFieldDefinition().name("field").build()` — this should already work since `name` is set, but `type` is not. Need to add `.type(new TypeName("String"))`.

At line 10: `FieldDefinition.newFieldDefinition().name("field").build()` → add `.type(new TypeName("String"))`.

---

### NodeTraverserTest

#### [MODIFY] [NodeTraverserTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/language/NodeTraverserTest.groovy)

Replace `Field.newField().build()` with `Field.newField().name("dummy").build()` at line 143.

---

### DataFetchingEnvironmentImplTest

#### [MODIFY] [DataFetchingEnvironmentImplTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/schema/DataFetchingEnvironmentImplTest.groovy)

- Line 29: `FragmentDefinition.newFragmentDefinition().name("frag").typeCondition(new TypeName("t")).build()` — needs `.selectionSet(SelectionSet.newSelectionSet().build())` added since `selectionSet` is now `assertNotNull`.
- Line 32: `new OperationDefinition("q")` — this constructor was NOT changed (no diff), so it should still work. But the class may depend on other annotated classes. Need to check if `OperationDefinition` has a single-arg convenience constructor.
- Line 150: `new Argument("arg1", new StringValue("argVal"))` and `new Field("someField", [argument])` — `Field` convenience constructors still work since they pass `name` directly to the main constructor, which no longer null-checks `name` (the `assertNotNull` is only in the Builder).

> [!IMPORTANT]
> The `Field` convenience constructors (e.g., `new Field("someField")`) should still work because they call the main constructor directly without `assertNotNull`. The failures in `DataFetchingEnvironmentImplTest` likely stem from `FragmentDefinition.Builder.build()` crashing on the `frag` field (line 29), which fails before the test even starts since it's a class-level field.

---

### SchemaPrinterTest

#### [MODIFY] [SchemaPrinterTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/schema/idl/SchemaPrinterTest.groovy)

Lines 2795, 2799: `FieldDefinition.newFieldDefinition().comments(...).build()` — add `.name("placeholder").type(new TypeName("String"))`.

Line 2757-2758: `newEnumTypeDefinition().comments(...).build()` — add `.name("placeholder")`.

Line 2761: `EnumValueDefinition.newEnumValueDefinition().comments(...).build()` — add `.name("placeholder")`.

Line 2744: `DirectiveDefinition.newDirectiveDefinition().comments(...).build()` — `DirectiveDefinition.Builder` does NOT have `assertNotNull(name)`, so this should be fine. But verify.

---

### TraversalContextTest

#### [MODIFY] [TraversalContextTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/validation/TraversalContextTest.groovy)

Lines 131, 165-168: `FragmentDefinition.newFragmentDefinition().name(...).typeCondition(...).build()` — add `.selectionSet(SelectionSet.newSelectionSet().build())` since `selectionSet` is now `assertNotNull`.

---

### FragmentsOnCompositeTypeTest

#### [MODIFY] [FragmentsOnCompositeTypeTest.groovy](file:///Users/dondonz/Development/graphql-java/src/test/groovy/graphql/validation/rules/FragmentsOnCompositeTypeTest.groovy)

Line 71: `FragmentDefinition.newFragmentDefinition().name("fragment").typeCondition(...).build()` — add `.selectionSet(SelectionSet.newSelectionSet().build())`.

---

## Verification Plan

### Automated Tests

Run each failing test class individually:

```bash
cd /Users/dondonz/Development/graphql-java

# Need to work around jar task failure from branch name with '/'
# Option 1: Run just the test compilation and execution
./gradlew test --tests "graphql.execution.ExecutionStrategyTest" \
  --tests "graphql.analysis.QueryTraverserTest" \
  --tests "graphql.language.NodeParentTreeTest" \
  --tests "graphql.language.NodeTraverserTest" \
  --tests "graphql.schema.DataFetchingEnvironmentImplTest" \
  --tests "graphql.schema.idl.SchemaPrinterTest" \
  --tests "graphql.validation.TraversalContextTest" \
  --tests "graphql.validation.rules.FragmentsOnCompositeTypeTest"
```

> [!WARNING]
> The `:jar` task is currently failing due to the branch name `copilot/add-jspecify-annotations-again` containing `/`, which creates an invalid path for the jar artifact. This may need to be fixed before tests can be run. Alternative: compile test code only with `./gradlew compileTestGroovy`.

All 23 originally failing tests should pass after the fixes.
