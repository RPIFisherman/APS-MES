// 6/25/2024 Yuyang Gong
// Herustic algorithm for APS demo
// compile and run: g++ -std=c++11 APS-demo.cpp -o APS-demo && ./APS-demo

#include <stdlib.h>
#include <iostream>
#include <iomanip>
#include <vector>
#include <algorithm>
#include <queue>
#include <climits>
// #include <map>
#include <string>

using namespace std;

#define RAND_SEED 1337

#define PRODUCT_NUM 10000

#define MACHINE_NUM 20
#define MACHINE_PRODUCT_PER_HOUR 10

// sales order
// class sales_order {};//TODO AP problem

// TODO inherit order into sales order and production order
//  production order
class order
{
public:
  int order_id;
  string name;
  int quantity;
  int due_date;
  int priority;
  int earlest_start_date;
  order(int id, string n, int q,
        int d, int p, int e) : order_id(id), name(n), quantity(q),
                               due_date(d), priority(p), earlest_start_date(e) {}
};

// compare function 1
// 1. priority is higher
// 2. priority same, due_date closer
bool compare_due(const order &o1, const order &o2)
{
  return o1.priority > o2.priority || (o1.priority == o2.priority && o1.due_date < o2.due_date);
}
// compare function 2
// 1. earlest_start_date closer
// 2. earlest_start_date same, priority is higher
// 3. priority same, earlest_start_date same, due_date closer
bool compare_start(const order &o1, const order &o2)
{
  return o1.earlest_start_date < o2.earlest_start_date ||
         (o1.earlest_start_date == o2.earlest_start_date && o1.priority > o2.priority) ||
         (o1.earlest_start_date == o2.earlest_start_date && o1.priority == o2.priority && o1.due_date < o2.due_date);
}

vector<order> generate_random_order(int size = PRODUCT_NUM)
{
  srand(RAND_SEED);
  vector<order> orders;
  for (int i = 0; i < size; i++)
  {
    int due_date = rand() % 100;
    int earlest_start_date = rand() % 100;
    while (earlest_start_date > due_date)
    {
      earlest_start_date = rand() % 100;
    }
    order o(i, "order" + (i < 10 ? "0" + to_string(i) : to_string(i)), rand() % 10000, due_date, rand() % 10, earlest_start_date);
    orders.push_back(o);
  }
  return orders;
}

// swap time between two products
// unit in hours
vector<vector<int>> generate_random_jump_matrix(int size = PRODUCT_NUM)
{
  srand(RAND_SEED);
  vector<vector<int>> jump_matrix;
  for (int i = 0; i < size; i++)
  {
    vector<int> row;
    for (int j = 0; j < size; j++)
    {
      row.push_back(rand() % 5);
    }
    jump_matrix.push_back(row);
  }
  return jump_matrix;
}

// machines
class machine
{
public:
  int machine_id;
  string name;
  int finishing_time;
  int machine_product_per_hour;
  vector<order *> orders_in_queue;
  machine(int id, string n, int c = 0, int mph = MACHINE_PRODUCT_PER_HOUR) : machine_id(id), name(n), finishing_time(c), machine_product_per_hour(mph) {}
  machine(const machine &m) : machine_id(m.machine_id), name(m.name), finishing_time(m.finishing_time), machine_product_per_hour(m.machine_product_per_hour) {}
};

vector<machine> generate_random_machine(int size = MACHINE_NUM)
{
  srand(RAND_SEED);
  vector<machine> machines;
  for (int i = 0; i < size; i++)
  {
    machine m(i, "machine" + (i < 10 ? "0" + to_string(i) : to_string(i)));
    machines.push_back(m);
  }
  return machines;
}

// find the best machine to put the order
class compare_machine_by_free_time
{
public:
  bool operator()(const machine &m1, const machine &m2)
  {
    return m1.finishing_time > m2.finishing_time ||
           (m1.finishing_time == m2.finishing_time && m1.machine_id > m2.machine_id);
  }
};

void printOrders(const vector<order> &orders)
{
  for (auto o : orders)
  {
    cout << "Order: " << o.name
         << " Quantity: " << fixed << setw(2) << o.quantity
         << " Due Date: " << fixed << setw(2) << o.due_date
         << " Priority: " << fixed << setw(2) << o.priority
         << " Earlest Start Date: " << fixed << setw(2) << o.earlest_start_date << endl;
  }
}

void printMachines(const vector<machine> &machines)
{
  for (auto m : machines)
  {
    cout << "Machine: " << m.name
         << " Finishing Time: " << fixed << setw(2) << setfill('0') << m.finishing_time
         << " Machine Product Per Hour: " << fixed << setw(2) << setfill('0') << m.machine_product_per_hour
         << " Orders in Queue: \n \t";
    for (auto o : m.orders_in_queue)
    {
      cout << o->name << " ";
    }
    cout << endl;
  }
}

int main()
{
  vector<order> orders = generate_random_order();
  // sort orders by priority and due_date
  sort(orders.begin(), orders.end(), compare_due);
  // std::sort(orders.begin(), orders.end(), compare_start);
  auto jump_matrix = generate_random_jump_matrix();
  auto machines = generate_random_machine();
  priority_queue<machine, vector<machine>, compare_machine_by_free_time> machine_queue;
  for (auto m : machines)
  {
    machine_queue.push(m);
  }

  for (auto o : orders)
  {
    // find the best machine to put the order
    auto best_machine = machine_queue.top();
    machine_queue.pop();
    // calculate the finishing time
    int finishing_time = best_machine.finishing_time +
                         o.quantity / best_machine.machine_product_per_hour +
                         jump_matrix[o.order_id][best_machine.machine_id];
    // update the finishing time
    best_machine.finishing_time = finishing_time;
    // update the order queue
    best_machine.orders_in_queue.push_back(&o);
    // push the machine back to the queue
    machine_queue.push(best_machine);
  }

  // print the orders
  // printOrders(orders);
  // print the machines
  while (!machine_queue.empty())
  {
    auto m = machine_queue.top();
    machine_queue.pop();
    cout << "Machine: " << m.name << " Finishing Time: " << m.finishing_time << endl;
  }
  return 0;
}
