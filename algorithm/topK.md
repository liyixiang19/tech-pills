# TopK 问题（第K个最大元素）

## 题目

LeetCode 215：https://leetcode.cn/problems/kth-largest-element-in-an-array/

给定整数数组 nums 和整数 k，返回数组中第 k 个最大的元素。要求时间复杂度优于 O(n log n)。

**实际应用场景：** 压测 1 千万请求，求 P90 响应时间（即第 n*0.9 小的值），本质就是 TopK。

---

## 解法一：QuickSelect（最优解）

### 核心思路

类似快排的 partition，但每次只递归一边，平均 O(n)。

### 代码（Hoare partition）

```java
class Solution {
    int quickselect(int[] nums, int l, int r, int k) {
        if (l == r) return nums[k];
        int x = nums[l], i = l - 1, j = r + 1;
        while (i < j) {
            do i++; while (nums[i] < x);
            do j--; while (nums[j] > x);
            if (i < j) {
                int tmp = nums[i];
                nums[i] = nums[j];
                nums[j] = tmp;
            }
        }
        // partition 后: nums[l..j] <= x, nums[j+1..r] >= x
        if (k <= j) return quickselect(nums, l, j, k);
        else return quickselect(nums, j + 1, r, k);
    }

    public int findKthLargest(int[] nums, int k) {
        int n = nums.length;
        // 第 k 大 = 第 n-k 小（0-indexed）
        return quickselect(nums, 0, n - 1, n - k);
    }
}
```

### 关键点

1. **pivot 选取：** `x = nums[l]` 取最左边元素，简单但最坏 O(n²)
2. **Hoare partition：** 双指针从两端向中间扫，左边都 ≤ x，右边都 ≥ x
3. **只递归一边：** 根据 k 和 j 的关系决定递归左半还是右半

### 复杂度

| | 平均 | 最坏 |
|---|---|---|
| 时间 | O(n) | O(n²) |
| 空间 | O(log n) 递归栈 | O(n) |

### 最坏情况

数组已排序 + 固定取左边做 pivot → 每次只排除 1 个元素：
```
n + (n-1) + (n-2) + ... + 1 = O(n²)
```

### 优化：随机 pivot

```java
int rand = l + (int)(Math.random() * (r - l + 1));
int tmp = nums[l]; nums[l] = nums[rand]; nums[rand] = tmp;
int x = nums[l]; // 后续逻辑不变
```

随机化后期望 O(n)，任何固定输入都无法让算法稳定退化。

---

## 解法二：小顶堆

维护大小为 k 的小顶堆，堆顶就是第 k 大。

```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> heap = new PriorityQueue<>(); // 小顶堆
    for (int num : nums) {
        heap.offer(num);
        if (heap.size() > k) {
            heap.poll(); // 弹出最小的，保留最大的 k 个
        }
    }
    return heap.peek(); // 堆顶 = 第 k 大
}
```

**复杂度：** O(n log k) 时间，O(k) 空间

**适用场景：** 数据流式到达、不能一次性加载到内存时。

---

## 解法三：计数排序（响应时间等有界整数场景）

```java
// 假设响应时间范围 0~10000ms
int[] count = new int[10001];
for (int rt : data) count[rt]++;

long cumulative = 0;
long target = (long)(n * 0.9); // P90
for (int i = 0; i < count.length; i++) {
    cumulative += count[i];
    if (cumulative >= target) {
        return i; // P90 的值
    }
}
```

**复杂度：** O(n + 范围) 时间，O(范围) 空间

---

## 方案选择

| 场景 | 推荐方案 | 复杂度 |
|------|---------|--------|
| 面试通用解 | QuickSelect + 随机pivot | O(n) 平均 |
| 数据流/内存受限 | 小顶堆 | O(n log k) |
| 值域有界（如响应时间） | 计数排序 | O(n) |
| 简单实现 | 排序取下标 | O(n log n) |

---

## 面试话术

> 这是 TopK 问题，最优解是 QuickSelect，基于快排的 partition 每次只递归一边，平均 O(n)。为了避免已排序数组导致的 O(n²) 最坏情况，实际中用随机 pivot。如果是流式数据或内存不够，可以用大小为 k 的小顶堆，O(n log k)。如果值域有界（比如压测响应时间 0~10s），计数排序最直接。
