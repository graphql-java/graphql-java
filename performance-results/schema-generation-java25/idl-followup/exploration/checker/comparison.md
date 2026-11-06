idl-base-20261106 vs idl-a-20261106; 1 fork(s) per version/case. Positive reductions are improvements.

| Fixture | Scope | Baseline ms/op | Candidate ms/op | Time reduction | Allocation reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | SchemaGenerator generation | 2.683 | 2.263 | +15.63% | +24.07% |
| 1 | FastSchemaGenerator generation | 1.724 | 1.278 | +25.86% | +40.38% |
| 1 | SchemaGenerator parse + generation | 4.053 | 3.575 | +11.80% | +18.29% |
| 1 | FastSchemaGenerator parse + generation | 3.079 | 2.699 | +12.34% | +27.46% |
| 2 | SchemaGenerator generation | 43.300 | 30.796 | +28.88% | +39.77% |
| 2 | FastSchemaGenerator generation | 28.648 | 16.340 | +42.96% | +62.11% |
| 2 | SchemaGenerator parse + generation | 60.290 | 48.300 | +19.89% | +32.44% |
| 2 | FastSchemaGenerator parse + generation | 45.973 | 33.613 | +26.89% | +45.64% |
| 3 | SchemaGenerator generation | 122.146 | 120.880 | +1.04% | -0.63% |
| 3 | FastSchemaGenerator generation | 72.546 | 69.274 | +4.51% | +11.07% |
| 3 | SchemaGenerator parse + generation | 188.484 | 183.449 | +2.67% | +3.38% |
| 3 | FastSchemaGenerator parse + generation | 139.137 | 132.879 | +4.50% | +6.20% |
| 4 | SchemaGenerator generation | 1565.515 | 1506.274 | +3.78% | +8.91% |
| 4 | FastSchemaGenerator generation | 866.235 | 836.112 | +3.48% | +7.62% |
| 4 | SchemaGenerator parse + generation | 2007.194 | 1984.425 | +1.13% | -3.15% |
| 4 | FastSchemaGenerator parse + generation | 1332.076 | 1290.583 | +3.11% | +1.44% |
| 5 | SchemaGenerator generation | 6919.910 | 7055.408 | -1.96% | -0.17% |
| 5 | FastSchemaGenerator generation | 3375.284 | 3330.263 | +1.33% | +4.54% |
| 5 | SchemaGenerator parse + generation | 7785.869 | 8156.179 | -4.76% | -0.30% |
| 5 | FastSchemaGenerator parse + generation | 4287.248 | 4186.949 | +2.34% | +2.95% |

Warmup screens flagged: 0/40. Actual allocation stalls: 0.
