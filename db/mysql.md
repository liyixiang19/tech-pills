# base

 mysql 关系型数据库，强大的事务支持，友好运维，开源生态，社区庞大


 ### 时间
  - datetime不带时区，全部交给程序处理
  - timestamp带时区，存储时mysql会转换成utc时间存储，查询时会转换成会话设置的时区返回
  - 对比pg或者gauss（timestamp无时区，timestamptz有时区）

### 存储引擎
  + myisam 纯读场景，写性能极差，表锁，索引和数据存储分离
  + innodb 90%使用，支持ACID事务，回滚，行级锁，聚簇索引，数据和主键索引存在一起，主键查询极快
#### innodb底层
    1. 使用redo log（持久性）和undo log（原子性）支撑事务的实现
    2. 事务执行时，先写 Redo Log 缓冲，再刷盘（可配置 innodb_flush_log_at_trx_commit：1 = 事务提交即刷盘，最安全；0 = 每秒刷盘，性能高但可能丢 1 秒数据）；服务器崩溃后，InnoDB 重放 Redo Log，恢复未刷到数据文件的修改。
    3. 事务修改数据前，先将旧数据写入 Undo Log；事务回滚时，从 Undo Log 恢复数据；同时 Undo Log 也是 MVCC（多版本并发控制）的基础 —— 读操作通过 Undo Log 读取历史版本，避免阻塞写操作。
    4. buffer pool缓冲池，命中直接读取缓存, 提高查询速度，内存的50%-70%