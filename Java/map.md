# HashMap 面试知识点

## 一、底层数据结构

- **JDK 1.7：** 数组 + 链表
- **JDK 1.8：** 数组 + 链表 + 红黑树

当链表长度 ≥ 8 且数组长度 ≥ 64 时，链表转红黑树；当红黑树节点 ≤ 6 时退化为链表。

> 为什么是 8？泊松分布下，链表长度达到 8 的概率极低（约千万分之六），这是空间和时间的折中。

---

## 二、put 流程（JDK 1.8）

1. 对 key 调用 `hash()` 方法计算哈希值（高16位异或低16位，扰动函数）
2. 用 `(n - 1) & hash` 定位桶下标（n 为数组长度，必须是 2 的幂）
3. 桶为空 → 直接放入新节点
4. 桶不为空 → 判断首节点 key 是否相同（`==` 或 `equals`）
   - 相同 → 覆盖 value
   - 不同 → 遍历链表/红黑树插入（**尾插法**，1.7 是头插法）
5. 插入后判断链表长度是否 ≥ 8，是则尝试树化
6. 判断 `size > threshold`，是则扩容

---

## 三、扩容机制（resize）

- 默认初始容量 16，负载因子 0.75，阈值 = 容量 × 负载因子 = 12
- 扩容为原来的 **2 倍**
- JDK 1.8 扩容优化：不需要重新计算 hash，只需看 `hash & oldCap` 的结果
  - 为 0 → 位置不变
  - 为 1 → 新位置 = 原位置 + oldCap

> 容量必须是 2 的幂：保证 `(n-1) & hash` 等价于取模，且扩容时可以用位运算快速判断新位置。

---

## 四、hash() 扰动函数

**JDK 1.7：**
```java
static int hash(int h) {
    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
}
```
4次右移 + 5次异或，扰动较重。

**JDK 1.8：**
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
1次右移 + 1次异或。高16位与低16位异或，让高位也参与运算，减少哈希碰撞。因为有红黑树兜底，即使碰撞多一些最坏也是 O(log n)，所以扰动可以简化。

---

## 五、头插法 vs 尾插法

### 头插法（JDK 1.7）

新节点插入到链表**头部**，原来的头节点变成新节点的 next。

```
插入顺序: A → B → C
结果: [C] → B → A （顺序反转）
```

- 插入 O(1)，不需要遍历
- 设计初衷：认为最近插入的元素被访问概率更高（时间局部性）
- **致命问题：扩容时链表反转，并发下形成环**

```java
// JDK 1.7 transfer 核心逻辑
void transfer(Entry[] newTable) {
    for (Entry e : table) {
        while (e != null) {
            Entry next = e.next;       // 1. 记住下一个
            int i = indexFor(e.hash, newCapacity);
            e.next = newTable[i];      // 2. 新节点指向新桶的头
            newTable[i] = e;           // 3. 新节点成为新桶的头
            e = next;                  // 4. 处理下一个
        }
    }
}
```

### 尾插法（JDK 1.8）

新节点插入到链表**尾部**，保持原有顺序。

```
插入顺序: A → B → C
结果: [A] → B → C （顺序一致）
```

- 插入需要遍历到末尾 O(n)，但链表短且超过 8 会树化，影响不大
- 扩容时保持原有顺序，不会反转，不会成环

```java
// JDK 1.8 扩容时拆链表（简化）
Node loHead = null, loTail = null;
Node hiHead = null, hiTail = null;

while (e != null) {
    if ((e.hash & oldCap) == 0) {
        if (loTail == null) loHead = e;
        else loTail.next = e;
        loTail = e;
    } else {
        if (hiTail == null) hiHead = e;
        else hiTail.next = e;
        hiTail = e;
    }
    e = e.next;
}
```

---

## 六、线程不安全的原因

### JDK 1.7：并发扩容导致死循环

头插法 + 并发 resize → 链表成环 → `get()` 死循环，CPU 100%

**过程：**
- 线程1 记住 `e = A, next = B`，被挂起
- 线程2 完成扩容，链表变成 B → A
- 线程1 恢复，头插法处理后 A ↔ B 互相指向，形成环

### JDK 1.8：并发 put 数据丢失

```java
if ((p = tab[i = (n - 1) & hash]) == null)
    tab[i] = newNode(hash, key, value, null);
```

两个线程同时 put 到同一个空桶：
1. 线程 A 判断 `tab[i] == null` 成立，被挂起
2. 线程 B 判断 `tab[i] == null` 成立，写入成功
3. 线程 A 恢复，直接覆盖 → **线程 B 的数据丢失**

### size 计数不准确

`++size` 是 read → +1 → write 三步，非原子操作，并发下计数会少。

---

## 七、JDK 1.7 vs 1.8 全面对比

| 维度 | JDK 1.7 | JDK 1.8 |
|------|---------|---------|
| 数据结构 | 数组 + 链表 | 数组 + 链表 + 红黑树 |
| 插入方式 | 头插法 | 尾插法 |
| 链表转树 | 无 | 链表 ≥ 8 且数组 ≥ 64 |
| hash 计算 | 4次位运算 + 5次异或 | 1次右移 + 1次异或 |
| 扩容定位 | 重新计算 indexFor | hash & oldCap 判断 |
| 扩容链表处理 | 逐个头插，链表反转 | 拆高低两条链表，整体迁移，顺序不变 |
| 并发风险 | 死循环（链表成环） | 数据丢失（不会死循环） |
| 节点类型 | Entry\<K,V\> | Node\<K,V\> / TreeNode\<K,V\> |
| 插入与扩容顺序 | 先扩容再插入 | 先插入再扩容 |

---

## 八、ConcurrentHashMap 对比

| 　　　 | JDK 1.7　　　　　　　　　　　　　| JDK 1.8　　　　　　　　　　　|
| --------| ----------------------------------| ------------------------------|
| 结构　 | Segment 数组 + HashEntry 数组　　| Node 数组 + 链表/红黑树　　　|
| 锁粒度 | Segment（分段锁，ReentrantLock） | 桶级别（synchronized + CAS） |
| 并发度 | 默认 16　　　　　　　　　　　　　| 等于数组长度　　　　　　　　 |

JDK 1.8 ConcurrentHashMap：
- 桶为空 → CAS 写入
- 桶不为空 → synchronized 锁住头节点
- 扩容时支持多线程协助迁移（transfer 方法）

---

## 九、高频面试 QA

**Q: HashMap 的 key 可以为 null 吗？**
可以，null key 固定放在下标 0。ConcurrentHashMap 不允许 null key/value。

**Q: 为什么重写 equals 必须重写 hashCode？**
HashMap 先用 hashCode 定位桶，再用 equals 判断 key 是否相同。如果 equals 为 true 但 hashCode 不同，会被放到不同桶里，逻辑错误。

**Q: 负载因子为什么是 0.75？**
时间和空间的折中。太小浪费空间，太大冲突严重。0.75 在泊松分布下能让桶中元素数量保持合理。

**Q: 为什么扩容是 2 倍？**
保证容量始终是 2 的幂，使得 `(n-1) & hash` 等价于取模，且扩容时可以用 `hash & oldCap` 一位判断新位置。

**Q: HashMap 和 Hashtable 的区别？**
- Hashtable 线程安全（方法加 synchronized），HashMap 不安全
- Hashtable 不允许 null key/value
- Hashtable 初始容量 11，扩容 2n+1；HashMap 初始 16，扩容 2n

---

## 十、面试回答模板

> JDK 1.7 用数组+链表，头插法插入，扩容时逐个节点重新hash并头插到新数组，导致链表反转，并发下可能成环死循环。
>
> JDK 1.8 改为数组+链表+红黑树，尾插法插入，扩容时用 `hash & oldCap` 把链表拆成高低两条整体迁移，顺序不变不会成环。同时引入红黑树，链表超过8且数组超过64时树化，最坏查找从 O(n) 优化到 O(log n)。hash 函数也简化了，因为有红黑树兜底不怕碰撞。

---

## 十一、Map 集合补充面试题

### 1. HashMap 的 put 方法能否保证原子性？两个线程同时 put 不同的 key 会怎样？

不能。即使两个 key 不冲突（落到不同桶），`++size` 也不是原子操作，可能导致 size 不准，进而影响扩容判断。如果恰好同时触发扩容，两个线程同时 resize 会创建两个新数组，最终只有一个生效，另一个线程的数据丢失。

---

### 2. HashMap 中用红黑树而不用 AVL 树，为什么？

AVL 树是严格平衡的，查找快（O(log n)），但插入/删除时旋转次数多。红黑树是近似平衡，查找略慢但插入/删除最多旋转 3 次。HashMap 的场景是频繁插入删除，红黑树综合性能更好。

---

### 3. ConcurrentHashMap 的 size() 是怎么算的？

- **1.7：** 先不加锁尝试统计两次，如果两次结果一致就返回；不一致则锁住所有 Segment 再统计
- **1.8：** 用 `baseCount` + `CounterCell[]` 数组（类似 LongAdder 的思路），put 时 CAS 更新 baseCount，失败则分散到 CounterCell 中，size() 时求和

---

### 4. HashMap 的 key 用可变对象会怎样？

如果 put 之后修改了 key 的字段（影响 hashCode），那么 get 时计算出的桶位置和 put 时不同，永远找不到这个 entry。这个 entry 变成了"内存泄漏"——存在但不可达。所以推荐用 String、Integer 等不可变对象做 key。

---

### 5. LinkedHashMap 和 TreeMap 分别什么场景用？

- **LinkedHashMap：** 维护插入顺序（或访问顺序），适合实现 LRU 缓存。设置 `accessOrder=true` + 重写 `removeEldestEntry()` 就是一个 LRU。
- **TreeMap：** 基于红黑树，key 有序（自然排序或 Comparator），适合需要范围查询（`subMap`、`headMap`、`tailMap`）的场景。

---

### 6. ConcurrentHashMap 为什么不允许 null key/value？

在并发环境下，`get(key)` 返回 null 时无法区分是"key 不存在"还是"value 就是 null"。单线程的 HashMap 可以用 `containsKey()` 再确认，但并发下两次调用之间状态可能变化，所以 Doug Lea 直接禁止了 null。

---

### 7. HashMap 初始容量设多少合适？已知要存 1000 个元素。

需要避免扩容：`容量 × 0.75 ≥ 1000`，即容量 ≥ 1334。又因为容量必须是 2 的幂，所以取 2048。实际中可以用 `new HashMap<>(1334)`，构造函数内部会自动向上取到 2048。Guava 提供了 `Maps.newHashMapWithExpectedSize(1000)` 帮你算。

---

### 8. 为什么 String 适合做 HashMap 的 key？

1. 不可变（final class，char[] 不可修改），hashCode 不会变
2. 重写了 hashCode 和 equals，且 hashCode 有缓存（计算一次后存在 `hash` 字段里）
3. 字符串常量池的存在使得相同内容的 String 可以 `==` 比较，加速判断

---

### 9. HashMap 和 HashSet 的关系？

HashSet 底层就是一个 HashMap，元素存在 key 里，value 统一是一个 `static final Object PRESENT = new Object()`。所以 HashSet 的去重逻辑完全依赖 HashMap 的 key 去重机制。

---

### 10. 1.8 中 HashMap 扩容时红黑树怎么处理？

和链表类似，用 `hash & oldCap` 把树节点拆成高低两组。拆完后如果某组节点数 ≤ 6，退化为链表（`untreeify`）；否则重新构建红黑树。这也是为什么退化阈值是 6 而不是 7——和树化阈值 8 之间留了缓冲，避免频繁树化/退化震荡。

---

## 十二、Hash 冲突解决方式

### HashMap：链地址法（拉链法）

同一个桶里的冲突元素用链表串起来，1.8 中链表超过 8 且数组超过 64 转红黑树。

### 常见解决 hash 冲突的方法

| 方法 | 思路 | 谁在用 |
|------|------|--------|
| 链地址法（拉链法） | 同一个桶用链表/树存多个元素 | HashMap、ConcurrentHashMap |
| 开放地址法 | 冲突了就往后找空位（线性探测/二次探测） | ThreadLocalMap、Python dict |
| 再哈希法 | 用第二个 hash 函数重新算位置 | 布隆过滤器（多个 hash） |

### HashMap 为什么选链地址法？

- 删除方便：链表删节点 O(1)，开放地址法删除需要标记"已删除"
- 负载因子可以 > 1：链表可以无限长，开放地址法满了就必须扩容
- 冲突聚集少：开放地址法容易产生"堆积"

---

## 十三、ThreadLocalMap 的线性探测

ThreadLocalMap 用**开放地址法（线性探测）**：算出初始位置后如果被占了就往后逐个找空位。

```java
// set 核心逻辑
int i = key.threadLocalHashCode & (len - 1);
while (tab[i] != null) {
    if (tab[i].get() == key) { tab[i].value = value; return; }
    if (tab[i].get() == null) { replaceStaleEntry(key, value, i); return; }
    i = nextIndex(i, len);  // (i + 1) & (len - 1)
}
tab[i] = new Entry(key, value);
```

**为什么不用链地址法？**
- 元素少（一个线程通常只有几个 ThreadLocal），冲突概率低
- 纯数组结构更省内存（不需要 next 指针）
- 连续内存缓存友好
- 探测过程中顺便清理被 GC 回收的过期 Entry

**缺点：聚集问题。** 连续被占的位置越多，后续探测越慢。通过负载因子 2/3（比 HashMap 的 0.75 更低）和主动清理过期 Entry 来缓解。

---

## 十四、LinkedHashMap

### 本质

LinkedHashMap = HashMap + 双向链表。每个节点多了 `before` 和 `after` 指针，串成一条独立于桶结构的双向链表，用来维护顺序。

### 两种顺序模式

```java
new LinkedHashMap<>(16, 0.75f, accessOrder);
```

| accessOrder | 行为 | 链表顺序 |
|---|---|---|
| false（默认） | 插入顺序 | 按 put 的先后排列 |
| true | 访问顺序 | 每次 get/put 把节点移到链表尾部 |

### 实现 LRU 缓存

`accessOrder = true` 时，最近访问的在尾部，最久没访问的在头部。重写 `removeEldestEntry()` 即可：

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private int capacity;

    public LRUCache(int capacity) {
        super(capacity, 0.75f, true);  // accessOrder = true
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;  // 超过容量就删最老的（头节点）
    }
}
```

### 和 HashMap 的对比

| | HashMap | LinkedHashMap |
|---|---|---|
| 继承关系 | — | extends HashMap |
| 遍历顺序 | 无序 | 有序（插入序或访问序） |
| 额外开销 | 无 | 每个节点多两个指针 |
| 典型用途 | 通用 KV 存储 | LRU 缓存、需要保持顺序的场景 |

---

## 十五、面试回答模板（HashMap 实现原理）

**组织逻辑：结构 → 流程 → 扩容 → 优化 → 线程安全**

> HashMap 底层在 JDK 1.8 中是数组 + 链表 + 红黑树的结构。
>
> put 的时候，先对 key 的 hashCode 做一次扰动——高 16 位异或低 16 位，让哈希分布更均匀。然后用 (n-1) & hash 算出桶下标。如果桶为空直接放入；如果不为空，就沿着链表用 equals 逐个比较，找到相同的 key 就覆盖 value，找不到就尾插到链表末尾。链表长度达到 8 且数组长度达到 64 时转红黑树。
>
> 扩容方面，默认初始容量 16，负载因子 0.75，超过阈值扩容为 2 倍。容量始终是 2 的幂，扩容时通过 hash & oldCap 判断节点新位置，不需要重新计算 hash。
>
> 和 1.7 相比主要改了三点：引入红黑树、头插法改尾插法（避免并发成环）、扩容用位运算拆链表整体迁移。HashMap 本身不是线程安全的，并发场景用 ConcurrentHashMap。
