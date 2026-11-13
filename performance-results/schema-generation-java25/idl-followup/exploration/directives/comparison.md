idl-a-20261106 vs idl-b2-20261106; 1 fork(s) per version/case. Positive reductions are improvements.

| Fixture | Scope | Baseline ms/op | Candidate ms/op | Time reduction | Allocation reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | SchemaGenerator generation | 2.265 | 2.263 | +0.10% | +1.65% |
| 1 | FastSchemaGenerator generation | 1.282 | 1.270 | +0.94% | +4.16% |
| 1 | SchemaGenerator parse + generation | 3.627 | 3.646 | -0.52% | -2.82% |
| 1 | FastSchemaGenerator parse + generation | 2.643 | 2.599 | +1.66% | +1.08% |
| 2 | SchemaGenerator generation | 31.081 | 30.516 | +1.82% | -1.28% |
| 2 | FastSchemaGenerator generation | 16.494 | 16.438 | +0.34% | +0.03% |
| 2 | SchemaGenerator parse + generation | 49.177 | 47.478 | +3.45% | +4.22% |
| 2 | FastSchemaGenerator parse + generation | 34.070 | 33.717 | +1.04% | +0.56% |
| 3 | SchemaGenerator generation | 120.619 | 118.515 | +1.74% | +1.68% |
| 3 | FastSchemaGenerator generation | 70.981 | 68.759 | +3.13% | +5.21% |
| 3 | SchemaGenerator parse + generation | 182.328 | 181.420 | +0.50% | +1.16% |
| 3 | FastSchemaGenerator parse + generation | 133.851 | 131.738 | +1.58% | +2.18% |
| 4 | SchemaGenerator generation | 1537.073 | 1555.275 | -1.18% | -4.96% |
| 4 | FastSchemaGenerator generation | 848.858 | 811.756 | +4.37% | +3.01% |
| 4 | SchemaGenerator parse + generation | 2000.079 | 2000.383 | -0.02% | +1.20% |
| 4 | FastSchemaGenerator parse + generation | 1300.542 | 1270.426 | +2.32% | +3.13% |
| 5 | SchemaGenerator generation | 6933.455 | 6820.995 | +1.62% | +0.97% |
| 5 | FastSchemaGenerator generation | 3374.965 | 3208.553 | +4.93% | +2.73% |
| 5 | SchemaGenerator parse + generation | 7984.203 | 8153.234 | -2.12% | -5.29% |
| 5 | FastSchemaGenerator parse + generation | 4217.188 | 4073.665 | +3.40% | +1.79% |

Warmup screens flagged: 0/40. Actual allocation stalls: 0.
