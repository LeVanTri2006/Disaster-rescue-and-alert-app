import sys

vm_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosRescueRequestViewModel.kt"
with open(vm_path, "r") as f:
    vm_content = f.read()

target = """    private fun calculateUrgency(form: SosFormData): SosUrgency {
        val hasVulnerable = form.children > 0 || form.elderly > 0
        val needsBoatOrLifeJacket = form.neededItems.contains("Thuyền") || form.neededItems.contains("Áo phao")

        // 1. CRITICAL
        if (form.condition == "Nguy hiểm tính mạng" || form.condition == "Bị thương nặng") {
            return SosUrgency.CRITICAL
        }
        if (form.condition == "Kẹt trong nhà" && (hasVulnerable || needsBoatOrLifeJacket)) {
            return SosUrgency.CRITICAL
        }

        // 2. HIGH
        if (form.condition == "Mất liên lạc" || form.condition == "Kẹt trong nhà") {
            return SosUrgency.HIGH
        }
        if (form.condition == "Bị thương nhẹ" && hasVulnerable) {
            return SosUrgency.HIGH
        }
        if (form.condition == "Thiếu thức ăn/nước" && hasVulnerable) {
            return SosUrgency.HIGH
        }
        if (form.neededItems.contains("Thuốc men") || needsBoatOrLifeJacket) {
            return SosUrgency.HIGH
        }

        // 3. MEDIUM
        if (form.condition == "Bị thương nhẹ" || form.condition == "Thiếu thức ăn/nước") {
            return SosUrgency.MEDIUM
        }
        if (form.neededItems.contains("Thực phẩm") || form.neededItems.contains("Nước uống")) {
            return SosUrgency.MEDIUM
        }

        // 4. LOW
        return SosUrgency.LOW
    }"""

replacement = """    private fun calculateUrgency(form: SosFormData): SosUrgency {
        val conditions = form.condition.split("-").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val neededItems = form.neededItems.toSet()

        val hasVulnerable = form.children > 0 || form.elderly > 0
        val needsBoat = neededItems.contains("Thuyền") || neededItems.contains("Áo phao")

        // 1. CRITICAL
        val criticalConditions = setOf("Nguy hiểm tính mạng", "Bất tỉnh", "Khó thở", "Chảy máu nhiều", "Bị thương nặng")
        if (conditions.any { it in criticalConditions }) {
            return SosUrgency.CRITICAL
        }
        if (conditions.contains("Kẹt trong nhà") && hasVulnerable) {
            return SosUrgency.CRITICAL
        }
        if (conditions.contains("Kẹt trong nhà") && needsBoat) {
            return SosUrgency.CRITICAL
        }

        // 2. HIGH
        if (conditions.contains("Mất liên lạc")) {
            return SosUrgency.HIGH
        }
        if (conditions.contains("Kẹt trong nhà")) {
            return SosUrgency.HIGH
        }
        if (conditions.contains("Bị thương nhẹ") && hasVulnerable) {
            return SosUrgency.HIGH
        }
        if (conditions.contains("Thiếu thức ăn/nước") && hasVulnerable) {
            return SosUrgency.HIGH
        }
        if (neededItems.contains("Thuốc men") || needsBoat) {
            return SosUrgency.HIGH
        }

        // 3. MEDIUM
        if (conditions.contains("Bị thương nhẹ") && !hasVulnerable) {
            return SosUrgency.MEDIUM
        }
        // As per Test 8, "Thiếu thức ăn/nước" without vulnerable people and without food/water needed -> LOW
        if (neededItems.contains("Thực phẩm") || neededItems.contains("Nước uống")) {
            return SosUrgency.MEDIUM
        }

        // 4. LOW
        return SosUrgency.LOW
    }"""

if target in vm_content:
    vm_content = vm_content.replace(target, replacement)
    with open(vm_path, "w") as f:
        f.write(vm_content)
    print("PATCH_DONE")
else:
    print("TARGET_NOT_FOUND")
