#!/bin/bash

# Directory containing docs
docs_dir="docs"

# Fetch the current date and calculate the date 30 days ago
current_date=$(date +%s)
thirty_days_ago=$(date -d "-30 days" +%s)

# Find outdated .md files
outdated_files=()
for file in $(find "$docs_dir" -name "*.md"); do
  # Get the last modified date of the file from git
  last_modified_date=$(git log -1 --format="%at" -- "$file")

  # Check if the file is older than 30 days
  if [[ $last_modified_date -lt $thirty_days_ago ]]; then
    outdated_files+=("$file")
  fi
done

# If there are outdated files, output them and exit with status 1
if [[ ${#outdated_files[@]} -gt 0 ]]; then
  echo "The following markdown files are outdated (not modified in the last 30 days):"
  for file in "${outdated_files[@]}"; do
    echo "$file"
  done
  exit 1
else
  echo "All markdown files are up-to-date."
fi