#!/bin/bash

# Script to install Git hooks for this repository
# This configures Git to use hooks from the .githooks directory

set -e

# Get the repository root directory
REPO_ROOT=$(git rev-parse --show-toplevel)

echo "Installing Git hooks for graphql-java..."

# Configure Git to use the .githooks directory
git config core.hooksPath "$REPO_ROOT/.githooks"

echo "✓ Git hooks installed successfully!"
echo ""
echo "The following hooks are now active:"
echo "  - pre-commit: Checks for Windows-incompatible filenames and large files"
echo ""
echo "To disable hooks temporarily, use: git commit --no-verify"
echo "To uninstall hooks, run: git config --unset core.hooksPath"
