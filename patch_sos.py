import sys

# Patch ViewModel
vm_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosRescueRequestViewModel.kt"
with open(vm_path, "r") as f:
    vm_content = f.read()

# 1. Add _selectedConditions and toggleCondition
target1 = """    fun updateCondition(condition: String) {
        _formData.update { it.copy(condition = condition) }
        clearValidationError()
    }"""
replacement1 = """    private val _selectedConditions = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val selectedConditions: kotlinx.coroutines.flow.StateFlow<Set<String>> = _selectedConditions.asStateFlow()

    fun toggleCondition(condition: String) {
        _selectedConditions.update { current ->
            if (current.contains(condition)) current - condition else current + condition
        }
        clearValidationError()
    }"""
if target1 in vm_content:
    vm_content = vm_content.replace(target1, replacement1)

# 2. Modify submitRequest
target2 = """        val form = _formData.value

        // Validation
        if (form.totalPeople <= 0) {
            setValidationError("Số người phải lớn hơn 0")
            return
        }
        if (form.condition.isBlank()) {
            setValidationError("Vui lòng chọn tình trạng hiện tại")
            return
        }"""
replacement2 = """        val form = _formData.value
        val conditionString = _selectedConditions.value.joinToString(" - ")

        // Validation
        if (form.totalPeople <= 0) {
            setValidationError("Số người phải lớn hơn 0")
            return
        }
        if (conditionString.isBlank()) {
            setValidationError("Vui lòng chọn tình trạng hiện tại")
            return
        }"""
if target2 in vm_content:
    vm_content = vm_content.replace(target2, replacement2)

target3 = """            neededItems = form.neededItems,
            condition = form.condition,
            description = form.description,"""
replacement3 = """            neededItems = form.neededItems,
            condition = conditionString,
            description = form.description,"""
if target3 in vm_content:
    vm_content = vm_content.replace(target3, replacement3)

target4 = """            batteryPercent = batteryProvider.getBatteryPercent(),
            urgency = calculateUrgency(form)
        )"""
replacement4 = """            batteryPercent = batteryProvider.getBatteryPercent(),
            urgency = calculateUrgency(form.copy(condition = conditionString))
        )"""
if target4 in vm_content:
    vm_content = vm_content.replace(target4, replacement4)

with open(vm_path, "w") as f:
    f.write(vm_content)

# Patch Screen
sc_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosRescueRequestScreen.kt"
with open(sc_path, "r") as f:
    sc_content = f.read()

# 1. Add selectedConditions
target5 = """    val formData by viewModel.formData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()"""
replacement5 = """    val formData by viewModel.formData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedConditions by viewModel.selectedConditions.collectAsState()"""
if target5 in sc_content:
    sc_content = sc_content.replace(target5, replacement5)

# 2. Replace conditions list and UI
target6 = """            val conditions = listOf("Nguy hiểm tính mạng", "Bị thương nặng", "Bị thương nhẹ", "Kẹt trong nhà", "Thiếu thức ăn/nước", "Mất liên lạc")
            FormSectionCard(
                icon = Icons.Default.MedicalServices,
                title = "Tình trạng hiện tại",
                isRequired = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    conditions.forEach { cond ->
                        val isSelected = formData.condition == cond
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
                                .clickable { viewModel.updateCondition(cond) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateCondition(cond) },
                                colors = RadioButtonDefaults.colors(selectedColor = EmergencyRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                cond,
                                fontSize = 14.sp,
                                color = if (isSelected) EmergencyRed else Color(0xFF1E293B),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }"""
replacement6 = """            val conditions = listOf(
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
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }"""
if target6 in sc_content:
    sc_content = sc_content.replace(target6, replacement6)
else:
    # Let's write the target6 to a file for debugging if it's missing
    pass

with open(sc_path, "w") as f:
    f.write(sc_content)

print("PATCH_DONE")
