import sys

file_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosDetailScreen.kt"

with open(file_path, "r") as f:
    content = f.read()

# Fix Row crossAxisAlignment -> verticalAlignment
content = content.replace(
    'crossAxisAlignment = CrossAxisAlignment.Start',
    'verticalAlignment = Alignment.Top'
)

with open(file_path, "w") as f:
    f.write(content)

