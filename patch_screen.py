import sys

sc_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosRescueRequestScreen.kt"
with open(sc_path, "r") as f:
    sc_content = f.read()

# 1. Add selectedConditions
target5 = """    val formData by viewModel.formData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()"""
replacement5 = """    val formData by viewModel.formData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedConditions by viewModel.selectedConditions.collectAsState()"""
if target5 in sc_content and "val selectedConditions" not in sc_content:
    sc_content = sc_content.replace(target5, replacement5)

# 2. Extract and replace the block
start = sc_content.find('val conditions = listOf("Nguy hiểm tính mạng"')
end = sc_content.find("            // 5. Description")
if start != -1 and end != -1:
    target_block = sc_content[start:end]
    replacement_block = """val conditions = listOf(
                "Nguy hiểm tính mạng",
                "Bất tỉnh",
                "Khó thở",
                "Chảy máu nhiều",
                "Bị thương nặng",
                "Bị thương nhẹ",
                "Kẹt trong nhà",
                "Thiếu thức ăn/nước",
                "Mất liên lạc"
            )
            FormSectionCard(
                icon = Icons.Default.MedicalServices,
                title = "Tình trạng hiện tại",
                isRequired = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conditions.forEach { cond ->
                        val isSelected = selectedConditions.contains(cond)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) EmergencyRed.copy(alpha = 0.08f) else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (isSelected) EmergencyRed else Color(0xFFE2E8F0),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.toggleCondition(cond) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleCondition(cond) },
                                colors = CheckboxDefaults.colors(checkedColor = EmergencyRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                cond,
                                fontSize = 14.sp,
                                color = if (isSelected) EmergencyRed else Color(0xFF1E293B),
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }

"""
    sc_content = sc_content.replace(target_block, replacement_block)
    
with open(sc_path, "w") as f:
    f.write(sc_content)

print("SCREEN_PATCH_DONE")
