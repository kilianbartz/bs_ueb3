import json
from transaction import Transaction
import random
import multiprocessing
import time
from tqdm import tqdm

# AI generated text as filler
text = """
Software Transactional Memory (STM) and other optimistic concurrency control mechanisms are designed to handle concurrent access to shared resources in a way that minimizes contention and maximizes performance. These approaches rely on the assumption that conflicts between concurrent operations are rare, and they aim to resolve conflicts only when they occur, rather than preventing them preemptively. Below is a detailed discussion of the pros and cons of STM and optimistic concurrency control.
Pros of Software Transactional Memory (STM)

    Simplified Programming Model:
        STM abstracts away the complexity of managing locks, making it easier for developers to write concurrent programs. Code regions marked as transactions are automatically managed for atomicity, consistency, and isolation
        1
        2
        .

    Deadlock Prevention:
        Since STM avoids explicit locking, it eliminates the possibility of deadlocks, which are common in lock-based synchronization systems
        1
        3
        .

    Improved Modularity:
        STM allows developers to compose concurrent operations more easily, as transactions can be nested or combined without worrying about lock hierarchies
        1
        3
        .

    Optimistic Execution:
        STM allows threads to execute concurrently without blocking, which can lead to higher throughput in scenarios where conflicts are infrequent
        2
        4
        .

    Automatic Conflict Resolution:
        STM systems automatically detect and resolve conflicts by rolling back and retrying transactions when necessary, reducing the burden on developers to handle these cases manually
        2
        5
        .

    Scalability:
        STM can scale well in systems with low contention, as it avoids the overhead of locking mechanisms that can become bottlenecks in highly concurrent environments
        2
        6
        .

Cons of Software Transactional Memory (STM)

    Performance Overhead:
        STM introduces significant overhead due to the need to track memory accesses, detect conflicts, and manage rollbacks. This can result in worse performance compared to lock-based systems, especially under high contention
        7
        8
        .

    Conflict Resolution Costs:
        When conflicts occur, STM must roll back and retry transactions, which can be expensive in terms of time and computational resources. This can degrade performance in workloads with frequent conflicts
        7
        5
        .

    Limited Use Cases:
        STM is most effective in scenarios where conflicts are rare. In high-contention environments, its performance can degrade significantly, making it less suitable for such use cases
        1
        8
        .

    Complexity in Debugging:
        Debugging STM-based systems can be challenging because the transactional model abstracts away the underlying memory operations, making it harder to trace and understand issues
        3
        9
        .

    Lack of Determinism:
        STM systems may exhibit non-deterministic behavior due to the rollback and retry mechanism, which can complicate testing and debugging
        3
        9
        .

    Integration Challenges:
        STM may not integrate well with legacy systems or libraries that rely on explicit locking, as these approaches are fundamentally different
        1
        3
        .

Pros of Optimistic Concurrency Control (OCC)

    High Concurrency:
        OCC allows multiple transactions to proceed without blocking, which can lead to better system responsiveness and throughput in low-conflict scenarios
        10
        4
        .

    Reduced Contention:
        By avoiding locks, OCC minimizes contention for shared resources, making it suitable for distributed systems and applications with low write-write conflicts
        4
        6
        .

    Simpler Lock-Free Design:
        OCC eliminates the need for complex lock management, reducing the risk of deadlocks and lock contention
        11
        12
        .

    Better Performance in Low-Conflict Scenarios:
        OCC is particularly effective when conflicts are infrequent, as it avoids the overhead of locking and blocking
        6
        11
        .

Cons of Optimistic Concurrency Control (OCC)

    Conflict Resolution Overhead:
        OCC relies on detecting conflicts at commit time and rolling back transactions when conflicts occur. This can lead to significant overhead in high-conflict scenarios
        5
        8
        .

    Increased Latency:
        Transactions that are rolled back and retried can increase the overall latency of the system, especially when conflicts are frequent
        6
        8
        .

    Complex Error Handling:
        Developers must handle transaction retries and ensure that the system remains consistent even after multiple rollbacks, increasing the complexity of error handling
        9
        5
        .

    Unsuitability for High-Contention Workloads:
        OCC performs poorly in environments with frequent conflicts, as the cost of rolling back and retrying transactions outweighs the benefits of optimistic execution
        8
        .

    Resource Wastage:
        Transactions that are rolled back represent wasted computational effort, as all the work done during the transaction must be discarded
        6
        8
        .

Comparison with Lock-Based Synchronization

While STM and OCC offer significant advantages in terms of simplicity and scalability, they are not universally better than traditional lock-based synchronization. Lock-based systems can outperform STM and OCC in high-contention scenarios, as they prevent conflicts preemptively rather than resolving them after they occur. However, lock-based systems come with their own challenges, such as deadlocks, priority inversion, and reduced modularity.
Conclusion

Software Transactional Memory and optimistic concurrency control mechanisms provide powerful tools for managing concurrency in modern systems. They excel in scenarios with low contention and high concurrency requirements, offering benefits such as simplified programming, deadlock prevention, and scalability. However, their performance can degrade in high-contention environments due to the overhead of conflict detection and resolution. Choosing the right concurrency control mechanism depends on the specific requirements and workload characteristics of the system being developed.
"""

NUMBER_ITERATIONS = 1_000


def random_text():
    start = random.randint(0, len(text))
    return text[start : random.randint(start + 1, max(len(text), start + 100))]


files = ["file" + str(i) for i in range(10)]


def workload_single_writes(iterations):
    for _ in tqdm(range(iterations)):
        # start a transaction
        transaction = Transaction()
        print(transaction.start())

        # construct entry
        name = random.choice(files)
        text = random_text()
        print(transaction.write(name, text))
        print(transaction.commit())


def workload_two_writes(iterations):
    for _ in tqdm(range(iterations)):
        # start a transaction
        transaction = Transaction()
        transaction.start()

        # construct entry
        name = random.choice(files)
        text = random_text()
        transaction.write(name, text)
        name = random.choice(files)
        text = random_text()
        transaction.write(name, text)
        transaction.commit()


def workload_single_read_write(iterations):
    for _ in tqdm(range(iterations)):
        # read a random file
        transaction = Transaction()
        if random.random() > 0.5:
            transaction.start()
            name = random.choice(files)
            transaction.read(name)
            transaction.commit()
        else:
            transaction.start()
            name = random.choice(files)
            text = random_text()
            transaction.write(name, text)
            transaction.commit()


def main():
    #  compare how low workload_single_writes() takes with one process, two processes, and four processes
    print("Single writes")
    start = time.time()
    workload_single_writes(NUMBER_ITERATIONS)
    print("Single process:", time.time() - start)

    start = time.time()
    p1 = multiprocessing.Process(
        target=workload_single_writes, args=(NUMBER_ITERATIONS // 2,)
    )
    p2 = multiprocessing.Process(
        target=workload_single_writes, args=(NUMBER_ITERATIONS // 2,)
    )
    p1.start()
    p2.start()
    p1.join()
    p2.join()
    print("Two processes:", time.time() - start)


if __name__ == "__main__":
    main()
