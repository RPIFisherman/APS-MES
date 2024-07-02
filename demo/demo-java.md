# APSDemo.java

## 1. Facts

- We can only produce one product at a time by one machine.
- We can't consider all the possible combinations of products and machines.
- Optimal solution is not guaranteed(only a good solution).

## 2. Assumptions

- Resources are not limited.
- Vialation cost is not considered yet(vialation is always allowed).

## 3. Features

- Use greedy algorithm to find the local best solution.
- Can resume the process from the last saved state(good for what-if analysis).

## 4. Input and output of the algorithms

- A sorted list of products(orders) by any criteria.
- A list of machines.
- A list of switch time between products.

## 5. Diagram

```mermaid
    flowchart TD
        A[Customer Orders] --> B[Factory Orders]
        B --> C[For order in sorted orders]
        C --> D[Find available machines\n apply the rules]
        D --> E[Finding the best machine and assign the order]
        E --> F{More Orders?}
        F -->|Yes| C
        F -->|No| G[End]
```

```mermaid
    flowchart TD
        A[客户订单] --> B[工厂订单]
        B --> C[按排序订单排序]
        C --> D[查找可用机器\n 应用规则]
        D --> E[查找最佳机器并分配订单]
        E --> F{更多订单？}
        F -->|是| C
        F -->|否| G[结束]
```

## 6. Data Structure

```java
  public static class Order {
    public int order_id;
    public String name;
    public int quantity;
    public int due_date;
    public int priority;
    public int earlest_start_date;

    ...
  }

  public static class Machine {
    public int machine_id;
    public String name;
    public int finishing_time;
    public int machine_product_per_hour;
    public List<Order> orders_in_queue;

    ...
  }
```

## 7. Algorithm

**Optimize switch time optimized -o1**

```java
  // store the fist free machines when having same jump time
  public static List<Machine>
  optimize_switch_time_optimize1(final List<Order> orders,
                                 final List<List<Integer>> jump_matrix,
                                 List<Machine> machines) {
    PriorityQueue<Machine> machine_queue =
        new PriorityQueue<>(new CompareMachineByFreeTime());
    for (Order o : orders) {
      machine_queue.clear();
      int machine_queue_switch_time = Integer.MAX_VALUE;
      for (Machine m : machines) {
        int switch_time = getJumpTime(jump_matrix, m.getLastOrder(), o);
        if (switch_time < machine_queue_switch_time) {
          machine_queue.clear();
          machine_queue.add(m);
          machine_queue_switch_time = switch_time;
        } else if (switch_time == machine_queue_switch_time) {
          machine_queue.add(m);
        }
      }
      // find the best machine to put the order
      Machine best_machine = machine_queue.poll();
      // calculate the finishing time
      int finishing_time = best_machine.finishing_time +
                           o.quantity / best_machine.machine_product_per_hour +
                           machine_queue_switch_time;
      // update the finishing time
      best_machine.finishing_time = finishing_time;
      // update the order queue
      best_machine.orders_in_queue.add(o);
    }
    return machines;
  }
```

The function `optimize_switch_time_optimize1` is designed to assign orders to machines in a way that minimizes the switching time between orders, using a priority queue to efficiently find the first free machine with the same switch time. Here's a breakdown of the key procedures:

1. **Initialization of Priority Queue**:

   ```java
   PriorityQueue<Machine> machine_queue = new PriorityQueue<>(new CompareMachineByFreeTime());
   ```

   - A priority queue `machine_queue` is created, using a comparator `CompareMachineByFreeTime` that orders machines by their free time.

2. **Iterating Over Orders**:

   ```java
   for (Order o : orders) {
       machine_queue.clear();
       int machine_queue_switch_time = Integer.MAX_VALUE;
   ```

   - The function iterates through each order in the list of orders.
   - The priority queue `machine_queue` is cleared for each new order.
   - `machine_queue_switch_time` is initialized to `Integer.MAX_VALUE` to track the minimum switch time for the current order.

3. **Finding Machines with Minimum Switch Time**:

   ```java
   for (Machine m : machines) {
       int switch_time = getJumpTime(jump_matrix, m.getLastOrder(), o);
       if (switch_time < machine_queue_switch_time) {
           machine_queue.clear();
           machine_queue.add(m);
           machine_queue_switch_time = switch_time;
       } else if (switch_time == machine_queue_switch_time) {
           machine_queue.add(m);
       }
   }
   ```

   - For each machine, the switch time to the current order is calculated using the `getJumpTime` method.
   - If the calculated switch time is less than the current minimum `machine_queue_switch_time`, the priority queue is cleared and the current machine is added to the queue, and the minimum switch time is updated.
   - If the calculated switch time equals the current minimum, the machine is added to the queue without clearing it.

4. **Assigning Order to the Best Machine**:

   ```java
   Machine best_machine = machine_queue.poll();
   ```

   - The machine with the earliest free time and minimum switch time is retrieved from the priority queue.

5. **Calculating and Updating Finishing Time**:

   ```java
   int finishing_time = best_machine.finishing_time +
                        o.quantity / best_machine.machine_product_per_hour +
                        machine_queue_switch_time;
   best_machine.finishing_time = finishing_time;
   ```

   - The finishing time for the selected machine is calculated by adding the current finishing time of the machine, the time required to process the current order, and the switch time.
   - The machine's finishing time is updated with this new value.

6. **Updating Order Queue for the Machine**:

   ```java
   best_machine.orders_in_queue.add(o);
   ```

   - The current order is added to the machine's order queue.

7. **Returning the List of Machines**:
   ```java
   return machines;
   ```
   - After processing all orders, the list of machines is returned with their updated states.

In summary, the function optimizes the assignment of orders to machines by minimizing switch times using a priority queue to efficiently manage and select the best machine for each order based on their free time and switch time.

```mermaid
flowchart TD
    A[Start] --> B[Get a Sorted List of Orders]
    B --> C[Iterate Over Orders]
    C --> D[Clear Priority Queue of Machines]
    D --> E[Initialize Minimum Switch Time to Infinity]
    E --> F{Iterate Over Machines}
    F --> G[Calculate Switch Time for Each Machine]
    G --> H{Switch Time < Minimum Switch Time?}
    H -->|Yes| I[Clear Priority Queue and Add Machine]
    I --> J[Update Minimum Switch Time]
    H -->|No| K{Switch Time == Minimum Switch Time?}
    K -->|Yes| L[Add Machine to Priority Queue]
    K -->|No| F
    J --> F
    L --> F
    F -->|End of loop| M[Retrieve Machine with Minimum Switch Time]
    M --> N[Calculate Finishing Time]
    N --> O[Update Machine's Finishing Time]
    O --> P[Add Order to Machine's Queue]
    P --> Q{More Orders?}
    Q -->|Yes| C
    Q -->|No| R[Return Machines List]
    R --> S[End]
```

```mermaid
flowchart TD
    A[开始] --> B[订单排序列表]
    B-->C[迭代订单]
    C-->D[清除每个订单的机器优先队列]
    D --> E[将最小切换时间初始化为无穷大]
    E --> F{迭代机器}
    F --> G[计算最小切换时间]
    F-->G[计算每台机器的切换时间]
    G --> H{切换时间 < 最短切换时间？}
    H-->|是| I[清除优先队列并添加机]
    I --> J[更新最短切换时间]
    H -->|No| K{切换时间 == 最少切换时间？}
    K -->|Yes| L[将机器添加到优先队列]
    K -->|No| F
    K -->|No| F
    J --> F
    L --> F
    F-->|循环结束| M[检索具有最短切换时间的机器]
    M-->N[计算完成时间]
    N-->O[更新机器的加工时间]
    O --> P[向机器队列添加订单]
    P --> Q{更多订单？}
    Q -->|Yes| C
    Q-->|否| R[返回机器列表]
    R-->S[结束]
```

## 8. Problems

1. Random even-distributed orders are used for testing.
2. Better way to summarize the results.
3. Data structure choice?

## 9. TODO

1. Real-world test data.
2. More complex rules.
3. ...
