# Java 并发编程

## 一、并发常见问题

- 内存泄漏
- 死锁
- 线程不安全（竞态条件、数据不一致）

---

## 二、死锁

### 排查方式

`jstack <pid>`，输出中会出现 `Found one Java-level deadlock` 关键字。

### 死锁的四个必要条件

1. 互斥条件
2. 请求与保持条件
3. 不剥夺条件
4. 循环等待条件

### 预防死锁：破坏必要条件

| 策略 | 做法 |
|------|------|
| 破坏请求与保持 | 一次性申请所有资源 |
| 破坏不剥夺 | 申请不到新资源时，主动释放已占有的资源 |
| 破坏循环等待 | 按固定顺序申请资源，释放时反序释放 |

---

## 三、乐观锁与悲观锁

### 悲观锁

假设一定会冲突，先加锁再操作。Java 中 `synchronized` 和 `ReentrantLock` 都是悲观锁。

### 乐观锁

假设不会冲突，操作时不加锁，提交时检查是否被修改。

两种实现方式：
- **版本号机制**：类似 InnoDB 的 MVCC，每次修改 version + 1，提交时比对 version
- **CAS 算法**：Compare And Swap，`AtomicInteger` 等原子类的底层实现

### CAS 的问题

| 问题 | 说明 |
|------|------|
| ABA 问题 | 值从 A → B → A，CAS 认为没变。解决：`AtomicStampedReference` 加版本号 |
| 自旋开销 | 竞争激烈时一直 CAS 失败，空转浪费 CPU |
| 只能保证单个变量原子性 | 多个变量需要用锁或 `AtomicReference` 包装 |

---

## 四、volatile 与 synchronized

| 对比点 | volatile | synchronized |
|--------|----------|-------------|
| 作用范围 | 只能修饰变量 | 方法、代码块 |
| 保证 | 可见性 + 禁止重排序 | 原子性 + 可见性 + 有序性 |
| 性能 | 开销小，不阻塞 | 开销大，可能阻塞 |
| 适用场景 | 状态标志位、DCL 单例 | 复合操作（i++、先检查后执行） |

**选择原则**：只需可见性 → volatile；需要原子性 → synchronized / Lock / 原子类。

---

## 五、synchronized 锁升级

```
无锁 → 偏向锁 → 轻量级锁（CAS 自旋）→ 重量级锁（阻塞）
```

| 锁状态 | 适用场景 | 实现方式 |
|--------|---------|---------|
| 偏向锁 | 只有一个线程访问 | 对象头记录线程 ID，下次进入直接放行 |
| 轻量级锁 | 少量线程交替访问，竞争不激烈 | CAS 自旋尝试获取 |
| 重量级锁 | 竞争激烈 | 操作系统 mutex，线程阻塞 |

---

## 六、ReentrantLock

### 基本特性

- 实现 `Lock` 接口，可重入独占锁
- 比 synchronized 更灵活：支持轮询、超时、中断、公平/非公平

### ReentrantLock vs synchronized

| 对比点 | synchronized | ReentrantLock |
|--------|-------------|---------------|
| 实现层面 | JVM 内置（monitorenter/monitorexit） | Java 代码，基于 AQS |
| 释放方式 | 自动（出代码块/异常） | 手动 unlock()，必须放 finally |
| 可中断 | 不支持 | lockInterruptibly() |
| 超时 | 不支持 | tryLock(timeout) |
| 公平性 | 非公平（不可选） | 可选公平/非公平 |
| Condition | 一个等待队列（wait/notify） | 多个 Condition 队列 |
| 性能 | JDK6 后优化很大 | 高竞争下略优 |

### 选择建议

- 简单互斥 → `synchronized`（够用就行，不会忘记释放）
- 需要 tryLock / 超时 / 中断 / 公平 / 多 Condition → `ReentrantLock`

### 公平锁 vs 非公平锁

**公平锁**：新线程来了先检查队列，有人排队就乖乖排到队尾，等前面的人释放后按顺序获取。

**非公平锁**：新线程来了直接 CAS 抢一次，抢到就用（插队），抢不到再排队。

**非公平锁性能更好的原因**：减少线程上下文切换。线程从阻塞态恢复需要操作系统调度，开销远大于一次 CAS。非公平锁让"正好在运行的线程"直接拿锁，避免了唤醒等待线程的开销。

**代价**：队列中的线程可能被反复插队，产生饥饿。

---

## 七、AQS（AbstractQueuedSynchronizer）

### 核心结构

```java
public abstract class AbstractQueuedSynchronizer {
    private volatile int state;          // 同步状态
    private transient volatile Node head; // CLH 队列头
    private transient volatile Node tail; // CLH 队列尾
}
```

### state 为什么用 volatile？

1. **可见性**：线程 A 修改 state 后，线程 B 立刻能看到最新值
2. **禁止重排序**：保证临界区操作在 state 修改之前完成

volatile 只保证单次读写的可见性，不保证复合操作原子性，所以还需要 CAS：
- volatile → 保证看到最新值
- CAS → 保证"读 + 比较 + 写"是原子的
- **volatile + CAS = 无锁并发的基石**

### CLH 队列

双向链表，每个节点代表一个等待锁的线程：

```
head(哨兵) ⇄ Node(B) ⇄ Node(C) ⇄ Node(D) = tail
```

**为什么用双向链表？**

| 原因 | 说明 |
|------|------|
| 取消节点 O(1) | 直接通过 node.prev 找到前驱，修改指针 |
| 唤醒时从尾部往前找 | 入队时 prev 先设置、next 后设置，存在中间状态，从后往前才安全 |
| 判断前驱是否是 head | acquireQueued 中需要 node.prev == head 来决定是否有资格抢锁 |

### Node 节点状态（waitStatus）

| 值 | 含义 |
|----|------|
| 0 | 初始状态 |
| SIGNAL(-1) | 释放锁时需要唤醒后继节点 |
| CANCELLED(1) | 线程取消等待 |
| CONDITION(-2) | 在 Condition 队列中 |
| PROPAGATE(-3) | 共享模式下传播唤醒 |

注意：waitStatus 描述的是**对后继节点的责任**，不是自己的状态。

### AQS 的模板方法模式

```
AQS 负责（通用逻辑）：入队、出队、阻塞、唤醒、中断处理
子类负责（差异逻辑）：tryAcquire / tryRelease（定义什么条件算获取成功）
```

### 独占模式流程（ReentrantLock）

**获取锁：**
```
lock() → tryAcquire()
  → 成功：拿到锁，执行
  → 失败：addWaiter() 入队 → acquireQueued() 自旋/阻塞等待
    → 前驱是 head？→ 是 → tryAcquire() 再试
                    → 否 → park() 阻塞
```

**释放锁：**
```
unlock() → tryRelease() → state - 1
  → state == 0（完全释放）→ unparkSuccessor() → unpark 队列头部下一个线程
  → state > 0（重入未完）→ 不唤醒
```

### 共享模式流程（CountDownLatch）

**await()：**
```
tryAcquireShared() → state == 0？
  → 是：直接通过
  → 否：入队阻塞等待
```

**countDown()：**
```
tryReleaseShared() → state CAS 减 1 → 减到 0？
  → 是：唤醒所有等待线程（链式传播唤醒）
  → 否：不唤醒
```

---

## 八、AQS 的几种实现对比

| 实现 | state 含义 | 模式 | 是否可重用 | 场景 |
|------|-----------|------|-----------|------|
| ReentrantLock | 重入次数（0=空闲） | 独占 | 是 | 互斥访问 |
| CountDownLatch | 剩余计数 | 共享 | 否（一次性） | 等待 N 个任务完成 |
| Semaphore | 可用许可数 | 共享 | 是 | 控制并发数/限流 |
| CyclicBarrier | 基于 ReentrantLock + Condition | 共享 | 是（可循环） | N 个线程互相等待 |
| ReentrantReadWriteLock | 高16位读/低16位写 | 读共享/写独占 | 是 | 读多写少 |

### CountDownLatch vs CyclicBarrier

| 对比点 | CountDownLatch | CyclicBarrier |
|--------|---------------|---------------|
| 等待关系 | 一方等多方 | 多方互相等 |
| 调用方式 | 干活的 countDown()，等的 await() | 所有人都调 await() |
| 能否重用 | 不能，计数到 0 就废了 | 能，到齐后自动重置 |
| 到齐后动作 | 无 | 可指定 barrierAction 回调 |

### Semaphore 注意点

- `release()` 可以不先 `acquire()` 就调用，会让许可数超过初始值
- Semaphore 不记录"谁获取了许可"，只管计数
- 和 ReentrantLock 不同：unlock() 非持有者调用会抛异常

### 读写锁注意点

- **锁升级（读→写）不允许**：会死锁（自己持有读锁，写锁要求无读锁，互相等待）
- **锁降级（写→读）允许**：持有写锁时可以获取读锁，再释放写锁

---

## 九、CAS 底层实现

### 调用链

```
Java: compareAndSetState(expect, update)
  → Unsafe.compareAndSwapInt()（native 方法）
    → JNI → JVM C++ 代码
      → CPU 指令: lock cmpxchg
```

### CPU 层面

- `cmpxchg`：比较并交换指令
- `lock` 前缀：锁定缓存行（或总线），保证多核下的原子性

CAS 是一条 CPU 指令，天然原子，不需要软件层面加锁。

---

## 十、ConcurrentHashMap

### 1.7 vs 1.8 实现对比

| | JDK 1.7 | JDK 1.8 |
|---|---|---|
| 结构 | Segment 数组 + HashEntry 数组 | Node 数组 + 链表/红黑树 |
| 锁粒度 | Segment（分段锁，ReentrantLock） | 桶级别（synchronized + CAS） |
| 并发度 | 默认 16（Segment 数量） | 等于数组长度 |
| get 加锁 | 不加锁（volatile 读） | 不加锁（volatile 读） |

---

### put 流程（JDK 1.8）

1. 计算 hash（spread 函数）
2. 桶为空 → **CAS** 写入新节点
3. 桶不为空 → **synchronized** 锁住头节点，遍历链表/红黑树插入
4. 链表长度 ≥ 8 → 树化
5. 更新计数（baseCount + CounterCell）

**为什么不全用 CAS？** 链表/树的遍历+修改是多步操作，CAS 只能保证单个变量的原子性，无法保护整个遍历过程。

---

### get 流程（JDK 1.8）—— 全程无锁

```java
public V get(Object key) {
    int h = spread(key.hashCode());
    Node<K,V>[] tab; Node<K,V> e;
    if ((tab = table) != null &&
        (e = tabAt(tab, (n - 1) & h)) != null) {  // volatile 读桶头节点
        if (e.hash == h && key.equals(e.key))
            return e.val;                           // 头节点命中
        if (e.hash < 0)                             // ForwardingNode 或 TreeBin
            return e.find(h, key).val;
        while ((e = e.next) != null) {              // 遍历链表
            if (e.hash == h && key.equals(e.key))
                return e.val;
        }
    }
    return null;  // key 不存在
}
```

**为什么不加锁也安全？**
- Node 的 `val` 和 `next` 都是 **volatile**，保证读到最新值
- 数组槽位通过 `Unsafe.getObjectVolatile` 读取
- volatile 保证可见性 + 禁止重排序，读操作不需要互斥

---

### 为什么不允许 null key/value

`get(key)` 返回 null 时：
- HashMap：可能是 key 不存在，也可能是 value 就是 null（有歧义）
- ConcurrentHashMap：**一定是 key 不存在**（无歧义）

HashMap 可以用 `containsKey` 再确认，但并发下 `containsKey` 和 `get` 之间状态可能被其他线程修改，判断不可靠。禁止 null 从根源消除歧义，一次 get 调用就能明确语义。

---

### size() 实现：分段计数（类似 LongAdder）

```java
// 计数结构
private transient volatile long baseCount;
private transient volatile CounterCell[] counterCells;
```

- put 时先 CAS 更新 baseCount
- CAS 失败（竞争激烈）→ 分散到 CounterCell 数组的某个槽
- size() 时：baseCount + 所有 CounterCell 的值求和

**为什么不用单个 AtomicLong？** 高并发下所有线程 CAS 同一个变量会疯狂重试，性能极差。分散到多个 Cell 减少竞争。

---

### 扩容：多线程协助迁移

1. 触发扩容的线程创建新数组（2倍），从后往前迁移桶
2. 其他线程 put 时发现正在扩容（遇到 ForwardingNode），加入帮忙迁移
3. 每个线程领取一段桶区间（stride），各自负责自己那段
4. 迁移完的桶放一个 ForwardingNode 标记，get 遇到它就去新数组查

---

### 1.8 为什么用 synchronized 而不是 ReentrantLock？

- JDK 1.6 后 synchronized 大量优化（偏向锁→轻量级锁→重量级锁），性能接近 ReentrantLock
- synchronized 是 JVM 内置的，不需要额外 Lock 对象，每个桶省一个对象头的内存
- 锁粒度已经细化到单个桶，竞争概率低，大多数走偏向锁或轻量级锁
- 代码更简洁，不需要手动 lock/unlock

---

### 复合操作的原子性

单个 get、put 是线程安全的，但"先检查再操作"不是原子的：

```java
// ❌ 错误写法：两步之间可能被其他线程插入
if (!map.containsKey(key)) {
    map.put(key, value);
}

// ✅ 正确写法：使用原子方法
map.putIfAbsent(key, value);
map.computeIfAbsent(key, k -> new Value());
map.compute(key, (k, v) -> ...);
map.merge(key, value, remappingFunction);
```

---

### ConcurrentHashMap vs Collections.synchronizedMap()

| | ConcurrentHashMap | synchronizedMap |
|---|---|---|
| 锁粒度 | 桶级别 | 整个 map 一把锁 |
| 并发读 | 无锁（volatile） | 读也要加锁 |
| 迭代器 | 弱一致性，不抛 CME | 迭代时修改抛 ConcurrentModificationException |
| 性能 | 高并发下远优 | 所有操作串行化 |
| null | 不允许 | 允许 |

---

### 面试回答模板

> ConcurrentHashMap 在 1.8 中用 Node 数组 + 链表/红黑树，抛弃了 1.7 的 Segment 分段锁。put 时桶为空用 CAS，桶不为空用 synchronized 锁住头节点，锁粒度细化到单个桶。get 全程无锁，靠 volatile 保证可见性。计数用 baseCount + CounterCell 分段计数，类似 LongAdder。扩容时支持多线程协助迁移。不允许 null key/value 是为了消除并发下 get 返回 null 的歧义。
