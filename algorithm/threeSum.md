# 三数之和（LeetCode 15）

## 题目

给定一个整数数组 nums，找出所有和为 0 的三元组 `[nums[i], nums[j], nums[k]]`，要求不重复。

## 解题思路：排序 + 固定一个数 + 双指针

1. 先排序
2. 外层循环固定第一个数 `nums[i]`
3. 内层用双指针 left、right 从两端往中间逼近，找另外两个数

### 为什么要排序？

排序后才能根据 sum 的大小判断指针该往哪边移动：
- `sum > 0` → 说明太大了，right 左移
- `sum < 0` → 说明太小了，left 右移
- `sum == 0` → 找到一组解

## 关键细节

### 1. 去重

两个地方需要去重：

**外层去重**：固定的第一个数如果和前一个相同，跳过

```java
if (i > 0 && nums[i] == nums[i-1]) continue;
```

**内层去重**：找到解之后，跳过 left 和 right 的重复值

```java
while(left < right && nums[left] == nums[left+1]) left++;
while(left < right && nums[right] == nums[right-1]) right--;
```

### 2. 去重只在 sum == 0 时执行

不能把去重放在 while 循环末尾每次都执行，否则会漏解。原因：
- `sum > 0` 时 right-- 自然会处理
- `sum < 0` 时 left++ 自然会处理
- 只有 `sum == 0` 时不去重才会产生重复三元组

### 3. 提前终止

```java
if (nums[i] > 0) break;
```

排序后最小的数都 > 0，三个正数不可能和为 0。

## 最终代码

```java
class Solution {
    public List<List<Integer>> threeSum(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        Arrays.sort(nums);

        for (int i = 0; i < nums.length - 2; i++) {
            // 提前终止
            if (nums[i] > 0) break;

            // 外层去重
            if (i > 0 && nums[i] == nums[i - 1]) continue;

            int left = i + 1;
            int right = nums.length - 1;

            while (left < right) {
                int sum = nums[i] + nums[left] + nums[right];

                if (sum == 0) {
                    res.add(Arrays.asList(nums[i], nums[left], nums[right]));
                    // 内层去重（只在找到解时执行）
                    while (left < right && nums[left] == nums[left + 1]) left++;
                    while (left < right && nums[right] == nums[right - 1]) right--;
                    left++;
                    right--;
                } else if (sum > 0) {
                    right--;
                } else {
                    left++;
                }
            }
        }
        return res;
    }
}
```

## 复杂度

| 维度 | 复杂度 | 说明 |
|------|--------|------|
| 时间 | O(n²) | 排序 O(n log n) + 双指针遍历 O(n²) |
| 空间 | O(1) | 不算结果集，只用了几个指针变量 |

## 易错点总结

1. 去重位置放错（放在 while 末尾每次执行 → 漏解）
2. 双指针移动方向搞反（sum > 0 应该 right--，不是 left++）
3. 外层循环边界是 `nums.length - 2`，不是 `nums.length - 1`
4. 找到解后 left 和 right 都要移动，不能只动一个
