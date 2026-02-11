# large-schema-5 — Split File

`large-schema-5.graphqls` (11.3 MB) exceeds the 10 MB file-size limit and has been split into two parts.
The split is at a type boundary so each part contains only complete type definitions.

## Reassembly

```bash
# Linux / macOS
cat large-schema-5.graphqls.part1 large-schema-5.graphqls.part2 > large-schema-5.graphqls

# Windows (PowerShell)
Get-Content large-schema-5.graphqls.part1, large-schema-5.graphqls.part2 | Set-Content large-schema-5.graphqls
```
