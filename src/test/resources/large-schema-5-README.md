# Large Schema File - Split for Windows Compatibility

The file `large-schema-5.graphqls` (11.3 MB) has been split into multiple parts to comply with the 10MB file size limit.

## Parts

- `large-schema-5.graphqls.part1` - First part (6.2 MB)
- `large-schema-5.graphqls.part2` - Second part (4.7 MB)

## Reassembly Instructions

To reassemble the original file, use one of the following methods:

### On Linux/macOS:
```bash
cat large-schema-5.graphqls.part1 large-schema-5.graphqls.part2 > large-schema-5.graphqls
```

### On Windows (PowerShell):
```powershell
Get-Content large-schema-5.graphqls.part1, large-schema-5.graphqls.part2 | Set-Content large-schema-5.graphqls
```

### On Windows (Command Prompt):
```cmd
copy /b large-schema-5.graphqls.part1+large-schema-5.graphqls.part2 large-schema-5.graphqls
```

## For Test Code

If test code references `large-schema-5.graphqls`, update it to either:
1. Reassemble the file programmatically before use
2. Update the code to read from both parts sequentially
