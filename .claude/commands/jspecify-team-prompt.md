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

3. Launch worker agents in **waves of 2** (only 2 workers this wave).
   After the wave completes, launch the reviewer.

4. After the reviewer confirms no errors, launch the validator.

## Execution Flow

### Wave 1
Launch Workers 1, 2 in parallel. Wait for all to complete.
Launch Reviewer for Wave 1. Wait for completion and NullAway confirmation.

### Final Step
Launch the validator to remove completed classes from
`JSpecifyAnnotationsCheck.groovy` and push the branch.

---

## Batch Assignments

### Worker 1 — graphql.normalized (5 classes)
```
graphql.normalized.incremental.NormalizedDeferredExecution
graphql.normalized.nf.NormalizedDocument
graphql.normalized.nf.NormalizedDocumentFactory
graphql.normalized.nf.NormalizedField
graphql.normalized.nf.NormalizedOperation
```

### Worker 2 — graphql.normalized + schema + util (4 classes)
```
graphql.normalized.nf.NormalizedOperationToAstCompiler
graphql.schema.diffing.SchemaGraph
graphql.schema.validation.OneOfInputObjectRules
graphql.util.CyclicSchemaAnalyzer
graphql.util.querygenerator.QueryGenerator
graphql.util.querygenerator.QueryGeneratorOptions
graphql.util.querygenerator.QueryGeneratorResult
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

### IntelliJ inspection check
After editing each file, use the IntelliJ MCP `inspections` tool to check
for warnings in the modified file. Fix any nullability-related warnings
before moving on. This catches issues that NullAway alone may miss.

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
5. Push: `git push -u origin claude/jspecify-wave5`

---

## Repository Details
- Working directory: `/Users/dondonz/Development/graphql-java`
- Branch: `claude/jspecify-wave5`
- NullAway compile check: `./gradlew compileJava`
