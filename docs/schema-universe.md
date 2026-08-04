# Schema universe design

## Status

This is an experimental type-system core. It establishes storage semantics and a transformation
model; it does not yet replace `GraphQLSchema` in execution.

## Goals

- Hold hundreds or thousands of related schemas in one universe.
- Reuse unchanged schema elements and adjacency between derived schemas.
- Make a small transformation proportional to the changed sources, not the whole schema.
- Keep edges outside vertices.
- Avoid an object allocation per edge and avoid boxed integers in steady-state storage.
- Publish immutable snapshots that are safe for concurrent reads.

The key workload assumption is lineage: schemas are derived from an initial schema, usually through
small edits. This is materially different from reconciling unrelated schemas.

## Model

`SchemaUniverse` is a chunked vertex arena. Every vertex receives a monotonically increasing integer
ID. Cleanup can reclaim unused vertices and leave holes, but IDs are never reused. There is one Java
vertex type for each supported GraphQL schema-element kind. A vertex contains intrinsic scalar
properties such as kind, name, and description, but no child references.

`SUSchema` is an immutable adjacency snapshot plus a schema-specific root vertex, a persistent
registry of every named type keyed by universe name ID, and sparse user-controlled metadata keyed
by vertex ID. Root edges identify operation roots and directive definitions. Named-type membership
is independent of reachability, so a schema can retain disconnected types without representing
membership as graph edges.

Every successfully built snapshot registers itself with its universe under its unique name.
`SchemaUniverse.getSchema(name)` selects one snapshot directly, while `getSchemas()` and
`getSchemasByName()` return immutable views in registration order. Imports and transformations use
the same registration path. Reusing a schema name in one universe is rejected.

Registry membership defines a snapshot's supported lifetime. Removing a snapshot ends that lifetime
even if application code still holds a reference to the `SUSchema`. Removal does not immediately
reclaim its vertices, but no traversal, export, or transformation is guaranteed after removal.

The snapshot can retain adjacency for a currently unreachable vertex. This dormant adjacency is not
part of the effective rooted graph; it allows a later derived schema to reattach an unchanged
subgraph at constant edit scope. Consequently, `getStoredEdgeCount()` is a storage count rather than
a reachable-edge count.

The current API assumes callers traverse from the root or another known-reachable vertex.

## Typed traversal

`SUSchema` is the primary traversal API. Callers normally do not need to inspect edge kinds
directly. It provides typed helpers for:

- operation roots, all named types, and lookup by type name;
- directive definitions, including their applied directives, and schema-applied directives;
- object and interface fields;
- field and directive arguments;
- declared types and list/non-null wrapped types;
- implemented interfaces, union members, enum values, and input fields;
- applied directives, including repeatable applications filtered by name;
- applied-directive argument payloads and their exact input types.

For example, `getFields(objectType)`, `getField(objectType, name)`,
`getAppliedDirectives(field)`, and `getType(field)` all use the selected schema snapshot's
adjacency. The same shared vertex can therefore return different children when accessed through
different `SUSchema` instances. `getChildren`, `getChild`, and `containsEdge` remain available as
generic escape hatches.

## Edge representation

An edge is packed into one primitive `long`:

```
 63       56 55                    32 31                     0
+-----------+------------------------+-------------------------+
| kind (8)  | target name id (24)    | target vertex id (32)   |
+-----------+------------------------+-------------------------+
```

The source ID is the key of the adjacency map, so it is not repeated per edge. Immutable outgoing
arrays group edges by kind. Most kind ranges are sorted by target-name ID and target ID. Applied
directive ranges retain attachment order so schema and SDL round trips preserve directive order.
This provides:

- 8 bytes per stored edge in an adjacency array;
- binary search by relationship and GraphQL name for unordered edge kinds;
- a linear scan over the normally small applied-directive range;
- deterministic iteration with directive attachment order preserved;
- no edge objects or boxed endpoint IDs.

The 24-bit name ID limits one universe to 16,777,215 distinct names. Vertex IDs are non-negative
Java integers.

## Persistent snapshots

Outgoing arrays, named types, and metadata associations are stored in persistent hash-array mapped
tries. Adjacency and metadata use vertex IDs as keys, while the type registry uses universe name
IDs. A transform batches edge changes, freezes each changed value once, and path-copies only the
affected trie branches. All unchanged branches, outgoing arrays, named types, and metadata maps are
shared by identity with the parent schema.

Approximate operation costs, where `d` is source out-degree, `V` is the number of sources with
adjacency, and `T` is the number of named types:

| Operation | Time | New retained storage |
| --- | --- | --- |
| Read outgoing adjacency | `O(log32 V)` | none |
| Child lookup by name | `O(log32 V + log d)` unordered, `O(log32 V + d)` ordered | none |
| Read named type by name | `O(log32 T)` | none |
| Read vertex metadata | `O(log32 M)` plus metadata-map lookup | none |
| Add/remove edge | `O(d)` transient, then canonicalize at build | one changed primitive array |
| Add/remove named type | `O(log32 T)` | one trie path |
| Edit vertex metadata | `O(log32 M + keys(vertex))` | one trie path and metadata map |
| Publish `k` changed sources | `O(k log32 V)` | copied trie paths plus `k` arrays |
| Derive with no type edits | effectively root replacement | root edge paths; type registry is shared |

Here `M` is the number of vertices with metadata in the snapshot.

The vertex arena uses chunked arrays. Vertex creation and cleanup are serialized; published vertex
and registered-schema reads are lock-free.

## Why this representation

The existing [`graphql.schema.diffing.SchemaGraph`](../src/main/java/graphql/schema/diffing/SchemaGraph.java)
already demonstrates an external-vertex/external-edge model and supplies a useful element taxonomy.
It is intentionally schema-local and uses Java edge objects, property maps, lists, Guava tables, and
multimaps. That is appropriate for pairwise graph-edit analysis, but it duplicates storage for every
schema and is not a multi-version store.

Research considered these alternatives:

- [JGraphT `AsSubgraph`](https://github.com/jgrapht/jgrapht/blob/master/jgrapht-core/src/main/java/org/jgrapht/graph/AsSubgraph.java)
  represents a subset with per-view vertex and edge sets. Its own documentation notes that adjacent
  iteration can be proportional to base-graph degree, even for a small subset. Hundreds of views
  would also duplicate set entries.
- [JGraphT sparse CSR graphs](https://github.com/jgrapht/jgrapht/blob/master/jgrapht-opt/src/main/java/org/jgrapht/opt/graph/sparse/SparseIntDirectedGraph.java)
  are compact and fast for write-once/read-many graphs, but explicitly unmodifiable. A small schema
  edit requires rebuilding a complete representation.
- [JGraphT Sux4J graphs](https://github.com/jgrapht/jgrapht/tree/master/jgrapht-unimi-dsi/src/main/java/org/jgrapht/sux4j)
  and [WebGraph](https://github.com/vigna/webgraph) approach information-theoretic storage bounds for
  frozen graphs. They are candidates for archival compaction, not the primary mutable-lineage form.
- [RoaringBitmap](https://github.com/RoaringBitmap/RoaringBitmap) can efficiently represent the
  schema IDs containing each global edge. It is attractive for cross-schema set queries, but
  creating a derived schema requires touching every inherited edge or adding a parent/delta layer;
  direct outgoing lookup also needs a separate index. It remains a possible secondary membership
  index after profiling.
- Persistent collections such as [PCollections](https://github.com/hrldcpr/pcollections) avoid full
  copies, while [Capsule](https://github.com/usethesource/capsule) documents the CHAMP/HAMT design and
  its JVM memory benefits. graphql-java has a no-new-dependencies policy, so the prototype uses a
  small specialized integer-to-adjacency HAMT rather than a general collection library.
- Primitive libraries such as [fastutil](https://github.com/vigna/fastutil) and
  [Eclipse Collections](https://github.com/eclipse-collections/eclipse-collections) reduce boxing
  in mutable graphs, but do not by themselves provide persistent schema snapshots.

## Vertex identity

Vertex sharing is explicit rather than structural. Calling `newField("foo")` twice creates two
vertices even when their current properties match. A transformation reuses a vertex only when it
means to preserve that schema element.

Default values are intrinsic argument or input-field vertex data. Applied-directive arguments are
compact immutable values embedded in their `SUAppliedDirective` occurrence rather than independent
vertices. They retain graphql-java's `InputValueWithState` representation and exact input type
vertex ID, so not-set, literal, external, internal, and explicit-null states remain distinguishable
and programmatic schemas round trip without deriving types from directive definitions.

Changing an applied argument requires creating a new applied-directive occurrence and replacing the
container's `APPLIED_DIRECTIVE` edge. Unchanged argument values can be reused in the replacement
payload. External and internal values are retained by reference; snapshot immutability is shallow
with respect to a mutable value supplied by an application.

Directive repeatability and valid locations are also intrinsic. Locations are stored as a compact
bit mask. Directive-definition and applied-directive AST nodes are retained when available.

## User metadata

Each `SUSchema` can associate an immutable `Map<String, Object>` with any vertex in its universe.
The association belongs to the schema snapshot, so schemas sharing the same vertex may expose
different metadata. Transformations structurally share unchanged metadata and automatically move
schema-root metadata to the derived schema's new root.

The map is copied when supplied and rejects null keys and values. Values are retained by reference,
so immutability is shallow with respect to mutable application objects. Metadata has no GraphQL
semantics, is not included in SDL or `GraphQLSchema` export, and is never interpreted by the
universe. Metadata values containing vertices do not create liveness references.

A metadata entry itself does keep its keyed vertex live while the schema is registered. Removing
an edge does not remove metadata, allowing an annotation to remain dormant for later reattachment;
applications can explicitly clear metadata when that retention is unwanted. Applied-directive
arguments are embedded values rather than vertices and therefore cannot have independent metadata.

The unresolved policy question is whether a field's `TYPE` edge is part of field identity. The
prototype permits a schema snapshot to select a different single `TYPE` edge for the same field
vertex. If "specific element" includes its type, this operation should instead be rejected globally
and the caller should create a new field vertex. The same decision applies to argument types and
wrapper targets.

## Import coverage

`SUImporter` seeds a universe from an existing `GraphQLSchema`. It currently imports:

- object, interface, union, enum, scalar, and input-object types;
- fields, arguments, enum values, and input fields;
- list and non-null wrappers;
- operation roots, complete named-type membership, implementations, and union membership;
- argument and input-field defaults, preserving all `InputValueWithState` states;
- directive repeatability, valid locations, definitions, and modern applied-directive topology;
- applied-directive argument values, exact input types, and application AST nodes.

It does not yet preserve `GraphQLCodeRegistry`, coercing implementations, deprecation metadata,
general non-directive AST definitions/extensions, comparators, or source locations. These need typed
intrinsic payloads before round-trip conversion can be lossless.

## Lifecycle and compaction

Removing a schema does not immediately scan or reclaim vertices. A schema is live exactly while its
exact instance is present in the universe registry; holding an external Java reference does not
extend its lifetime. `cleanupUnusedVertices()` periodically reclaims vertices unused by registered
schemas.

Cleanup marks every registered root, every registered named type, every source and target in the
registered snapshots' complete persistent edge maps, and every vertex ID with schema metadata. It
identity-deduplicates structurally shared HAMT nodes, so unchanged subtrees are scanned once rather
than once per schema. Applied-directive argument type IDs are marked separately because those
references are intrinsic values rather than edges.

Marking must include dormant adjacency retained by a registered snapshot, not only the effective
graph reachable from its root. Otherwise cleanup would break the supported constant-scope
reattachment of unchanged subgraphs. The sweep clears unmarked arena slots and releases empty
chunks. Reclaimed IDs remain invalid permanently, while `getVertexCount()` reports retained
vertices. Cleanup must not overlap schema construction, import, or transformation; reads through
registered schemas can continue.

For long-lived universes with enough churn that sparse IDs or the monotonic ID limit become
material, compacting registered schemas into a new universe remains a possible archival operation.

## Required measurements

Before integrating with execution, benchmarks should compare:

- retained bytes for 1, 100, and 1,000 schemas at several edit percentages;
- initial import time;
- one-field add/remove/type-change latency;
- type and field lookup throughput;
- rooted full traversal;
- materialization back to `GraphQLSchema`;
- persistent HAMT against page-based copy-on-write and edge-membership bitmaps.

Measurements should use JMH for throughput/allocation and JOL or a heap histogram for retained size.
The result should determine whether to add lazy per-schema name/reverse indexes and whether packed
adjacency needs a small-degree linear-search specialization.
