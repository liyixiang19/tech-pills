# 和为 K 的子数组（前缀和 + HashMap）

## 题目

LeetCode 560：https://leetcode.cn/problems/subarray-sum-equals-k/

给定整数数组 nums 和整数 k，统计并返回该数组中和为 k 的连续子数组的个数。

---

## 核心思想：前缀和 + HashMap

### 什么是前缀和

从数组开头累加到某个位置的总和：

```
数组:      [2,  1,  3,  -1,  4]
prefix[0] = 2
prefix[1] = 2 + 1 = 3
prefix[2] = 2 + 1 + 3 = 6
prefix[3] = 2 + 1 + 3 + (-1) = 5
prefix[4] = 2 + 1 + 3 + (-1) + 4 = 9
```

### 关键公式

```
子数组 [i+1..j] 的和 = prefix[j] - prefix[i]
```

任意子数组的和 = 两个前缀和的差。就像量尺子：右端刻度 - 左端刻度 = 中间长度。

### 转化为查找问题

```
prefix[j] - prefix[i] = k
→ prefix[i] = prefix[j] - k
→ 对于当前前缀和 prefix[j]，查之前有没有出现过 prefix[j] - k
→ 用 HashMap O(1) 查找
```

---

## 代码

```java
class Solution {
    public int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(0, 1);  // 前缀和为0出现1次（空前缀）

        int prefixSum = 0;
        int res = 0;

        for (int i = 0; i < nums.length; i++) {
            prefixSum += nums[i];                          // 1. 算：当前前缀和
            res += map.getOrDefault(prefixSum - k, 0);    // 2. 查：之前有几个前缀和 = prefixSum - k
            map.put(prefixSum, map.getOrDefault(prefixSum, 0) + 1);  // 3. 存：记录当前前缀和
        }

        return res;
    }
}
```

---

## 关键顺序：算 → 查 → 存

| 步骤 | 操作 | 原因 |
|------|------|------|
| 算 | `prefixSum += nums[i]` | 得到从头到当前位置的累加和 |
| 查 | `map.get(prefixSum - k)` | 找之前有几个前缀和满足条件 |
| 存 | `map.put(prefixSum, ...)` | 记录当前前缀和，供后续查找 |

顺序不能乱：先查再存，保证不会"自己查到自己"。

---

## 易错点

1. **查的是 `prefixSum - k`，不是 `nums[i] - k`。** 前缀和是累加值，不是单个元素。

2. **`map.put(0, 1)` 不能漏。** 处理从下标 0 开始的子数组恰好和为 k 的情况（此时 prefixSum - k = 0，需要能查到）。

3. **不能用滑动窗口。** 数组元素可以为负数，窗口扩大和不一定增大，无法判断收缩方向。

4. **map 存的是前缀和出现的次数，不是查找结果。** `map.put(prefixSum, map.getOrDefault(prefixSum, 0) + 1)` 是累计这个前缀和出现了几次。

---

## 举例走一遍

```
nums = [1, 2, 3], k = 3

i=0: prefixSum=1, 查 map.get(1-3)=map.get(-2)=0, 存 map={0:1, 1:1}
i=1: prefixSum=3, 查 map.get(3-3)=map.get(0)=1,  res=1, 存 map={0:1, 1:1, 3:1}
i=2: prefixSum=6, 查 map.get(6-3)=map.get(3)=1,  res=2, 存 map={0:1, 1:1, 3:1, 6:1}

结果: 2（子数组 [1,2] 和 [3]）
```

---

## 复杂度

- 时间：O(n)，一次遍历
- 空间：O(n)，HashMap 最多存 n 个前缀和

---

## HashMap 在这题中的角色

HashMap 是一个"历史记录本"：
- key = 之前出现过的前缀和
- value = 这个前缀和出现了几次

每到一个新位置，不用回头遍历所有之前的位置，O(1) 就能知道有几个合法的起点。这就是用空间换时间的经典套路。
