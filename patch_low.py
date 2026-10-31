import sys

# Patch ViewModel
vm_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosRescueRequestViewModel.kt"
with open(vm_path, "r") as f:
    vm_content = f.read()

target_vm = """        if (conditions.contains("Bị thương nhẹ") && !hasVulnerable) {
            return SosUrgency.MEDIUM
        }
        // As per Test 8, "Thiếu thức ăn/nước" without vulnerable people and without food/water needed -> LOW
        if (neededItems.contains("Thực phẩm") || neededItems.contains("Nước uống")) {
            return SosUrgency.MEDIUM
        }"""

replacement_vm = """        if (conditions.contains("Bị thương nhẹ") && !hasVulnerable) {
            return SosUrgency.MEDIUM
        }
        if (conditions.contains("Thiếu thức ăn/nước") && !hasVulnerable) {
            return SosUrgency.MEDIUM
        }
        if (neededItems.contains("Thực phẩm") || neededItems.contains("Nước uống")) {
            return SosUrgency.MEDIUM
        }"""

if target_vm in vm_content:
    vm_content = vm_content.replace(target_vm, replacement_vm)
    with open(vm_path, "w") as f:
        f.write(vm_content)
    print("VM_PATCH_DONE")
else:
    print("VM_TARGET_NOT_FOUND")

# Patch Screen
sc_path = "/home/vantri/AndroidStudioProjects/bai1/app/src/main/java/com/example/bai1/presentation/sos/SosRescueRequestScreen.kt"
with open(sc_path, "r") as f:
    sc_content = f.read()

target_sc = """                "Kẹt trong nhà",
                "Thiếu thức ăn/nước",
                "Mất liên lạc"
            )"""

replacement_sc = """                "Kẹt trong nhà",
                "Thiếu thức ăn/nước",
                "Mất liên lạc",
                "An toàn, chỉ cần hỗ trợ"
            )"""

if target_sc in sc_content:
    sc_content = sc_content.replace(target_sc, replacement_sc)
    with open(sc_path, "w") as f:
        f.write(sc_content)
    print("SC_PATCH_DONE")
else:
    print("SC_TARGET_NOT_FOUND")
