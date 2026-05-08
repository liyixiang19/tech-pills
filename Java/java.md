1、@transactional失效场景

@transactional原理是用aop代理，所以同类中调用会失效
try catch 捕获异常被吃掉会失效
数据库本身不支持事务也不会生效myisam

2、