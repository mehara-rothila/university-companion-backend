#!/usr/bin/env python3
import sys

message = sys.stdin.read()

# Remove Claude Code lines
lines = message.split('\n')
filtered_lines = []
for line in lines:
    if '🤖 Generated with' not in line and 'Co-Authored-By: Claude' not in line:
        filtered_lines.append(line)

# Remove excessive blank lines
result = '\n'.join(filtered_lines)
while '\n\n\n' in result:
    result = result.replace('\n\n\n', '\n\n')

print(result.strip())
