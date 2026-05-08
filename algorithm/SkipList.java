import java.util.Random;

/**
 * 跳表（Skip List）的 Java 实现
 * 
 * 跳表是一种基于有序链表的数据结构，通过多层索引实现 O(log n) 的查找、插入、删除。
 * Redis 的有序集合（ZSet）底层就使用了跳表。
 * 
 * 核心思想：
 * - 最底层是一个完整的有序链表，包含所有数据
 * - 上面的层是索引层，通过随机化决定节点是否"晋升"到更高层
 * - 查找时从最高层开始，逐层下降，快速定位目标位置
 */
public class SkipList {

    // ==================== 常量 ====================

    /** 最大层数，Redis 中为 32，这里用 16 足够演示 */
    private static final int MAX_LEVEL = 16;

    /** 晋升概率，每个节点有 50% 的概率多升一层（Redis 用 25%） */
    private static final double PROMOTE_PROBABILITY = 0.5;

    // ==================== 节点定义 ====================

    /**
     * 跳表节点
     * 
     * 每个节点包含：
     * - value: 存储的值
     * - next[]: 每一层的前进指针数组
     *   next[0] 是最底层（第1层）的下一个节点
     *   next[1] 是第2层的下一个节点
     *   ...以此类推
     */
    static class Node {
        int value;
        Node[] next; // next[i] 表示第 i 层的下一个节点

        /**
         * @param value 节点存储的值
         * @param level 该节点的层数（决定了 next 数组的大小）
         */
        Node(int value, int level) {
            this.value = value;
            this.next = new Node[level];
        }
    }

    // ==================== 跳表属性 ====================

    /** 头节点（哨兵节点），不存储实际数据，作为所有层的起点 */
    private final Node head;

    /** 当前跳表的最大层数（随着插入动态变化） */
    private int currentLevel;

    /** 跳表中的元素个数 */
    private int size;

    /** 随机数生成器，用于决定新节点的层数 */
    private final Random random;

    // ==================== 构造方法 ====================

    public SkipList() {
        // 头节点拥有最大层数，确保任何层都能从 head 出发
        this.head = new Node(-1, MAX_LEVEL);
        this.currentLevel = 1; // 初始只有1层
        this.size = 0;
        this.random = new Random();
    }

    // ==================== 核心方法 ====================

    /**
     * 随机生成新节点的层数
     * 
     * 原理：类似抛硬币
     * - 每次有 PROMOTE_PROBABILITY 的概率多升一层
     * - 直到"抛出反面"或达到最大层数为止
     * 
     * 这样大部分节点只有1层，少数节点有2层，更少的有3层...
     * 形成金字塔结构，保证查找效率
     */
    private int randomLevel() {
        int level = 1;
        // random.nextDouble() 返回 [0, 1) 的随机数
        while (random.nextDouble() < PROMOTE_PROBABILITY && level < MAX_LEVEL) {
            level++;
        }
        return level;
    }

    /**
     * 查找目标值是否存在
     * 
     * 查找过程：
     * 1. 从最高层的 head 开始
     * 2. 在当前层尽量往右走（只要右边节点的值 < target）
     * 3. 走不动了就下降一层，继续往右走
     * 4. 到达最底层后，检查下一个节点是否就是 target
     * 
     * @param target 要查找的值
     * @return 是否存在
     */
    public boolean search(int target) {
        Node current = head;

        // 从最高层往下逐层查找
        // currentLevel - 1 是最高层的索引（数组从0开始）
        for (int i = currentLevel - 1; i >= 0; i--) {
            // 在第 i 层，尽量往右走
            // 条件：右边有节点 且 右边节点的值 < target
            while (current.next[i] != null && current.next[i].value < target) {
                current = current.next[i]; // 向右移动
            }
            // 走不动了（右边为空 或 右边值 >= target），下降到下一层
        }

        // 循环结束后，current 停在最底层中"最后一个值 < target 的节点"
        // 那么 current.next[0] 就是"第一个值 >= target 的节点"
        Node candidate = current.next[0];

        // 判断是否找到
        return candidate != null && candidate.value == target;
    }

    /**
     * 插入一个值
     * 
     * 插入过程：
     * 1. 找到每一层中"应该插在哪个节点后面"（记录在 update 数组中）
     * 2. 随机生成新节点的层数
     * 3. 在对应的每一层执行链表插入操作
     * 
     * @param value 要插入的值
     */
    public void insert(int value) {
        // update[i] 记录第 i 层中，新节点应该插在哪个节点的后面
        Node[] update = new Node[MAX_LEVEL];

        Node current = head;

        // 从最高层往下，找到每一层的插入位置
        for (int i = currentLevel - 1; i >= 0; i--) {
            // 在第 i 层往右走，找到最后一个值 < value 的节点
            while (current.next[i] != null && current.next[i].value < value) {
                current = current.next[i];
            }
            // current 就是第 i 层中，新节点的前驱节点
            update[i] = current;
        }

        // 随机决定新节点的层数
        int newLevel = randomLevel();

        // 如果新节点的层数超过了当前跳表的最大层数
        // 需要把多出来的那些层的 update 设为 head（从 head 开始连接）
        if (newLevel > currentLevel) {
            for (int i = currentLevel; i < newLevel; i++) {
                update[i] = head;
            }
            currentLevel = newLevel; // 更新跳表当前最大层数
        }

        // 创建新节点
        Node newNode = new Node(value, newLevel);

        // 在每一层执行链表插入（和普通链表插入一模一样）
        // newNode 插到 update[i] 的后面
        for (int i = 0; i < newLevel; i++) {
            // 新节点的 next 指向 update[i] 原来的 next
            newNode.next[i] = update[i].next[i];
            // update[i] 的 next 指向新节点
            update[i].next[i] = newNode;
        }

        size++;
    }

    /**
     * 删除一个值（如果存在）
     * 
     * 删除过程：
     * 1. 找到每一层中目标节点的前驱（记录在 update 数组中）
     * 2. 如果目标存在，在每一层执行链表删除操作
     * 3. 如果删除后最高层为空，降低 currentLevel
     * 
     * @param value 要删除的值
     * @return 是否删除成功（值不存在则返回 false）
     */
    public boolean delete(int value) {
        Node[] update = new Node[MAX_LEVEL];
        Node current = head;

        // 从最高层往下，找到每一层中目标节点的前驱
        for (int i = currentLevel - 1; i >= 0; i--) {
            while (current.next[i] != null && current.next[i].value < value) {
                current = current.next[i];
            }
            update[i] = current;
        }

        // 定位到最底层的候选节点
        Node target = current.next[0];

        // 如果目标不存在，直接返回
        if (target == null || target.value != value) {
            return false;
        }

        // 从底层到目标节点的最高层，逐层删除
        for (int i = 0; i < currentLevel; i++) {
            // 如果这一层 update[i] 的下一个不是目标节点，说明目标节点没有这一层
            // 因为目标节点的层数可能小于 currentLevel
            if (update[i].next[i] != target) {
                break;
            }
            // 标准链表删除：跳过目标节点
            update[i].next[i] = target.next[i];
        }

        // 如果删除后最高层变空了，降低层数
        // （避免无意义的空层浪费查找时间）
        while (currentLevel > 1 && head.next[currentLevel - 1] == null) {
            currentLevel--;
        }

        size--;
        return true;
    }

    // ==================== 辅助方法 ====================

    /** 获取跳表元素个数 */
    public int size() {
        return size;
    }

    /** 判断跳表是否为空 */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 打印跳表的结构（从最高层到最底层）
     * 用于直观观察跳表的索引分布
     */
    public void printSkipList() {
        System.out.println("===== 跳表结构（共 " + currentLevel + " 层，" + size + " 个元素）=====");
        for (int i = currentLevel - 1; i >= 0; i--) {
            System.out.print("第" + (i + 1) + "层: head");
            Node current = head.next[i];
            while (current != null) {
                System.out.print(" → " + current.value);
                current = current.next[i];
            }
            System.out.println(" → null");
        }
        System.out.println("================================================");
    }

    // ==================== 测试 ====================

    public static void main(String[] args) {
        SkipList skipList = new SkipList();

        // 插入一些数据
        int[] data = {3, 6, 7, 9, 12, 15, 19, 21, 25, 30};
        System.out.println("依次插入: 3, 6, 7, 9, 12, 15, 19, 21, 25, 30\n");

        for (int val : data) {
            skipList.insert(val);
        }

        // 打印跳表结构
        skipList.printSkipList();

        // 查找测试
        System.out.println("\n查找 12: " + skipList.search(12));  // true
        System.out.println("查找 8: " + skipList.search(8));    // false
        System.out.println("查找 30: " + skipList.search(30));  // true

        // 删除测试
        System.out.println("\n删除 12: " + skipList.delete(12)); // true
        System.out.println("删除 8: " + skipList.delete(8));   // false（不存在）
        System.out.println("查找 12: " + skipList.search(12));  // false（已删除）

        System.out.println("\n删除 12 后的结构:");
        skipList.printSkipList();
    }
}
