import sys

file_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosDetailScreen.kt"

with open(file_path, "r") as f:
    content = f.read()

# Fix 1: EmergencyStatusCard urgency
content = content.replace(
    'sos.urgency.isNotBlank()',
    'sos.urgency.name.isNotBlank()'
).replace(
    'sos.urgency.uppercase()',
    'sos.urgency.name.uppercase()'
)

# Fix 2: NeedsCard argument type
content = content.replace(
    'fun NeedsCard(neededItems: List<String>) {',
    'fun NeedsCard(neededItems: Set<String>) {'
)

with open(file_path, "w") as f:
    f.write(content)

