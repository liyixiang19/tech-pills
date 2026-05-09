# Redis 知识体系

## 一、Redis 为什么快

1. **纯内存操作**：数据读写都在内存中，访问速度纳秒级，比磁盘 IO 快数个数量级
2. **单线程事件循环 + I/O 多路复用**：避免上下文切换和锁竞争，单线程配合 epoll 处理大量并发连接
3. **优化的数据结构**：内部根据数据大小动态选择编码（ziplist、quicklist、skiplist、hashtable），兼顾性能和空间
4. **高效通信协议（RESP）**：实现简单、解析快、二进制安全，序列化/反序列化开销小

---

## 二、数据结构

### 基本类型
| 类型 | 底层实现 | 典型场景 |
|------|---------|---------|
| String | SDS（Simple Dynamic String） | 缓存、计数器、分布式锁 |
| List | quicklist（ziplist + 双向链表） | 消息队列、最新列表 |
| Hash | ziplist / hashtable | 对象存储 |
| Set | intset / hashtable | 标签、去重、交并差集 |
| ZSet | **跳表 + hashtable** | 排行榜、延迟队列 |

### ZSet 底层：跳表 + Hash
- **跳表**：支持范围查询，O(logN) 查找
- **Hash**：O(1) 查成员分数

**跳表 vs B+树：**
- 思想类似：上层索引 + 底层有序链表
- B+树更适合磁盘（矮胖，IO 次数少）
- 跳表更适合内存（实现简单，无复杂自旋和平衡）
- 跳表优点：实现简单、范围查询高效
- 跳表缺点：空间开销较大（多层索引）

---

## 三、缓存

### 3.1 缓存思想
**空间换时间**

本地缓存优势：低依赖、轻量、简单、成本低  
本地缓存缺陷：
- 对分布式架构不友好，多实例间缓存无法共享
- 容量受单机内存限制

### 3.2 多级缓存架构
**caffeine（本地缓存）+ Redis（分布式缓存）**

核心问题：缓存一致性  
解决方案：Canal + 广播消息
1. DB 修改数据
2. 监听 Canal 消息，触发缓存更新
3. Redis 缓存：集群共享一份，直接同步
4. 本地缓存：多份分散在不同 JVM，借助广播 MQ 通知各实例同步

### 3.3 Spring Cache 中的 Redis
- 默认使用 String 类型，Java 对象序列化为 JSON 字符串存储
- 在 `CacheManager` 中配置序列化方式
- 需要特殊转换类型时，单独写 CacheManager

---

## 四、Redis 的其他应用场景

基于 Redisson 封装的 API：
1. **延迟队列**：ZSet，score 为过期时间，最短的在最前面
2. **分布式锁**：详见下文
3. **消息队列**：Stream 类型

---

## 五、持久化机制

### 5.1 RDB（Redis Database Snapshot）— 快照

**原理：** 指定时间间隔内，将内存数据集快照写入磁盘，生成 `.rdb` 二进制文件。

**触发方式：**
- `save`：阻塞主线程，生产环境不建议
- `bgsave`：fork 子进程，利用 COW（Copy-On-Write），不阻塞主线程
- 配置自动触发：如 `save 900 1`（900秒内至少1次写操作）

**适用场景：** 容忍几分钟数据丢失、快速恢复大数据集、定期备份

### 5.2 AOF（Append Only File）— 追加日志

**原理：** 每条写命令追加写入日志文件，恢复时重放所有命令。

**刷盘策略（fsync）：**
| 策略 | 说明 | 数据安全 | 性能 |
|------|------|---------|------|
| always | 每条命令刷盘 | 最安全 | 最差 |
| everysec | 每秒刷盘（推荐） | 最多丢1秒 | 好 |
| no | OS 决定 | 不可控 | 最好 |

**AOF 重写：** 文件过大时触发 `bgrewriteaof`，用最少命令重建当前数据集。

**适用场景：** 数据安全性要求高，金融、订单等业务

### 5.3 混合持久化（Redis 4.0+，生产推荐）

**原理：** AOF 重写时，前半段 RDB 二进制（全量），后半段 AOF 文本（增量）。

**优势：** 兼顾恢复速度（RDB）和数据安全（AOF）。

### 5.4 对比总结

| 维度 | RDB | AOF | 混合 |
|------|-----|-----|------|
| 数据安全 | 可能丢几分钟 | 最多丢1秒 | 最多丢1秒 |
| 恢复速度 | 快 | 慢（重放命令） | 快 |
| 文件体积 | 小（压缩二进制） | 大（文本命令） | 中等 |
| 性能影响 | fork 时短暂开销 | everysec 影响小 | 重写时有开销 |

---

## 六、分布式锁

### 6.1 为什么 Redis 可以做分布式锁

| 能力 | Redis 机制 |
|------|-----------|
| 互斥 | 单线程命令串行 + SET NX |
| 原子 | 单命令原子 + Lua 脚本 |
| 防死锁 | key 过期（EX/PX） |
| 安全性 | value 存 owner 标识，解锁时校验 |
| 阻塞等待 | pub/sub 通知 |
| 集群共享 | 网络可达的中心存储 |

**核心：** 单线程模型保证命令串行执行，`SET NX` 天然互斥。

### 6.2 加锁：SET NX EX

```bash
SET lock_key unique_value NX EX 30
# NX：不存在才设置（互斥）
# EX 30：30秒过期（防死锁）
# unique_value：UUID/线程ID（防误解锁）
```

**为什么不能 SETNX + EXPIRE 分两步？** 非原子操作，SETNX 后客户端崩溃 → 永久死锁。

### 6.3 解锁：Lua 脚本保证原子性

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
```

**为什么不能 GET + DEL 分两步？** GET 判断通过后、DEL 前锁可能过期被别人抢到，DEL 就删了别人的锁。

### 6.4 锁续期：Watchdog 看门狗

**问题：** 业务执行时间超过锁过期时间 → 锁提前释放 → 并发问题。

**Redisson Watchdog：** 默认 30 秒锁，每 10 秒（expireTime/3）检查并续期。不传 leaseTime 参数时自动启用。

### 6.5 Redisson 实现

```java
RLock lock = redisson.getLock("orderLock");
try {
    boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
    if (locked) {
        // 业务逻辑
    }
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**可重入锁 — Hash 结构：**
```
Key:   lock_name
Field: UUID:threadId
Value: 重入次数（lock +1, unlock -1, 减到 0 才删 key）
```

**加锁 Lua 脚本（RedissonLock.tryLockInnerAsync）：**
```lua
if (redis.call('exists', KEYS[1]) == 0) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
return redis.call('pttl', KEYS[1]);
```

**解锁 Lua 脚本（RedissonLock.unlockInnerAsync）：**
```lua
if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
    return nil;
end;
local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);
if (counter > 0) then
    redis.call('pexpire', KEYS[1], ARGV[2]);
    return 0;
else
    redis.call('del', KEYS[1]);
    redis.call('publish', KEYS[2], ARGV[1]);
    return 1;
end;
```

### 6.6 Redis 宕机对分布式锁的影响

#### 单机宕机
Redis 挂了 → 锁消失 → 互斥性被破坏。

#### 主从架构（Sentinel）— 经典锁丢失问题
```
客户端A 在 Master 加锁成功
→ Master 还没同步到 Slave 就宕机
→ Slave 升为新 Master（没有锁数据）
→ 客户端B 在新 Master 加锁成功
→ A 和 B 同时持锁
```
**根因：** Redis 主从复制是异步的。

#### Redis Cluster
同样的问题，每个分片都是一主多从，异步复制丢数据。

### 6.7 RedLock 算法

部署 N 个独立 Redis 节点（通常 5 个，互不主从）。

**加锁：** 向 N 个节点依次加锁，成功数 ≥ N/2+1 且总耗时 < 锁过期时间 → 加锁成功。

**争议（Martin Kleppmann）：**
- GC 停顿可能导致锁过期但客户端不知道
- 时钟跳变导致锁提前过期
- 生产中很少使用，复杂度高、性能差

### 6.8 应对方案

#### 方案 1：业务幂等（最重要）⭐
```java
if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
    try {
        if (orderDao.exists(orderId)) return;  // 幂等校验
        orderDao.create(order);
    } finally { lock.unlock(); }
}
```
**锁是性能优化，幂等才是正确性保障。**

#### 方案 2：Redis 锁 + DB 唯一索引兜底
```java
try {
    orderDao.insert(order);  // 唯一索引兜底
} catch (DuplicateKeyException e) {
    log.warn("订单已存在");   // Redis 锁失效时 DB 层拦截
}
```

#### 方案 3：强一致场景 → ZooKeeper / etcd
ZAB 协议强一致，主挂了不丢已提交数据。代价：性能差一个数量级。

| 场景 | 方案 |
|------|------|
| AP（容忍极端锁失效） | Redis 锁 + 业务幂等 |
| CP（不允许锁失效） | ZooKeeper / etcd |

### 6.9 常见坑

1. **锁粒度过大**：应该按 key 锁关键资源，不要锁整个方法
2. **try-finally 忘写**：异常时锁不释放
3. **业务超过锁时间**：没用 watchdog 容易翻车
4. **解锁不校验 owner**：删了别人的锁
5. **SETNX + EXPIRE 分开写**：非原子，可能死锁
6. **定时任务用 lock() 不用 tryLock()**：阻塞等待导致任务重复执行

**核心结论：分布式锁不是银弹，幂等才是。**
