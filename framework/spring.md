# Spring 核心知识点

## 一、循环依赖与三级缓存

### 1.1 什么是循环依赖

A 依赖 B，B 依赖 A，形成闭环。Spring 在创建 Bean 时如果不做处理，会陷入死循环。

### 1.2 三级缓存结构

| 缓存 | 字段 | 存放内容 |
|------|------|----------|
| 一级缓存 | `singletonObjects` | 完全初始化好的 Bean（成品） |
| 二级缓存 | `earlySingletonObjects` | 提前暴露的半成品 Bean（已实例化，未填充属性） |
| 三级缓存 | `singletonFactories` | Bean 的 ObjectFactory（一个 lambda，延迟决策用） |

### 1.3 解决流程（A 依赖 B，B 依赖 A，B 有 AOP 切面）

```
① new A()                                  A 实例化完成
② A 的 ObjectFactory → 三级缓存            三级: {A: 工厂}
③ A 填充属性，发现需要 B
④   new B()                                B 实例化完成
⑤   B 的 ObjectFactory → 三级缓存          三级: {A: 工厂, B: 工厂}
⑥   B 填充属性，发现需要 A
⑦     从三级缓存取 A 的工厂，调用 getObject()
       → A 不需要代理，返回原始 A
       → 结果放入二级缓存，三级缓存删除 A
                                            二级: {A: 原始A}  三级: {B: 工厂}
⑧   B 拿到 A（半成品），属性填充完成
⑨   B 执行初始化
⑩   B 的 postProcessAfterInitialization()
     → B 有切面，创建代理 B
⑪   代理 B 放入一级缓存                    一级: {B: 代理B}
⑫ 回到 A，从一级缓存拿到代理 B，注入
⑬ A 执行初始化
⑭ A 的 postProcessAfterInitialization()
   → A 不需要代理，原样返回
⑮ A 放入一级缓存                           一级: {A: 原始A, B: 代理B}
```

### 1.4 为什么是三级而不是两级

两级缓存能解决普通的循环依赖，但解决不了"循环依赖 + AOP"共存时的对象一致性问题。

- **三级缓存存的是工厂（lambda）**，不是对象本身
- 工厂被调用时才决定：返回原始对象还是代理对象
- 这就是**延迟决策**——要不要代理，等真正有人来要的时候再说

各级缓存的本质：

| 层级 | 本质 | 解决什么 |
|------|------|----------|
| 三级 | 工厂（函数） | 延迟决策——要不要代理，等用的时候再说 |
| 二级 | 工厂执行后的结果 | 避免重复创建——保证半成品的唯一性 |
| 一级 | 完整成品 | 正常的单例容器 |

### 1.5 没有循环依赖时三级缓存的行为

所有单例 Bean 实例化后都会无条件放入三级缓存（Spring 无法提前判断有没有循环依赖）。但如果没有循环依赖，工厂从头到尾不会被调用，二级缓存也不会参与。Bean 走完完整生命周期后直接放入一级缓存，三级缓存中的工厂被清理掉。

### 1.6 无法解决的循环依赖

- **构造器注入**：实例化阶段就需要对方，还没来得及放入缓存
- **prototype 作用域**：Spring 不缓存 prototype Bean
- **@Async**：后置处理器生成的代理对象与早期暴露的不一致

### 1.7 关键源码

```java
// DefaultSingletonBeanRegistry
protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    Object singletonObject = this.singletonObjects.get(beanName);
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
        singletonObject = this.earlySingletonObjects.get(beanName);
        if (singletonObject == null && allowEarlyReference) {
            synchronized (this.singletonObjects) {
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    singletonObject = this.earlySingletonObjects.get(beanName);
                    if (singletonObject == null) {
                        ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                        if (singletonFactory != null) {
                            singletonObject = singletonFactory.getObject();
                            this.earlySingletonObjects.put(beanName, singletonObject);
                            this.singletonFactories.remove(beanName);
                        }
                    }
                }
            }
        }
    }
    return singletonObject;
}
```

---

## 二、AOP 原理

### 2.1 核心思想

AOP（面向切面编程）的本质是**动态代理**——在不修改目标类源码的情况下，通过代理对象在方法执行前后织入增强逻辑。

通俗理解：代理就是"经纪人"，对外看起来和明星一样（实现相同接口/继承相同类），但在真正干活前后可以加额外操作（谈价格、发通稿）。

AOP 解决的工程问题：把"日志、事务、权限"这些横跨多处的重复逻辑从业务代码中抽出来，让代理自动加上。

### 2.2 两种代理方式

| | JDK 动态代理 | CGLIB 代理 |
|--|--|--|
| 条件 | 目标类实现了接口 | 目标类没有实现接口 |
| 原理 | 基于 `java.lang.reflect.Proxy`，生成接口的实现类 | 基于字节码生成，创建目标类的子类 |
| 限制 | 只能代理接口方法 | 无法代理 `final` 类和 `final` 方法 |

> Spring Boot 2.x 起默认使用 CGLIB，即使目标类实现了接口。

### 2.3 关键概念

- **Aspect（切面）**：横切关注点的模块化，如日志、事务
- **JoinPoint（连接点）**：程序执行的某个点，Spring AOP 中只支持方法级别
- **Pointcut（切入点）**：匹配连接点的表达式，决定在哪些方法上织入
- **Advice（通知）**：在连接点上执行的动作
  - `@Before`：方法执行前
  - `@After`：方法执行后（无论是否异常）
  - `@AfterReturning`：方法正常返回后
  - `@AfterThrowing`：方法抛出异常后
  - `@Around`：包裹方法执行，最强大
- **Weaving（织入）**：将切面应用到目标对象的过程，Spring AOP 在运行时织入

### 2.4 代理调用链（责任链模式）

当调用代理对象的方法时，Spring 获取该方法匹配的所有 Advice，组装成拦截器链，通过 `MethodInvocation.proceed()` 依次执行。

执行顺序：`Around Before → Before → 目标方法 → AfterReturning → After → Around After`

### 2.5 AOP 失效的常见场景

1. **自调用**：同一个类中方法 A 调用方法 B，B 上的切面不生效（走的是 `this` 而非代理对象）
2. **private/final 方法**：CGLIB 无法覆写
3. **非 Spring 管理的对象**：手动 new 出来的对象没有代理

解决自调用：注入自身（`@Autowired` 自己）、`AopContext.currentProxy()`、拆分到不同类。

---

## 三、AOP 与 Bean 初始化的关系

### 3.1 Bean 的完整生命周期

```
① 实例化       new OrderService()
② 属性填充      注入依赖（@Autowired）
③ 初始化前      BeanPostProcessor.postProcessBeforeInitialization()
④ 初始化        @PostConstruct / InitializingBean / init-method
⑤ 初始化后      BeanPostProcessor.postProcessAfterInitialization()  ← AOP 在这！
⑥ 放入容器      singletonObjects
```

### 3.2 AOP 的介入机制

AOP 通过 `AnnotationAwareAspectJAutoProxyCreator`（一个 BeanPostProcessor）在第 ⑤ 步介入：

1. 找出系统里所有的切面（@Aspect 标注的类）
2. 判断当前 Bean 的方法是否匹配某个切点
3. 如果匹配，创建代理对象（JDK/CGLIB），**替换原始 Bean**
4. 容器里存的是代理对象，别人注入时拿到的也是代理

### 3.3 循环依赖时 AOP 的提前触发

正常情况下 AOP 代理在第 ⑤ 步创建。但循环依赖打乱了顺序——别人提前来要这个 Bean 时，三级缓存的工厂会提前执行 AOP 判断，决定返回原始对象还是代理对象。

这就是三级缓存工厂的本质：**把原本该在第 ⑤ 步做的 AOP 判断，按需提前到任何时刻**。

---

## 四、Spring 扩展点

### 4.1 容器级扩展点

#### BeanFactoryPostProcessor

- **时机**：所有 BeanDefinition 加载完毕，但还没实例化任何 Bean
- **能力**：修改 BeanDefinition（改类名、改作用域、改属性值）
- **典型用途**：`PropertySourcesPlaceholderConfigurer` 替换 `${xxx}` 占位符

#### BeanDefinitionRegistryPostProcessor

- **时机**：比 BeanFactoryPostProcessor 更早
- **能力**：动态注册新的 Bean
- **典型用途**：MyBatis 的 `MapperScannerConfigurer` 扫描 @Mapper 注册 Bean、Feign 的 `@EnableFeignClients`

### 4.2 Bean 级扩展点

#### BeanPostProcessor

- **时机**：每个 Bean 初始化前后
- **能力**：可以返回新对象替换原 Bean
- **典型用途**：AOP 代理生成、`@Autowired` 注入、`@PostConstruct` 处理

#### InstantiationAwareBeanPostProcessor

- **时机**：实例化前后、属性填充时
- **能力**：绕过默认实例化、定制属性注入
- **典型用途**：MyBatis Mapper 代理、`@Autowired` 属性注入

### 4.3 Aware 接口族

让 Bean 感知容器信息：

```java
ApplicationContextAware       // 拿到 ApplicationContext
BeanFactoryAware              // 拿到 BeanFactory
BeanNameAware                 // 知道自己的 beanName
EnvironmentAware              // 拿到 Environment（配置信息）
```

典型用途：写 `SpringContextUtil` 工具类，在非 Spring 管理的地方获取 Bean。

### 4.4 Bean 自身的生命周期钩子

初始化回调（执行顺序）：`@PostConstruct` → `afterPropertiesSet()` → `init-method`

销毁回调：`@PreDestroy` → `destroy()` → `destroyMethod`

典型用途：初始化连接池、加载缓存、优雅停机。

### 4.5 容器事件

```java
ContextRefreshedEvent    // 容器刷新完成（所有 Bean 就绪）
ApplicationReadyEvent    // SpringBoot 应用完全就绪
ContextClosedEvent       // 容器关闭
```

典型用途：启动后预热缓存、业务事件解耦。

### 4.6 按场景选择扩展点

| 场景 | 首选扩展点 |
|------|-----------|
| 启动时加载数据、预热 | `ApplicationRunner` / `ApplicationReadyEvent` |
| 动态注册 Bean | `BeanDefinitionRegistryPostProcessor` |
| 修改已有 Bean 定义 | `BeanFactoryPostProcessor` |
| 给特定 Bean 做增强 | `BeanPostProcessor` |
| 在工具类里拿 Spring 容器 | `ApplicationContextAware` |
| 初始化资源（连接池等） | `@PostConstruct` |
| 优雅停机、释放资源 | `@PreDestroy` |
| 模块解耦，事件驱动 | `@EventListener` |

---

## 五、@Autowired 详解

### 5.1 三种注入方式

```java
// ① 字段注入（最常见，但不推荐）
@Autowired
private UserService userService;

// ② 构造器注入（官方推荐）
private final UserService userService;
@Autowired  // Spring 4.3+ 单构造器可省略
public OrderService(UserService userService) {
    this.userService = userService;
}

// ③ Setter 注入
@Autowired
public void setUserService(UserService userService) {
    this.userService = userService;
}
```

### 5.2 装配规则

```
按【类型】匹配
    ↓
找到 0 个 → 报错（除非 required=false）
找到 1 个 → 直接注入
找到多个 → 按【名字】匹配 → 匹配不上则看 @Primary / @Qualifier
```

### 5.3 实现原理：AutowiredAnnotationBeanPostProcessor

`@Autowired` 不是 JVM 魔法，而是通过 `AutowiredAnnotationBeanPostProcessor`（一个 BeanPostProcessor）实现的：

1. **postProcessMergedBeanDefinition()**：扫描类里所有 `@Autowired` 注解的字段和方法，缓存反射信息
2. **postProcessProperties()**：在属性填充阶段，遍历缓存的注入点，从 BeanFactory 找到对应 Bean，通过反射注入

### 5.4 注入方式与循环依赖的关系

| 注入方式 | 三级缓存能解决循环依赖吗 | 原因 |
|---------|:---:|------|
| 字段注入 `@Autowired` | ✅ | 注入在属性填充阶段，Bean 已实例化并放入三级缓存 |
| Setter 注入 | ✅ | 同上 |
| `@Resource` / `@Inject` | ✅ | 同样在属性填充阶段 |
| 构造器注入 | ❌ | 实例化阶段就需要依赖，还没来得及放三级缓存 |

**真正的分界线不是"用什么注解"，而是"注入发生在实例化时还是实例化后"。**

### 5.5 为什么官方推荐构造器注入

1. **依赖不可变**：可以声明 `final`，线程安全
2. **依赖明确**：构造器参数一目了然
3. **强制依赖检查**：缺失直接启动失败，而不是运行时 NPE
4. **便于单元测试**：不用启动 Spring，直接 new
5. **暴露设计问题**：循环依赖报错是在帮你发现设计缺陷

### 5.6 构造器注入循环依赖的解决方案

```java
// 方案 1：@Lazy（最常用）
public A(@Lazy B b) { }  // 注入 B 的代理，延迟加载

// 方案 2：重构代码（根本解法）
// 抽出公共 Service、用事件机制解耦
```

### 5.7 Spring 对循环依赖的态度

- 三级缓存是**历史兼容方案**，不是鼓励循环依赖
- 推荐构造器注入，循环依赖报错是 feature 不是 bug
- **Spring Boot 2.6 起默认禁止循环依赖**（`spring.main.allow-circular-references=false`）
- 方向是逐步淘汰对循环依赖的支持

---

## 六、总结：核心概念串联

```
Bean 生命周期：实例化 → 属性填充 → 初始化前 → 初始化 → 初始化后 → 放入容器
                                      ↑                    ↑
                              @Autowired 注入在这      AOP 代理在这
                              （AutowiredAnnotation     （AbstractAutoProxy
                               BeanPostProcessor）       Creator）

三级缓存：在实例化后、属性填充前放入工厂，为循环依赖兜底
         工厂的作用：延迟决策是否需要 AOP 代理

扩展点：BeanPostProcessor 是核心机制
       @Autowired、AOP、@PostConstruct 都是通过不同的 BeanPostProcessor 实现的
       Spring 的强大不在于自己做了多少事，而在于留了足够多的口子让别人做事
```
