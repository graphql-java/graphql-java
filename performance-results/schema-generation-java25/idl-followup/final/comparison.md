baseline vs optimized; 3 fork(s) per version/case. Positive reductions are improvements.

| Fixture | Scope | Baseline ms/op | Candidate ms/op | Time reduction | Allocation reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | SchemaGenerator generation | 2.735 | 2.272 | +16.94% | +25.48% |
| 1 | FastSchemaGenerator generation | 1.717 | 1.275 | +25.77% | +43.62% |
| 1 | SchemaGenerator parse + generation | 4.040 | 3.622 | +10.34% | +18.27% |
| 1 | FastSchemaGenerator parse + generation | 3.087 | 2.604 | +15.64% | +28.69% |
| 2 | SchemaGenerator generation | 42.931 | 31.493 | +26.64% | +37.93% |
| 2 | FastSchemaGenerator generation | 29.093 | 16.572 | +43.04% | +61.84% |
| 2 | SchemaGenerator parse + generation | 59.669 | 47.915 | +19.70% | +32.75% |
| 2 | FastSchemaGenerator parse + generation | 46.040 | 34.001 | +26.15% | +45.62% |
| 3 | SchemaGenerator generation | 122.737 | 119.655 | +2.51% | +6.37% |
| 3 | FastSchemaGenerator generation | 73.937 | 68.631 | +7.18% | +13.40% |
| 3 | SchemaGenerator parse + generation | 186.753 | 187.189 | -0.23% | +2.86% |
| 3 | FastSchemaGenerator parse + generation | 137.525 | 133.196 | +3.15% | +8.27% |
| 4 | SchemaGenerator generation | 1539.894 | 1507.842 | +2.08% | +4.45% |
| 4 | FastSchemaGenerator generation | 867.287 | 795.741 | +8.25% | +13.78% |
| 4 | SchemaGenerator parse + generation | 2017.339 | 1958.017 | +2.94% | +4.12% |
| 4 | FastSchemaGenerator parse + generation | 1333.891 | 1273.957 | +4.49% | +6.44% |
| 5 | SchemaGenerator generation | 7088.679 | 6840.991 | +3.49% | +2.88% |
| 5 | FastSchemaGenerator generation | 3405.253 | 3247.720 | +4.63% | +6.44% |
| 5 | SchemaGenerator parse + generation | 7903.000 | 7809.971 | +1.18% | +1.62% |
| 5 | FastSchemaGenerator parse + generation | 4240.320 | 4109.960 | +3.07% | +3.76% |

Warmup screens flagged: 2/120. Actual allocation stalls: 0.
