# 一、创建任务

1. **runAsync(() -> {})**
  - 无返回值异步执行
  - 使用默认线程池
2. **supplyAsync(() -> result)**
  - 有返回值异步执行（最常用）
3. **completedFuture(value)**
  - 直接创建一个已完成的 Future（用于测试/兜底）

# 二、获取结果

1. **get()**
  - 阻塞获取结果，会抛受检异常
2. **join()**
  - 阻塞获取结果，**不抛受检异常**（并行聚合常用）
3. **getNow(defaultVal)**
  - 没完成就立即返回默认值，不阻塞

# 三、回调（任务完成后自动执行）

1. **thenRun(runnable)**
  - 不接收结果、无返回值
2. **thenAccept(result -> {})**
  - 接收结果，无返回值
3. **thenApply(result -> newResult)**
  - 接收结果，**返回新结果**（转换、加工）
4. **whenComplete((result, ex) -> {})**
  - 无论成功/异常都会进入
5. **handle((result, ex) -> newResult)**
  - 同 whenComplete，但**可返回新结果**

# 四、异常处理

1. **exceptionally(ex -> fallbackResult)**
  - 出现异常时，返回兜底值
2. **orTimeout(time, unit)**
  - 超时抛出异常
3. **completeOnTimeout(defaultVal, time, unit)**
  - 超时返回默认值（更友好）

# 五、多任务组合（核心价值）

1. **allOf(f1, f2, f3)**
  - **等待所有任务完成**（并行聚合接口必用）
2. **anyOf(f1, f2, f3)**
  - 任意一个完成就返回（抢最快、兜底）
3. **thenCombine(f2, (r1, r2) -> r)**
  - 两个任务都完成后，合并结果
4. **thenCompose(result -> anotherFuture)**
  - 前一个结果作为后一个任务入参（**串行异步**）

# 六、异步后缀（*Async）

- **thenRunAsync / thenAcceptAsync / thenApplyAsync…**
- 作用：回调方法**也交给线程池执行**，不占用当前线程
- 适用：回调逻辑也很重时

# 七、控制任务完成

1. **complete(value)**
  - 手动让任务完成并设结果
2. **completeExceptionally(ex)**
  - 手动抛出异常
3. **cancel(true)**
  - 取消任务

---

# 最简单记忆口诀

- 想并行查接口 → **allOf + join**
- 想串行异步 → **thenCompose**
- 想合并两个结果 → **thenCombine**
- 想最快返回 → **anyOf**
- 想异常兜底 → **exceptionally**
- 想超时控制 → **orTimeout**

