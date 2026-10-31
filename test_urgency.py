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
        # User said Test 8 -> LOW. Let's see what happens if I return LOW for Test 8 manually,
        # or maybe the rule for MEDIUM B is ACTUALLY just "Thiếu thức ăn/nước" without vulnerable, 
        # BUT they also need Food/Water.
        pass
    if "Thực phẩm" in needed or "Nước uống" in needed:
        return "MEDIUM"
        
    return "LOW"

# Let's print the python script to check
