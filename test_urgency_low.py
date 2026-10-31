def calculate(condition_str, adults, children, elderly, needed_str):
    conditions = set(x.strip() for x in condition_str.split("-") if x.strip())
    needed = set(x.strip() for x in needed_str.split("-") if x.strip())
    
    has_vulnerable = children > 0 or elderly > 0
    needs_boat = "Thuyền" in needed or "Áo phao" in needed
    
    # CRITICAL
    critical_conds = {"Nguy hiểm tính mạng", "Bất tỉnh", "Khó thở", "Chảy máu nhiều", "Bị thương nặng"}
    if any(c in conditions for c in critical_conds):
        return "CRITICAL"
    if "Kẹt trong nhà" in conditions and has_vulnerable:
        return "CRITICAL"
    if "Kẹt trong nhà" in conditions and needs_boat:
        return "CRITICAL"
        
    # HIGH
    if "Mất liên lạc" in conditions:
        return "HIGH"
    if "Kẹt trong nhà" in conditions:
        return "HIGH"
    if "Bị thương nhẹ" in conditions and has_vulnerable:
        return "HIGH"
    if "Thiếu thức ăn/nước" in conditions and has_vulnerable:
        return "HIGH"
    if "Thuốc men" in needed or needs_boat:
        return "HIGH"
        
    # MEDIUM
    if "Bị thương nhẹ" in conditions and not has_vulnerable:
        return "MEDIUM"
    if "Thiếu thức ăn/nước" in conditions and not has_vulnerable:
        return "MEDIUM"
    if "Thực phẩm" in needed or "Nước uống" in needed:
        return "MEDIUM"
        
    # LOW
    return "LOW"

tests = [
    ("An toàn, chỉ cần hỗ trợ", 2, 0, 0, "Chăn màn - Điện thoại sạc", "LOW"),
    ("An toàn, chỉ cần hỗ trợ - Mất liên lạc", 2, 0, 0, "", "HIGH"),
    ("An toàn, chỉ cần hỗ trợ - Bị thương nhẹ", 2, 0, 0, "", "MEDIUM"),
    ("An toàn, chỉ cần hỗ trợ - Bất tỉnh", 2, 0, 0, "", "CRITICAL"),
    ("An toàn, chỉ cần hỗ trợ - Kẹt trong nhà", 0, 1, 0, "", "CRITICAL"),
    ("An toàn, chỉ cần hỗ trợ - Thiếu thức ăn/nước", 2, 0, 0, "", "MEDIUM")
]

for i, (cond, a, c, e, needs, expected) in enumerate(tests):
    res = calculate(cond, a, c, e, needs)
    if res != expected:
        print(f"TEST {i+1} FAILED: expected {expected}, got {res}")
    else:
        print(f"TEST {i+1} PASSED")
