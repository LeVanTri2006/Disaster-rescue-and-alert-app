package com.example.bai1.rescue.routing.algorithm

import com.example.bai1.rescue.routing.model.RoadEdge

/**
 * Khóa trạng thái tìm kiếm trong thuật toán Uniform Cost Search mở rộng (Edge-based Search State).
 *
 * Cho phép phân biệt các trạng thái duyệt đến cùng một [nodeId] nhưng từ các hướng đi vào [incomingEdgeId]
 * khác nhau, giải quyết bài toán Turn Restrictions (hạn chế rẽ từ cạnh vào sang cạnh ra tại nút giao).
 *
 * @property nodeId ID của nút hiện tại đang xét.
 * @property incomingEdgeId ID của cạnh vừa đi qua để đến [nodeId] (null tại nút xuất phát).
 * @property restrictionStateId ID trạng thái mở rộng phục vụ DFA cho via-way restrictions trong tương lai (mặc định 0).
 */
data class UcsStateKey(
    val nodeId: String,
    val incomingEdgeId: String?,
    val restrictionStateId: Int = 0
)

/**
 * Nút trạng thái tìm kiếm được lưu trữ trong PriorityQueue của UCS.
 *
 * @property stateKey Khóa trạng thái tìm kiếm (nodeId, incomingEdgeId, restrictionStateId).
 * @property costG Tổng chi phí tích lũy g(n) từ nút xuất phát đến trạng thái này.
 * @property insertionOrder Bộ đếm thứ tự chèn phục vụ tie-breaking có tính đơn định (deterministic FIFO).
 */
data class UcsSearchNode(
    val stateKey: UcsStateKey,
    val costG: Double,
    val insertionOrder: Long = 0L
) : Comparable<UcsSearchNode> {
    override fun compareTo(other: UcsSearchNode): Int {
        val costCmp = this.costG.compareTo(other.costG)
        return if (costCmp != 0) {
            costCmp
        } else {
            this.insertionOrder.compareTo(other.insertionOrder)
        }
    }
}

/**
 * Bước chuyển tiếp từ trạng thái trước đó sang trạng thái hiện tại trong cây tìm kiếm UCS,
 * phục vụ tái tạo tuyến đường (Route Reconstruction).
 *
 * @property previousStateKey Trạng thái trước đó trong quá trình tìm kiếm.
 * @property traversedEdge Cạnh đường bộ đã đi qua giữa hai trạng thái.
 */
data class ParentTransition(
    val previousStateKey: UcsStateKey,
    val traversedEdge: RoadEdge
)

