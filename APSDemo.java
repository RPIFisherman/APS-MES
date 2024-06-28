// 6/25/2024 Yuyang Gong
// Herustic algorithm for APS demo

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.System;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

class Pair<T, U> {
  public T first;
  public U second;

  public Pair(T first, U second) {
    this.first = first;
    this.second = second;
  }
}

class APSDemo {
  public static final int RAND_SEED = 1337;
  public static final int PRODUCT_NUM = 30000; // 30000 need ~4GB memory
  public static final int MACHINE_NUM = 20; // >200 traverse get much slower
  public static final int PRIORITY_NUM = 5;
  public static final int MACHINE_PRODUCT_PER_HOUR = 500;

  public static final int MIN_PRODUCT_QUANTITY = 1000;
  public static final int MAX_PRODUCT_QUANTITY = 10000 - MIN_PRODUCT_QUANTITY;
  public static final int MIN_SWITCH_TIME = 1;
  public static final int MAX_SWITCH_TIME = 3 - MIN_SWITCH_TIME;
  public static final int MAX_ESD_DATE = 10;
  public static final int MIN_DUE_START_INTERVAL = 30;
  // public static final int MAX_DDL_DATE = 90 - MIN_DUE_START_INTERVAL;
  public static final int MAX_DDL_DATE =
      (int)(((double)PRODUCT_NUM * MAX_PRODUCT_QUANTITY / MACHINE_NUM /
             MACHINE_PRODUCT_PER_HOUR / 24) *
            0.65) -
      MIN_DUE_START_INTERVAL; // dynamic interval index > 0.7 is loose bound

  // 0: index Order           initial order
  // 1: priority > ddl > esd
  //                          priority is the most important
  // 2: esd > priority > ddl
  //                          not vialate the esd is the most important
  // 3: ddl > priority > esd
  //                          not vialate the ddl is the most important
  // 4: ddl > quantity > priority
  //                          not vialate the ddl is the most important,
  //                      XXX: good for loose ddl model, unstable for tight ddl
  // 5: quantity > ddl > priority
  //                          utilization efficiency is the most important
  // 6: quantity_lower > ddl > priority
  //                          if the ddl is too close(must vialate), maker the
  //                          smaller quantity first to max the unvialate order
  public static final int SORT_METHOD = 4;
  public static final int PRINT_FLAG = 2; // 0: no print, 1: print running time,
                                          // 2: print summay 3. print all
  public static final boolean OUTPUT_JUMP_MATRIX = false;
  public static final boolean OUTPUT_SCHEDULE = false;

  public static DecimalFormat df = new DecimalFormat("0.00");

  // TODO add sales order, production order, extend both from order
  public static class Order {
    public int order_id;
    public String name;
    public int quantity;
    public int due_date;
    public int priority;
    public int earlest_start_date;

    public Order(int id, String n, int q, int d, int p, int e) {
      order_id = id;
      name = n;
      quantity = q;
      due_date = d;
      priority = p;
      earlest_start_date = e;
    }
  }

  public static class Machine {
    public int machine_id;
    public String name;
    public int finishing_time;
    public int machine_product_per_hour;
    public List<Order> orders_in_queue;

    public Machine(int id, String n, int c, int mph) {
      machine_id = id;
      name = n;
      finishing_time = c;
      machine_product_per_hour = mph;
      orders_in_queue = new ArrayList<>();
    }

    public Machine(Machine m) {
      machine_id = m.machine_id;
      name = m.name;
      finishing_time = m.finishing_time;
      machine_product_per_hour = m.machine_product_per_hour;
      orders_in_queue = new ArrayList<>(m.orders_in_queue);
    }

    public Order getLastOrder() {
      return orders_in_queue.size() == 0
          ? null
          : orders_in_queue.get(orders_in_queue.size() - 1);
    }
  }

  public static List<Order> generateRandomOrder(int size) {
    Random rand = new Random(RAND_SEED);
    List<Order> orders = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      int earlest_start_date = rand.nextInt(MAX_ESD_DATE);
      int due_date = rand.nextInt(MAX_DDL_DATE) + earlest_start_date +
                     MIN_DUE_START_INTERVAL;
      Order o = new Order(
          i, "order" + (i < 10 ? "0" + i : i),
          ((rand.nextInt(MAX_PRODUCT_QUANTITY) + MIN_PRODUCT_QUANTITY) /
           MACHINE_PRODUCT_PER_HOUR) *
              MACHINE_PRODUCT_PER_HOUR,
          due_date, rand.nextInt(PRIORITY_NUM), earlest_start_date);
      orders.add(o);
    }
    return orders;
  }

  public static List<List<Integer>> generateRandomJumpMatrix(int size) {
    Random rand = new Random(RAND_SEED);
    List<List<Integer>> jump_matrix = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      List<Integer> row = new ArrayList<>();
      for (int j = 0; j < size; j++) {
        row.add(rand.nextInt(MAX_SWITCH_TIME) + MIN_SWITCH_TIME);
      }
      jump_matrix.add(row);
    }
    return jump_matrix;
  }

  public static Integer getJumpTime(final List<List<Integer>> jump_matrix,
                                    final Order o1, final Order o2) {
    return o1 == null ? 0 : jump_matrix.get(o1.order_id).get(o2.order_id);
  }

  public static List<Machine> generateRandomMachine(int size) {
    List<Machine> machines = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      Machine m = new Machine(i, "machine" + (i < 10 ? "0" + i : i), 0,
                              MACHINE_PRODUCT_PER_HOUR);
      machines.add(m);
    }
    return machines;
  }

  // compare function 1
  // 1. priority is higher
  // 2. priority same, due_date closer
  // 3. priority same, due_date same, earlest_start_date closer
  public static class CompareOrder_priority_ddl_esd
      implements Comparator<Order> {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.priority < o2.priority ||
              (o1.priority == o2.priority && o1.due_date > o2.due_date) ||
              (o1.priority == o2.priority && o1.due_date == o2.due_date &&
               o1.earlest_start_date > o2.earlest_start_date)
          ? 1
          : -1;
    }
  }

  // compare function 2
  // 1. earlest_start_date closer
  // 2. earlest_start_date same, priority is higher
  // 3. priority same, earlest_start_date same, due_date closer
  public static class CompareOrder_esd_priority_ddl
      implements Comparator<Order> {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.earlest_start_date < o2.earlest_start_date ||
              (o1.earlest_start_date == o2.earlest_start_date &&
               o1.priority > o2.priority) ||
              (o1.earlest_start_date == o2.earlest_start_date &&
               o1.priority == o2.priority && o1.due_date < o2.due_date)
          ? 1
          : -1;
    }
  }

  // compare function 3
  // 1. due_date closer
  // 2. due_date same, priority is higher
  // 3. priority same, due_date same, earlest_start_date closer
  public static class CompareOrder_ddl_priority_esd
      implements Comparator<Order> {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.due_date > o2.due_date ||
              (o1.due_date == o2.due_date && o1.priority < o2.priority) ||
              (o1.due_date == o2.due_date && o1.priority == o2.priority &&
               o1.earlest_start_date > o2.earlest_start_date)
          ? 1
          : -1;
    }
  }

  // compare function 4
  // 1. due_date closer
  // 2. due_date same, quantity is larger
  // 3. quantity same, due_date same, priority is higher
  public static class CompareOrder_ddl_quantity_priority
      implements Comparator<Order> {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.due_date > o2.due_date ||
              (o1.due_date == o2.due_date && o1.quantity < o2.quantity) ||
              (o1.due_date == o2.due_date && o1.quantity == o2.quantity &&
               o1.priority < o2.priority)
          ? 1
          : -1;
    }
  }

  // compare function 5
  // 1. quantity is higher
  // 2. quantity same, due_date closer
  // 3. due_date same, priority is higher
  public static class CompareOrder_quantity_ddl_priority
      implements Comparator<Order> {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.quantity < o2.quantity ||
              (o1.quantity == o2.quantity && o1.due_date > o2.due_date) ||
              (o1.quantity == o2.quantity && o1.due_date == o2.due_date &&
               o1.priority < o2.priority)
          ? 1
          : -1;
    }
  }

  // compare function 6
  // 1. quantity is lower
  // 2. quantity same, due_date closer
  // 3. due_date same, priority is higher
  public static class CompareOrder_quantity_lower_ddl_priority
      implements Comparator<Order> {
    @Override
    public int compare(Order o1, Order o2) {
      return o1.quantity > o2.quantity ||
              (o1.quantity == o2.quantity && o1.due_date > o2.due_date) ||
              (o1.quantity == o2.quantity && o1.due_date == o2.due_date &&
               o1.priority < o2.priority)
          ? 1
          : -1;
    }
  }

  // best machine to put the order
  public static class CompareMachineByFreeTime implements Comparator<Machine> {
    @Override
    public int compare(Machine m1, Machine m2) {
      return m1.finishing_time > m2.finishing_time ||
              (m1.finishing_time == m2.finishing_time &&
               m1.machine_id > m2.machine_id)
          ? 1
          : -1;
    }
  }

  public static PriorityQueue<Machine>
  first_free_Machines(final List<Order> orders,
                      final List<List<Integer>> jump_matrix,
                      List<Machine> machines) {
    PriorityQueue<Machine> machine_queue =
        new PriorityQueue<>(new CompareMachineByFreeTime());
    for (Machine m : machines) {
      machine_queue.add(m);
    }

    for (Order o : orders) {
      // find the best machine to put the order
      Machine best_machine = machine_queue.poll();
      // calculate the finishing time
      int finishing_time =
          best_machine.finishing_time +
          o.quantity / best_machine.machine_product_per_hour +
          (best_machine.orders_in_queue.isEmpty()
               ? 0
               : getJumpTime(jump_matrix, best_machine.getLastOrder(), o));
      // update the finishing time
      best_machine.finishing_time = finishing_time;
      // update the order queue
      best_machine.orders_in_queue.add(o);
      // push the machine back to the queue
      machine_queue.add(best_machine);
    }
    return machine_queue;
  }

  // Optimize first_free_Machines with checking the two orders at the same time
  public static PriorityQueue<Machine>
  first_free_Machines_optimize1(final List<Order> orders,
                                final List<List<Integer>> jump_matrix,
                                List<Machine> machines) {
    PriorityQueue<Machine> machine_queue =
        new PriorityQueue<>(new CompareMachineByFreeTime());
    for (Machine m : machines) {
      machine_queue.add(m);
    }

    Order o1 = orders.get(0);
    for (int i = 1; i < orders.size(); i++) {
      Order o2 = orders.get(i);
      // find the best machine to put the order
      Machine best_machine = machine_queue.poll();
      // calculate the finishing time
      int required_time1 =
          o1.quantity / best_machine.machine_product_per_hour +
          (best_machine.orders_in_queue.isEmpty()
               ? 0
               : getJumpTime(jump_matrix, best_machine.getLastOrder(), o1));
      int required_time2 =
          o2.quantity / best_machine.machine_product_per_hour +
          (best_machine.orders_in_queue.isEmpty()
               ? 0
               : getJumpTime(jump_matrix, best_machine.getLastOrder(), o2));
      // 1. if o1 has higher priority, put o1 first
      // 2. same priority, if rt1 + rt2 < both o1 and o2's due date, put the one
      // with shorter switch time first
      // 3. same priority, if rt1 + rt2 > both o1 and o2's due date, put the one
      // with longer due date first
      // 4. same priority, if rt1 + rt2 < one of the order's due date, put the
      // one exceed the due date first assign the second one to o1 and compare
      // again next time
      // TODO: other rules to optimize the schedule
      // FIXME: Potential bug: the output is not better than the original ???
      if (o1.priority > o2.priority) {
        best_machine.finishing_time += required_time1;
        best_machine.orders_in_queue.add(o1);
        o1 = o2;
      } else if (o1.priority == o2.priority) {
        int estimate_time = best_machine.finishing_time + required_time1 +
                            required_time2 + getJumpTime(jump_matrix, o1, o2);
        if (estimate_time < o1.due_date && estimate_time < o2.due_date) {
          // have enough time to finish both orders
          // find the shortest jump time sequence
          int jump_time1 =
              getJumpTime(jump_matrix, best_machine.getLastOrder(), o1) +
              getJumpTime(jump_matrix, o1, o2);
          int jump_time2 =
              getJumpTime(jump_matrix, best_machine.getLastOrder(), o2) +
              getJumpTime(jump_matrix, o2, o1);
          if (jump_time1 < jump_time2) {
            best_machine.finishing_time += required_time1;
            best_machine.orders_in_queue.add(o1);
            o1 = o2;
          } else {
            best_machine.finishing_time += required_time2;
            best_machine.orders_in_queue.add(o2);
          }
        } else {
          // if one of the order exceed the due date,
          // put the one exceed the due day first
          if (o1.due_date < o2.due_date) {
            best_machine.finishing_time += required_time1;
            best_machine.orders_in_queue.add(o1);
            o1 = o2;
          } else {
            best_machine.finishing_time += required_time2;
            best_machine.orders_in_queue.add(o2);
          }
        }
      } else { // if (o1.priority < o2.priority)
        best_machine.finishing_time += required_time2;
        best_machine.orders_in_queue.add(o2);
      }
      // push the machine back to the queue
      machine_queue.add(best_machine);
    }

    // add the last order
    Machine best_machine = machine_queue.poll();
    int required_time =
        o1.quantity / best_machine.machine_product_per_hour +
        (best_machine.orders_in_queue.isEmpty()
             ? 0
             : getJumpTime(jump_matrix, best_machine.getLastOrder(), o1));
    best_machine.finishing_time += required_time;
    best_machine.orders_in_queue.add(o1);
    machine_queue.add(best_machine);

    return machine_queue;
  }

  // optimize the jumping/switch time
  public static List<Machine>
  optimize_switch_time(final List<Order> orders,
                       final List<List<Integer>> jump_matrix,
                       List<Machine> machines) {
    for (Order o : orders) {
      // find the best machine to put the order
      Machine best_machine = machines.get(0);
      int best_machine_jump_time =
          getJumpTime(jump_matrix, best_machine.getLastOrder(), o);
      for (int i = 1; i < machines.size(); i++) {
        Machine m = machines.get(i);
        int jump_time = getJumpTime(jump_matrix, m.getLastOrder(), o);
        if (best_machine_jump_time > jump_time) {
          best_machine = m;
          best_machine_jump_time = jump_time;
        }
      }
      // calculate the finishing time
      int finishing_time = best_machine.finishing_time +
                           o.quantity / best_machine.machine_product_per_hour +
                           best_machine_jump_time;
      // update the finishing time
      best_machine.finishing_time = finishing_time;
      // update the order queue
      best_machine.orders_in_queue.add(o);
    }
    return machines;
  }

  // optimize optimize_switch_time with using a priority queue to
  // store the fist free machine when having same jump time
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

  public static void printOrders(List<Order> orders) {
    for (Order o : orders) {
      System.out.println("Order: " + o.name + " Quantity: " + o.quantity +
                         " Due Date: " + o.due_date +
                         " Priority: " + o.priority +
                         " Earlest Start Date: " + o.earlest_start_date);
    }
  }

  public static void printMachine(List<Machine> machines, boolean print_order) {
    for (Machine m : machines) {
      System.out.print("Machine: " + m.name +
                       " Estimate Finishing Time: " + m.finishing_time);
      int sum_of_order = 0;
      for (Order o : m.orders_in_queue) {
        sum_of_order += o.quantity;
      }
      System.out.println(
          " Total Order: " + sum_of_order + " Actual Finishing Time: " +
          df.format((double)sum_of_order / m.machine_product_per_hour / 24) +
          " days");
      // printout the orders in the machine
      if (print_order) {
        for (Order o : m.orders_in_queue) {
          System.out.println("\tOrder: " + o.name + " Quantity: " + o.quantity +
                             " Due Date: " + o.due_date +
                             " Priority: " + o.priority +
                             " Earlest Start Date: " + o.earlest_start_date);
        }
      }
    }
  }

  public static void evaluateSchedule(final List<List<Integer>> jump_matrix,
                                      final List<Machine> machines,
                                      boolean verbose) {

    ArrayList<Double> total_switch_time = new ArrayList<>();
    ArrayList<Double> total_work_time = new ArrayList<>();
    ArrayList<Pair<Order, Integer>> total_order_on_time = new ArrayList<>();
    ArrayList<Pair<Order, Integer>> total_order_late = new ArrayList<>();
    for (Machine m : machines) {
      Integer previous_order = -1;
      double work_time = 0;
      double switch_time = 0;
      for (Order o : m.orders_in_queue) {
        work_time += o.quantity / m.machine_product_per_hour;
        if (previous_order != -1) {
          switch_time += jump_matrix.get(previous_order).get(o.order_id);
        }
        if (work_time + switch_time > o.due_date * 24) {
          total_order_late.add(new Pair<>(o, o.due_date - (int)work_time));
        } else {
          total_order_on_time.add(new Pair<>(o, o.due_date - (int)work_time));
        }
        previous_order = o.order_id;
      }
      total_switch_time.add(switch_time);
      total_work_time.add(work_time);
    }

    if (verbose) {
      for (int i = 0; i < machines.size(); i++) {
        System.out.println(
            "Machine: " + machines.get(i).name +
            " Total Switch Time: " + total_switch_time.get(i) +
            " Total Work Time: " + total_work_time.get(i) + " Utilization: " +
            df.format(total_work_time.get(i) /
                      (total_work_time.get(i) + total_switch_time.get(i)) *
                      100) +
            "%");
      }
      for (int i = 0; i < total_order_on_time.size(); i++) {
        System.out.println(
            "Order: " + total_order_on_time.get(i).first.name + " on time"
            + " Remaining Time: " + total_order_on_time.get(i).second);
      }
      for (int i = 0; i < total_order_late.size(); i++) {
        System.out.println("Order: " + total_order_late.get(i).first.name +
                           " late"
                           + " Late Time: " + total_order_late.get(i).second);
      }
    }
    int total_switch_time_sum =
        total_switch_time.stream().mapToInt(Double::intValue).sum();
    int total_work_time_sum =
        total_work_time.stream().mapToInt(Double::intValue).sum();
    // switch time range:
    int total_switch_time_max =
        total_switch_time.stream().mapToInt(Double::intValue).max().getAsInt();
    int total_switch_time_min =
        total_switch_time.stream().mapToInt(Double::intValue).min().getAsInt();
    // work time range:
    int total_work_time_max =
        total_work_time.stream().mapToInt(Double::intValue).max().getAsInt();

    int total_work_time_min =
        total_work_time.stream().mapToInt(Double::intValue).min().getAsInt();

    int total_order_on_time_sum = total_order_on_time.size();
    int total_order_late_sum = total_order_late.size();
    System.out.println("Total Switch Time(" + total_switch_time_min + "-" +
                       total_switch_time_max + ")[" +
                       (total_switch_time_max - total_switch_time_min) +
                       "]: " + total_switch_time_sum + " Total Work Time(" +
                       total_work_time_min + "-" + total_work_time_max + ")[" +
                       (total_work_time_max - total_work_time_min) +
                       "]: " + total_work_time_sum + " Utilization: " +
                       df.format((double)total_work_time_sum /
                                 (total_work_time_sum + total_switch_time_sum) *
                                 100) +
                       "%");
    System.out.println("Total Order on time: " + total_order_on_time_sum +
                       " Total Order late: " + total_order_late_sum);
  }

  public static void outputJPMatrix2CSV(List<List<Integer>> jump_matrix,
                                        String filename) {
    try {
      File file = new File(filename);
      FileWriter fw = new FileWriter(file);
      for (int i = 0; i < jump_matrix.size(); i++) {
        for (int j = 0; j < jump_matrix.get(i).size(); j++) {
          fw.write(jump_matrix.get(i).get(j).toString());
          if (j != jump_matrix.get(i).size() - 1) {
            fw.write(",");
          }
        }
        fw.write("\n");
      }
      fw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void outputSchedule2CSV(List<Machine> machines,
                                        String filename) {
    try {
      File file = new File(filename);
      FileWriter fw = new FileWriter(file);

      // find the max length of the order queue
      int max_order_queue_length = 0;
      for (Machine m : machines) {
        if (m.orders_in_queue.size() > max_order_queue_length) {
          max_order_queue_length = m.orders_in_queue.size();
        }
      }

      // write the header
      fw.write("Machine ID,Machine Product Per Hour,");
      for (int i = 0; i < max_order_queue_length; i++) {
        fw.write("Order ID,Quantity,Priority,Due Date,Earliest Start Date");
        if (i != max_order_queue_length - 1) {
          fw.write(",");
        }
      }
      fw.write("\n");

      // write the content
      for (Machine m : machines) {
        fw.write(m.machine_id + "," + m.machine_product_per_hour + ",");
        for (Order o : m.orders_in_queue) {
          fw.write(o.order_id + "," + o.quantity + "," + o.priority + "," +
                   o.due_date + "," + o.earlest_start_date);
          if (o != m.orders_in_queue.get(m.orders_in_queue.size() - 1)) {
            fw.write(",");
          }
        }
        fw.write("\n");
      }
      fw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void cleanMachine(List<Machine> machines) {
    for (Machine m : machines) {
      m.finishing_time = 0;
      m.orders_in_queue.clear();
    }
  }

  public static void main(String[] args) {
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
    // print out the final static parameters
    System.out.println("\n");
    System.out.print(
        "Product Num: " + PRODUCT_NUM + " Machine Num: " + MACHINE_NUM +
        " Priority Num: " + PRIORITY_NUM +
        " Machine Product Per Hour: " + MACHINE_PRODUCT_PER_HOUR +
        " Min Product Quantity: " + MIN_PRODUCT_QUANTITY +
        " Max Product Quantity: " +
        (MAX_PRODUCT_QUANTITY + MIN_PRODUCT_QUANTITY) +
        "\nMin Switch Time: " + MIN_SWITCH_TIME + " Max Switch Time: " +
        (MAX_SWITCH_TIME + MIN_SWITCH_TIME) + " Max ESD Date: " + MAX_ESD_DATE +
        " Min Due Start Interval: " + MIN_DUE_START_INTERVAL +
        " Max DDL Date: " + (MAX_DDL_DATE + MIN_DUE_START_INTERVAL) +
        " Sort Method: ");
    List<Order> orders = generateRandomOrder(PRODUCT_NUM);
    // sort orders by priority and due_date
    switch (SORT_METHOD) {
    case 1:
      System.out.print("PRIORITY DDL ESD");
      Collections.sort(orders, new CompareOrder_priority_ddl_esd());
      break;
    case 2:
      System.out.print("ESD PRIORITY DDL");
      Collections.sort(orders, new CompareOrder_esd_priority_ddl());
      break;
    case 3:
      System.out.print("DDL PRIORITY ESD");
      Collections.sort(orders, new CompareOrder_ddl_priority_esd());
      break;
    case 4:
      System.out.print("DDL QUANTITY PRIORITY");
      Collections.sort(orders, new CompareOrder_ddl_quantity_priority());
      break;
    case 5:
      System.out.print("QUANTITY DDL PRIORITY");
      Collections.sort(orders, new CompareOrder_quantity_ddl_priority());
      break;
    case 6:
      System.out.print("QUANTITY_LOWER DDL PRIORITY");
      Collections.sort(orders, new CompareOrder_quantity_lower_ddl_priority());
      break;
    default:
      System.out.print("INDEX ORDER");
      break;
    }
    System.out.println("\n");

    List<List<Integer>> jump_matrix = generateRandomJumpMatrix(PRODUCT_NUM);
    List<Machine> machines = generateRandomMachine(MACHINE_NUM);
    if (OUTPUT_JUMP_MATRIX) {
      outputJPMatrix2CSV(jump_matrix, "jump_matrix.csv");
    }

    long startTime = System.currentTimeMillis();
    first_free_Machines(orders, jump_matrix, machines);
    long endTime = System.currentTimeMillis();
    switch (PRINT_FLAG) {
    case 4:
      printOrders(orders);
    case 3:
      printMachine(machines, PRINT_FLAG >= 4);
    case 2:
      evaluateSchedule(jump_matrix, machines, PRINT_FLAG >= 3);
    case 1:
      System.out.println("First free machine time: " + (endTime - startTime) +
                         "ms\n");
    default:
      break;
    }
    if (OUTPUT_SCHEDULE) {
      outputSchedule2CSV(machines, "schedule.csv");
    }

    cleanMachine(machines);
    startTime = System.currentTimeMillis();
    first_free_Machines_optimize1(orders, jump_matrix, machines);
    endTime = System.currentTimeMillis();
    switch (PRINT_FLAG) {
    case 3:
      printMachine(machines, PRINT_FLAG >= 4);
    case 2:
      evaluateSchedule(jump_matrix, machines, PRINT_FLAG >= 3);
    case 1:
      System.out.println("First free machine optimized -o1 time: " +
                         (endTime - startTime) + "ms\n");
    default:
      break;
    }
    if (OUTPUT_SCHEDULE) {
      outputSchedule2CSV(machines, "schedule_optimize1.csv");
    }

    cleanMachine(machines);
    startTime = System.currentTimeMillis();
    optimize_switch_time(orders, jump_matrix, machines);
    endTime = System.currentTimeMillis();
    switch (PRINT_FLAG) {
    case 3:
      printMachine(machines, PRINT_FLAG >= 4);
    case 2:
      evaluateSchedule(jump_matrix, machines, PRINT_FLAG >= 3);
    case 1:
      System.out.println("Optimize switch time time: " + (endTime - startTime) +
                         "ms\n");
    default:
      break;
    }
    if (OUTPUT_SCHEDULE) {
      outputSchedule2CSV(machines, "schedule_switch_time.csv");
    }

    cleanMachine(machines);
    startTime = System.currentTimeMillis();
    optimize_switch_time_optimize1(orders, jump_matrix, machines);
    endTime = System.currentTimeMillis();
    switch (PRINT_FLAG) {
    case 3:
      printMachine(machines, PRINT_FLAG >= 4);
    case 2:
      evaluateSchedule(jump_matrix, machines, PRINT_FLAG >= 3);
    case 1:
      System.out.println("Optimize switch time optimized -o1 time: " +
                         (endTime - startTime) + "ms\n");
      System.out.println(
          "Max Heap Memory: " + heapMemoryUsage.getMax() / 1024 / 1024 + "MB");
    default:
      break;
    }
    if (OUTPUT_SCHEDULE) {
      outputSchedule2CSV(machines, "schedule_switch_time_optimize1.csv");
    }

    System.out.println("\n\n");
  }
}