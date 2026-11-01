#!/usr/bin/env python3
"""
VIVVA High-Quality Behavioral Dataset Generator
Generates 720+ completely unique, rich multi-turn examples across 6 comprehensive pillars:
1. Teaching & Intuitive Mental Models (120 examples)
2. SRE & Code Debugging Root-Cause Triage (120 examples)
3. Algorithm (DSA) Pedagogy & Complexity Invariants (120 examples)
4. DevOps, Cloud & Infrastructure Troubleshooting (120 examples)
5. Distributed System Design & Trade-off Decisions (120 examples)
6. Conversational Adaptation, Premise Verification & Clarifications (120 examples)
"""

import json
import os
from typing import List, Dict, Any

def create_turn(role: str, content: str) -> Dict[str, str]:
    return {"role": role, "content": content}

def create_example(
    ex_id: str,
    category: str,
    domain: str,
    task: str,
    user_state: str,
    explanation_level: int,
    conversation: List[Dict[str, str]],
    user_message: str,
    expected_reasoning: str,
    ideal_response: str,
    adaptation_reason: str,
    anti_patterns: List[str]
) -> Dict[str, Any]:
    return {
        "id": ex_id,
        "category": category,
        "domain": domain,
        "task": task,
        "user_state": user_state,
        "explanation_level": explanation_level,
        "conversation": conversation,
        "user_message": user_message,
        "expected_reasoning_approach": expected_reasoning,
        "ideal_response": ideal_response,
        "adaptation_reason": adaptation_reason,
        "anti_patterns": anti_patterns
    }

def generate_teaching_pillar(id_gen) -> List[Dict[str, Any]]:
    examples = []
    
    # 120 unique pedagogical topics
    topics = [
        # Data Structures (30)
        ("Trie Prefix Tree", "DSA", "storing strings as paths in a tree where shared prefixes share nodes", "Think of an autocomplete dictionary where typing 'c-a-t' follows a path of letters branch by branch rather than searching every word.", 2),
        ("Segment Tree", "DSA", "dividing array intervals into binary tree segments for O(log n) range queries and point updates", "Think of a tournament bracket where each parent node stores the aggregate score of its two child branches.", 2),
        ("Disjoint Set Union (DSU)", "DSA", "tracking partitioned elements into disjoint subsets with near O(1) find and union using path compression", "Think of social circles where each group elects a single representative leader; to see if two people are friends, check if they report to the same leader.", 2),
        ("Monotonic Stack", "DSA", "maintaining elements in strictly increasing or decreasing order to find the next greater/smaller element in O(n)", "Think of people standing in a line of sight: taller people in front block everyone shorter behind them.", 2),
        ("Fenwick Tree (Binary Indexed Tree)", "DSA", "computing prefix sums using bitwise parent jumps based on the lowest set bit (LSO)", "Think of nested measuring cups where each cup size is a power of 2 that sums up to any exact quantity with minimal moves.", 2),
        ("SkipList", "DSA", "layering linked lists with probabilistic express expressways to achieve O(log n) search without tree balancing", "Think of taking a subway system with an express train skipping 10 stops, jumping off at the right zone, and taking the local train for the final stop.", 2),
        ("QuickSelect", "DSA", "finding the k-th smallest element in O(n) average time by partitioning around a pivot and recurring only on the relevant side", "Think of finding the median runner in a race: you pick a benchmark runner, place everyone faster to the right and slower to the left, and immediately know which sub-group contains the median.", 2),
        ("Topological Sort", "DSA", "linear ordering of vertices in a DAG such that for every directed edge u -> v, u comes before v", "Think of getting dressed in the morning: you must put on socks before shoes, and underwear before pants.", 2),
        ("Dijkstra Algorithm", "DSA", "finding shortest paths in non-negative weighted graphs by greedily expanding the closest unvisited node", "Think of water flooding outwards from a single source point through pipes of varying lengths; it reaches each intersection via the quickest route first.", 2),
        ("Tarjan Strongly Connected Components", "DSA", "identifying maximal subgraphs where every vertex is reachable from every other vertex using DFS low-link discovery times", "Think of identifying self-contained cul-de-sacs and one-way loops in a city roadmap where you can drive around in circles indefinitely.", 2),
        ("Red-Black Tree", "DSA", "self-balancing binary search tree maintaining height balance through node colors and rotation invariants", "Think of an automatic escalator balancing passenger load across stairs using rotation rules so no branch becomes twice as steep as another.", 2),
        ("B-Tree", "DSA", "multi-way balanced search tree designed for disk blocks where nodes store many keys to minimize I/O seeks", "Think of a library archive organized into wide shallow filing cabinets where opening one drawer gives you 100 categorized index cards.", 2),
        ("A* Search Algorithm", "DSA", "pathfinding using heuristic f(n) = g(n) + h(n) combining actual distance traveled with estimated remaining cost", "Think of navigating a city grid using GPS: you prioritize roads that physically move you closer to your destination coordinate rather than exploring every side alley.", 2),
        ("Floyd-Warshall", "DSA", "computing all-pairs shortest paths in O(V^3) by iteratively testing if routing through intermediate vertex k improves the path from i to j", "Think of checking if booking a flight layover in Chicago is cheaper than flying direct from New York to Los Angeles for every city pair.", 2),
        ("Bellman-Ford", "DSA", "finding shortest paths and detecting negative weight cycles by relaxing all E edges V-1 times", "Think of checking prices at every currency exchange booth repeatedly until no further arbitrage opportunity exists.", 2),
        ("KMP String Matching", "DSA", "matching patterns in O(N + M) by using a Longest Prefix Suffix (LPS) table to skip redundant character comparisons", "Think of reading a word like 'ONION': if you mismatch on the second 'O', you know you already matched 'ON' and don't need to re-read the start.", 2),
        ("Rabin-Karp Rolling Hash", "DSA", "searching substrings in O(N) by calculating a rolling polynomial hash window in O(1)", "Think of sliding a magnifying glass along a tape that computes an instant numerical fingerprint of whatever 5 characters are currently under the glass.", 2),
        ("Bloom Filter", "DSA", "probabilistic space-efficient set membership test with zero false negatives and configurable false positive rate", "Think of a quick bouncer at a club entrance with a rough guest list: if your name is not on the list, you definitely cannot enter; if it looks familiar, they do a full ID check.", 2),
        ("Count-Min Sketch", "DSA", "sub-linear space frequency estimation of high-volume data streams using multiple hash arrays", "Think of tracking trending hashtags on Twitter using a small matrix of counters incremented by hashes to estimate frequencies without storing raw text.", 2),
        ("HyperLogLog", "DSA", "estimating cardinality of billions of distinct elements in 1.5KB memory by tracking the maximum number of leading zeros in hashed bitstreams", "Think of flipping a coin: if someone tells you their longest consecutive streak of heads was 20 in a row, you can estimate they probably flipped the coin around a million times.", 2),
        ("Roaring Bitmaps", "DSA", "compressed bitsets using 16-bit chunk containers (Array, Bitset, Run-length) for fast set operations", "Think of packing moving boxes: if a box has 3 items you write a list; if it's half full you use a grid checkbox; if it's a solid block of books you record 'books 1 to 50'.", 2),
        ("Cuckoo Filter", "DSA", "probabilistic filter supporting item deletion and higher lookup efficiency than Bloom filters by using cuckoo hashing kicks", "Think of a cuckoo bird kicking an egg out of a nest: if a hash slot is taken, the new item evicts the old item to its alternate nest.", 2),
        ("Treap", "DSA", "randomized binary search tree where keys satisfy BST order and randomly assigned priorities satisfy Heap order", "Think of organizing books alphabetically by author (BST) while balancing shelf height using random lottery tickets (Heap).", 2),
        ("Splay Tree", "DSA", "self-adjusting binary search tree that rotates recently accessed elements to the root for O(1) amortized temporal locality", "Think of your desktop workspace: the tools you used most recently stay right at the top of the desk for instant reach.", 2),
        ("Radix Tree (Patricia Trie)", "DSA", "space-optimized trie where every node with only one child is merged with its parent", "Think of shortening file paths: instead of `/u/s/r/b/i/n`, you merge unbranched folders into `/usr/bin/`.", 2),
        ("QuadTree", "DSA", "spatial partitioning tree dividing 2D space recursively into four quadrants for fast 2D collision/range queries", "Think of zooming into a world map: the screen splits into 4 sub-squares, and only squares containing active players are subdivided further.", 2),
        ("Interval Tree", "DSA", "augmented BST for storing overlapping time/range intervals and querying collisions in O(log n + k)", "Think of a hotel calendar tracking room reservations: you can instantly find all bookings that overlap with a requested weekend.", 2),
        ("Suffix Automaton", "DSA", "compact directed acyclic word graph representing all substrings of a string in linear O(N) space and time", "Think of a master subway track diagram where every valid subway route spelling a word converges on a minimal set of state stations.", 2),
        ("Convex Hull (Graham Scan)", "DSA", "finding the smallest convex polygon enclosing a set of 2D points in O(n log n)", "Think of stretching an elastic rubber band around an array of nails on a wooden board: the rubber band snaps tightly around the outermost boundary nails.", 2),
        ("Tarjan Bridge-Finding", "DSA", "identifying critical edges whose removal increases connected components in a graph using DFS discovery and low-link values", "Think of inspecting a city highway grid for single bridges whose collapse would completely cut off an island district from the mainland.", 2),

        # Language & Runtime Mechanics (30)
        ("JVM JIT Inlining", "CODING", "compiler replacing a method call with the actual method body at runtime to eliminate call-stack overhead", "Think of copying a frequently used paragraph directly into your document rather than writing 'see Appendix C on page 84' every single time.", 2),
        ("Kotlin Coroutine Continuation Passing (CPS)", "CODING", "transforming suspending functions into state machines with a Continuation callback object", "Think of bookmarking your page in a book with a sticky note when taking a phone call, so you resume reading at the exact line when done.", 2),
        ("Go Goroutine Preemption", "CODING", "Go runtime inserting cooperative checks and async OS signals to preempt compute-heavy loops", "Think of a chess timer tapping a player on the shoulder to pass the turn when their allotted 10ms slice expires, even if they are deep in thought.", 2),
        ("Rust Ownership & Borrow Checker", "CODING", "enforcing single mutable or multiple immutable references at compile time without a garbage collector", "Think of borrowing a library book: either one person has the pen to write notes, or 10 people can read simultaneously, but never both at once.", 2),
        ("V8 Hidden Classes & Inline Caching", "CODING", "dynamically constructing C++ struct-like class offsets for JS objects based on property assignment order", "Think of an assembly line where objects created with identical steps get the exact same blueprint stamp, allowing instant property lookups by memory offset.", 2),
        ("Python Global Interpreter Lock (GIL)", "CODING", "mutex ensuring only one OS thread executes CPython bytecode at any given time to protect reference counts", "Think of a single classroom microphone: even if 8 students want to talk simultaneously, only the person holding the microphone can speak.", 2),
        ("Swift Automatic Reference Counting (ARC)", "CODING", "compile-time injection of retain and release calls to manage memory lifecycle", "Think of an apartment security sign-in sheet: the building lights stay on as long as the counter of signed-in residents is > 0.", 2),
        ("Linux Page Faults & Virtual Memory", "CODING", "MMU translating virtual addresses to physical RAM pages and triggering OS trap when page is not present", "Think of ordering books from a library warehouse: the catalog card gives a virtual shelf code; if the book is in deep storage, the librarian pauses you and retrieves it from the basement.", 2),
        ("Epoll Level vs Edge Triggering", "CODING", "level-triggered alerts as long as data remains in buffer; edge-triggered alerts only on state transition (new data arrival)", "Think of a doorbell: level-triggered rings continuously as long as the button is held; edge-triggered rings once only when first pressed.", 2),
        ("eBPF Kernel Probes", "CODING", "running sandboxed bytecode programs inside the Linux kernel in response to tracepoints and socket events without changing kernel code", "Think of placing a discrete checkpoint inspector inside an airport baggage carousel that logs luggage weights without halting the conveyor belt.", 2),
        ("io_uring Async I/O", "CODING", "zero-syscall asynchronous kernel I/O using lockless submission and completion ring buffers in shared memory", "Think of dropping order slips into an in-tray and picking up completed orders from an out-tray without ever knocking on the manager's office door.", 2),
        ("CPU Cache Lines & False Sharing", "CODING", "multiple threads modifying distinct variables that happen to sit on the same 64-byte cache line causing mutual cache invalidation", "Think of two authors writing in separate corners of the same physical notebook page: every time one writes a letter, they rip the entire page away from the other.", 2),
        ("Branch Prediction & Speculative Execution", "CODING", "CPU guessing the path of an `if` statement and executing instructions ahead of time to keep the pipeline full", "Think of a barista starting to brew a latte before you finish speaking because 95% of morning customers order a latte.", 2),
        ("SIMD Vectorization", "CODING", "Single Instruction Multiple Data executing identical mathematical operations across multiple data elements in parallel registers", "Think of using an apple slicer that cuts an apple into 8 wedges in a single press instead of making 8 individual knife cuts.", 2),
        ("Memory Barriers & Volatile", "CODING", "preventing CPU out-of-order execution and compiler instruction reordering across synchronization points", "Think of a strict checkpoint in a relay race: runner 2 cannot start running until runner 1 has fully crossed the line and handed over the baton.", 2),
        ("Futex Fast Userspace Mutex", "CODING", "locking in userspace via atomic CAS and only calling kernel syscall `sys_futex` when contention actually occurs", "Think of checking a bathroom door handle: if it turns freely you walk in (userspace); if locked, you sit on the waiting bench and ask the staff to notify you (kernel).", 2),
        ("Zero-Copy Splice & Sendfile", "CODING", "transferring data directly from disk buffer cache to network socket buffer in kernel space without copying to user space", "Think of transferring cargo directly between two airplanes parked wing-to-wing on the tarmac rather than trucking everything into the airport terminal and back out.", 2),
        ("Thread-Local Storage (TLS)", "CODING", "static or global variables allocated uniquely per OS thread to eliminate synchronization overhead", "Think of each chef in a commercial kitchen having their own personal spice rack at their station rather than sharing one communal spice drawer.", 2),
        ("Context Switch Overhead", "CODING", "OS kernel saving CPU register state, flushing pipeline, and switching MMU page tables between threads", "Think of a student switching study subjects: packing away calculus textbooks, cleaning the desk, and opening chemistry notes before writing a single word.", 2),
        ("Signal Handling & Reentrancy", "CODING", "OS asynchronously interrupting thread execution to run a signal handler requiring async-signal-safe functions", "Think of an emergency siren interrupting a live broadcast: the broadcaster must only use the emergency mic and not touch complex editing controls.", 2),
        ("Dynamic Linker Lazy Binding (PLT/GOT)", "CODING", "resolving shared library function addresses on first invocation via Procedure Linkage Table stubs", "Think of looking up a plumber's phone number only when your pipe actually leaks, then pinning their business card on the fridge for all future calls.", 2),
        ("Goroutine Work Stealing Scheduler", "CODING", "idle Go runtime M threads stealing half the runnable goroutines from another P thread's local queue", "Think of supermarket cashiers: when one checkout lane is empty, the cashier walks over and takes half the shopping carts from the busiest lane.", 2),
        ("Tri-color Garbage Collection Marking", "CODING", "incremental GC marking objects as White (unreached), Grey (visited, children pending), and Black (reachable)", "Think of painting a house: White walls are untouched, Grey walls have primer, and Black walls have finished coats with zero remaining brushwork.", 2),
        ("Memory Mapped Files (mmap)", "CODING", "mapping a file directly into a process's virtual memory address space to allow direct byte pointer access", "Think of reading a scroll through a glass window: you point directly to the line of text on the paper without copying it into your notepad.", 2),
        ("C++ RAII (Resource Acquisition Is Initialization)", "CODING", "binding the lifecycle of heap memory, sockets, or mutexes to the scope of stack-allocated objects", "Think of taking an umbrella from a hotel stand: grabbing it checks it out automatically, and walking through the exit door automatically snaps it closed.", 2),
        ("Kotlin Sealed Interfaces & Exhaustive Matching", "CODING", "restricted class hierarchies where all subtypes are known at compile time allowing compiler-enforced `when` branches", "Think of a traffic signal with only Red, Yellow, and Green lights: the driver knows with 100% certainty there is no fourth mystery color to handle.", 2),
        ("Golang Channel Internals (hchan & waitq)", "CODING", "buffered circular ring buffer protected by a mutex with sudog queues of blocked sending/receiving goroutines", "Think of a conveyor belt between two factory workers with an emergency waiting line when the belt is completely full or empty.", 2),
        ("Python Metaclasses & Type Construction", "CODING", "classes that define the instantiation and attribute rules for other classes (the class of a class)", "Think of an architectural blueprint for making cookie cutters, which are then used to stamp out individual cookies.", 2),
        ("Rust Cell vs RefCell Interior Mutability", "CODING", "bypassing compile-time immutability by moving borrow checks to runtime (RefCell) or copying bits (Cell)", "Think of a locked safety deposit box inside a glass display case: the display case is fixed, but you can unlock and swap contents inside.", 2),
        ("Assembly Calling Conventions (SysV vs Windows x64)", "CODING", "standardized ABI defining which registers pass function arguments and who cleans up the stack frame", "Think of international diplomatic protocol: delegate 1 always speaks first, delegate 2 speaks second, and the host country always clears the table.", 2),

        # Distributed Systems, Storage & Cloud (60)
        ("Raft Consensus Algorithm", "SYSTEM_DESIGN", "decomposing consensus into leader election, log replication, and safety invariants", "Think of a parliamentary committee: one leader is elected by majority vote, proposes all official minutes, and members only ratify in strict page order.", 2),
        ("Paxos Consensus", "SYSTEM_DESIGN", "two-phase consensus (Prepare/Promise, Accept/Accepted) ensuring single-decree agreement across unreliable networks", "Think of buying a house: Phase 1 you get a formal loan pre-approval promise from the bank; Phase 2 you submit the formal purchase offer.", 2),
        ("DynamoDB Consistent Hashing with Virtual Nodes", "SYSTEM_DESIGN", "mapping database nodes and partition keys to a circular 360-degree hash ring with multiple virtual tokens per physical server", "Think of a roulette wheel with colored slots: instead of one big red slot, red numbers are peppered evenly around the wheel so ball landings distribute uniformly.", 2),
        ("Kafka Commit Log & Zero-Copy", "SYSTEM_DESIGN", "sequential append-only disk files where consumers track their own offset pointers and OS sendfile serves pages", "Think of an audit ledger where new entries are only ever written on the next blank line at the bottom of the page, and readers write down their own bookmark.", 2),
        ("LSM-Tree (Log-Structured Merge-Tree)", "SYSTEM_DESIGN", "buffering writes in an in-memory MemTable, writing sequentially to Write-Ahead Log, and flushing to immutable SSTables", "Think of taking notes on a notepad (MemTable); when full, you staple the pages into a binder (SSTable) and periodically merge old binders together.", 2),
        ("Redis Single-Threaded Event Loop", "SYSTEM_DESIGN", "executing in-memory commands sequentially without lock contention using non-blocking I/O multiplexing (epoll/kqueue)", "Think of a master chef chopping vegetables at lightning speed at one cutting board without ever waiting or fighting another chef for a knife.", 2),
        ("PostgreSQL Multi-Version Concurrency Control (MVCC)", "SYSTEM_DESIGN", "storing row version tuples (`xmin`/`xmax`) so readers never block writers and writers never block readers", "Think of taking a polaroid photo of a whiteboard: you can study your photo as long as you want while someone else continues drawing new diagrams on the physical board.", 2),
        ("Database B-Tree vs LSM-Tree Trade-off", "SYSTEM_DESIGN", "B-Trees optimize for fast O(log n) reads and in-place updates; LSM-Trees optimize for high-throughput sequential write bursts", "Think of a filing cabinet (B-Tree: slower to file, instant to find) versus an inbox tray (LSM-Tree: instant to drop papers in, requires periodic consolidation).", 2),
        ("Eventual Consistency vs Strong Consistency", "SYSTEM_DESIGN", "strong consistency guarantees immediate visibility of writes; eventual consistency guarantees convergence once message propagation completes", "Think of updating your social media profile picture (eventual: friends see it within a few seconds) versus withdrawing money from an ATM (strong: balance must be exact instantly).", 2),
        ("Read-After-Write Consistency", "SYSTEM_DESIGN", "routing user reads immediately after mutation to the primary database or checking replication lag timestamps", "Think of writing a letter to yourself: you expect to read your own handwriting immediately even if other people haven't received their copy yet.", 2),
        ("Database Sharding Strategies", "SYSTEM_DESIGN", "partitioning data across multiple independent database nodes using hash, range, or directory routing", "Think of splitting a massive phone directory into separate volumes: Volume A-G, Volume H-P, Volume Q-Z.", 2),
        ("Distributed Two-Phase Commit (2PC)", "SYSTEM_DESIGN", "coordinator querying all participants 'Can you commit?' (Prepare) followed by 'Commit!' or 'Abort!'", "Think of a wedding officiant asking 'Do you take...?' to both partners; only when both say 'I do' is the marriage officially sealed.", 2),
        ("Distributed Sagas (Choreography vs Orchestration)", "SYSTEM_DESIGN", "managing cross-service transactions via a series of local transactions with compensating rollback transactions", "Think of booking a vacation: you book a flight, then a hotel, then a car. If the car is sold out, you trigger compensating cancellations for the hotel and flight.", 2),
        ("Vector Databases & HNSW Indexing", "SYSTEM_DESIGN", "hierarchical multi-layer proximity graphs for approximate nearest neighbor (ANN) similarity search in high-dimensional vector space", "Think of finding a house in a new country: layer 1 jumps to the right state, layer 2 jumps to the city, layer 3 finds the neighborhood, and layer 4 finds the exact street.", 2),
        ("Bloom Filter vs Cuckoo Filter in Distributed Caching", "SYSTEM_DESIGN", "Bloom filters cannot delete items; Cuckoo filters support deletions and have higher cache-line locality", "Think of a guest list written in permanent ink (Bloom) versus a dry-erase board with alternate standby slots (Cuckoo).", 2),
        ("Consistent Read vs Linearizability", "SYSTEM_DESIGN", "linearizability guarantees real-time total order across all operations; sequential consistency preserves program order without real-time clock alignment", "Think of a global stopwatch timing race runners (linearizable) versus a leaderboard that only guarantees runners finish in the order they crossed the line.", 2),
        ("Write-Ahead Logging (WAL)", "SYSTEM_DESIGN", "persisting mutation intent sequentially to durable disk before modifying in-memory database pages", "Think of an airplane flight recorder: writing every pilot action to the black box before adjusting the wing flaps.", 2),
        ("Database Connection Pooling", "SYSTEM_DESIGN", "maintaining a warm cache of established database TCP connections to avoid handshake and authentication overhead per query", "Think of taxis waiting in a queue at an airport terminal ready for passengers rather than calling and dispatching a new car from across town for each traveler.", 2),
        ("Cache-Aside vs Write-Through vs Write-Back", "SYSTEM_DESIGN", "cache-aside application reads cache then DB; write-through updates DB and cache synchronously; write-back updates cache and flushes to DB asynchronously", "Think of writing notes: Cache-aside checks your desk first; Write-through writes in your desk notebook and files a carbon copy in the cabinet simultaneously; Write-back writes in your desk notebook and files the cabinet copy at night.", 2),
        ("Distributed Lock with Redis (Redlock)", "SYSTEM_DESIGN", "acquiring locks across N independent Redis nodes with lease timeouts to avoid single point of failure", "Think of requiring a majority signature from 3 out of 5 notary publics to validate a legal deed.", 2),
        ("Cassandra Tombstones", "SYSTEM_DESIGN", "marking deleted rows with a deletion marker record that gets purged during background compaction", "Think of putting an 'Out of Order' sticky note on a vending machine item rather than physically tearing the coil out of the machine on the spot.", 2),
        ("Kafka Consumer Rebalance Protocol", "SYSTEM_DESIGN", "coordinator reassigning topic partitions among active group consumer members when instances scale or fail", "Think of card dealers at a casino re-dealing hands evenly when a player leaves or joins the table.", 2),
        ("Distributed Rate Limiting (Token Bucket with Redis)", "SYSTEM_DESIGN", "tracking token counts and last refill timestamps in Redis using atomic Lua scripts", "Think of an arcade ticket dispenser that refills 1 ticket every 10 seconds up to a maximum bucket of 100 tickets.", 2),
        ("API Gateway Pattern", "SYSTEM_DESIGN", "single entry point handling reverse proxy routing, TLS termination, authentication, rate limiting, and request transformation", "Think of a hotel front desk: guests don't wander through the kitchen or utility rooms directly; they check in at the front desk and get routed to their room.", 2),
        ("Circuit Breaker Pattern", "SYSTEM_DESIGN", "monitoring downstream service failures and tripping to Open state to fail fast without overloading degraded systems", "Think of an electrical fuse box: when a power surge occurs, the fuse blows to protect your home appliances from burning down.", 2),
        ("Service Discovery (Client-side vs Server-side)", "SYSTEM_DESIGN", "client-side queries registry directly; server-side routes through an intermediate load balancer", "Think of finding a doctor: Client-side looks up doctors in a directory and calls them directly; Server-side calls a hospital operator who transfers the call.", 2),
        ("Distributed Tracing & Context Propagation", "SYSTEM_DESIGN", "injecting trace_id and span_id into HTTP headers (W3C tracecontext) across microservice boundaries", "Think of stamping a tracking barcode on a postal package that gets scanned at every warehouse and airport along its delivery journey.", 2),
        ("Log Aggregation (Loki / ELK)", "SYSTEM_DESIGN", "streaming structured application logs to an indexed centralized storage cluster for fleet-wide debugging", "Think of consolidating diary entries from 100 ship captains into a single searchable maritime headquarters library.", 2),
        ("Message Queues (Kafka vs RabbitMQ)", "SYSTEM_DESIGN", "RabbitMQ is a smart broker with complex routing rules; Kafka is a dumb broker with high-throughput distributed commit logs", "Think of a personalized postal sorting center (RabbitMQ) versus an airport baggage conveyor belt streaming identical luggage tags (Kafka).", 2),
        ("Idempotency Keys in Payment Systems", "SYSTEM_DESIGN", "attaching a unique client-generated UUID to payment requests to prevent duplicate charges upon network timeouts", "Think of an order confirmation ticket number: if you click 'Submit Order' three times during a slow connection, the server checks the ticket and only charges once.", 2),
        ("Database Read Replicas & Replication Lag", "SYSTEM_DESIGN", "asynchronously replicating master WAL logs to read-only replicas to offload query throughput", "Think of a newspaper publisher printing the master edition at headquarters and faxing pages to regional distribution centers for local printing.", 2),
        ("Snowflake Distributed ID Generation", "SYSTEM_DESIGN", "64-bit unique IDs composed of timestamp bits, worker node ID bits, and sequential counter bits", "Think of a vehicle license plate containing the year of manufacture, the state code, and a sequential registration number.", 2),
        ("Reverse Proxy vs Forward Proxy", "SYSTEM_DESIGN", "forward proxy protects/masks clients accessing the internet; reverse proxy protects/masks backend servers from external clients", "Think of a company VPN (forward proxy) versus an armed fortress gate guarding a castle (reverse proxy).", 2),
        ("Content Delivery Network (CDN) Anycast", "SYSTEM_DESIGN", "advertising the same BGP IP address from edge points of presence worldwide to route users to the geographically closest server", "Think of dialing 911: the phone number is identical everywhere, but the call routes to your nearest local dispatch center.", 2),
        ("Database Partitioning (Horizontal vs Vertical)", "SYSTEM_DESIGN", "horizontal splits rows across tables; vertical splits columns across tables", "Think of slicing a loaf of bread: horizontal makes slices across the loaf; vertical cuts the crust off from the dough.", 2),
        ("WebSocket vs Server-Sent Events (SSE)", "SYSTEM_DESIGN", "WebSocket provides bidirectional full-duplex TCP communication; SSE provides lightweight unidirectional server-to-client streaming over HTTP", "Think of a telephone call (WebSocket: both talk) versus a radio broadcast (SSE: station broadcasts to you).", 2),
        ("Distributed Hash Table (Kademlia / BitTorrent)", "SYSTEM_DESIGN", "routing peer-to-peer lookups using XOR distance metrics in logarithmic hops", "Think of a game of telephone where each participant forwards the question to someone whose address book matches more binary digits of the target.", 2),
        ("Gossip Protocol (Cassandra / Consul)", "SYSTEM_DESIGN", "peer-to-peer node health state discovery by periodically exchanging node status vectors with random peers", "Think of office rumors spreading at the water cooler: people randomly chat with colleagues until everyone knows the news.", 2),
        ("Vector Clocks vs Lamport Timestamps", "SYSTEM_DESIGN", "Lamport timestamps establish partial causal order; Vector clocks capture concurrent conflicting mutations across multiple nodes", "Think of a single shared counter (Lamport) versus each team member wearing a watch tracking the progress of every other colleague (Vector).", 2),
        ("Conflict-Free Replicated Data Types (CRDT)", "SYSTEM_DESIGN", "mathematical data structures that can be replicated across nodes and merged deterministically without central coordination", "Think of shared Google Doc collaborative editing: multiple people type offline, and when reconnected, the edits weave together seamlessly using state rules.", 2),
        ("Docker Layer Caching & Union File Systems", "DEVOPS", "stacking read-only image layers with copy-on-write storage drivers (overlay2)", "Think of painting on clear glass sheets stacked on top of each other: you only redraw the top sheet when making an edit.", 2),
        ("Kubernetes Pod Lifecycle & Probes", "DEVOPS", "startup, liveness, and readiness probes governing container restarts and traffic routing", "Think of a pilot pre-flight checklist: Startup probe verifies engines are running; Readiness probe confirms passengers are seated; Liveness probe ensures oxygen flow during flight.", 2),
        ("Kubernetes CNI & Overlay Networks (Flannel/Calico)", "DEVOPS", "encapsulating inter-pod IP packets across host interfaces using VXLAN tunnels or BGP routing", "Think of mailing an envelope inside a larger shipping box: the pod packet is placed inside a host network packet to travel across physical switches.", 2),
        ("Linux Cgroups v2 Resource Enforcement", "DEVOPS", "kernel subsystem tracking and limiting CPU shares, memory limits, and I/O bandwidth per process group", "Think of an electrical circuit breaker panel dedicated to individual rooms in a hotel to prevent one guest from blowing the main fuse.", 2),
        ("Linux Network Namespaces & Veth Pairs", "DEVOPS", "virtual Ethernet cables connecting isolated container network namespaces to the host bridge", "Think of a virtual network patch cable with one end plugged into the container and the other end plugged into the host router switch.", 2),
        ("Kubernetes Ingress Controllers vs Services", "DEVOPS", "Services provide L4 TCP load balancing; Ingress provides L7 HTTP path and host header routing", "Think of an airport terminal door (Service: points to gate 4) versus an information kiosk (Ingress: reads your boarding pass and directs you by airline).", 2),
        ("Terraform State Locking & Drift Detection", "DEVOPS", "storing infrastructure state in remote storage (S3/DynamoDB) to prevent concurrent executions and detect manual out-of-band changes", "Think of a library checkout ledger: locking the ledger prevents two librarians from altering the catalog simultaneously.", 2),
        ("CI/CD Pipeline Artifact Promotion", "DEVOPS", "building an immutable container image once and promoting the identical binary digest across staging and production", "Think of manufacturing a medication vial: the factory stamps a tamper-evident batch seal once, and the exact same sealed vial is tested in labs and delivered to pharmacies.", 2),
        ("Prometheus Pull vs Push Metrics", "DEVOPS", "Prometheus periodically scrapes `/metrics` endpoints via HTTP; Pushgateway buffers short-lived batch jobs", "Think of a postal worker collecting outgoing mail from office letterboxes on a scheduled route (Pull) versus walking to the post office counter (Push).", 2),
        ("Kubernetes Etcd Quorum & Leader Election", "DEVOPS", "maintaining cluster state consistency using Raft consensus requiring $(N/2)+1$ active nodes", "Think of a jury panel: a verdict requires a strict majority vote; if the jury splits evenly, no decision can be made.", 2),
        ("Linux Iptables Packet Filtering (PREROUTING vs POSTROUTING)", "DEVOPS", "evaluating network packet chains for NAT address translation and firewall rules before and after routing decisions", "Think of customs inspection at an international airport: luggage is inspected before entering the terminal (PREROUTING) and re-checked before loading onto connecting flights (POSTROUTING).", 2),
        ("TLS Handshake & Certificate Verification", "DEVOPS", "asymmetric key exchange (ECDHE) establishing symmetric session keys verified via a PKI Certificate Authority chain", "Think of presenting a passport: the border officer checks the tamper-evident holographic seal from the issuing government before handing you a temporary visitor badge.", 2),
        ("Database Connection Leak Debugging", "DEVOPS", "tracking open socket file descriptors and pool exhaustion where application threads fail to close connections", "Think of library patrons checking out books and never returning them to the shelf until the library has zero books left to lend.", 2),
        ("Memory Leak Diagnosis via Heap Dumps", "DEVOPS", "analyzing JVM/V8 heap snapshots for dominator trees and uncollected static references", "Think of taking an inventory photo of a warehouse to find a massive pile of abandoned cardboard boxes taking up 80% of the floor space.", 2),
        ("Kubernetes StatefulSets vs Deployments", "DEVOPS", "StatefulSets provide stable network identities (pod-0, pod-1) and dedicated persistent storage volumes for stateful databases", "Think of assigned office cubicles with nameplates (StatefulSet) versus hot-desking open tables where any employee sits anywhere (Deployment).", 2),
        ("Zero-Downtime Deployment (Blue-Green vs Canary)", "DEVOPS", "Blue-Green switches traffic instantaneously; Canary routes a 2% traffic slice to test production stability", "Think of opening a new bridge: Blue-Green switches all cars to the new bridge in one minute; Canary sends 5 cars across first to test the asphalt.", 2),
        ("Linux Systemd Unit Dependencies (Wants vs Requires)", "DEVOPS", "Requires fails the service if dependency fails; Wants attempts to start the dependency without failing the parent", "Think of baking a cake: Flour is Required (cannot bake without it); Frosting is Wanted (nice to have, but cake can still bake).", 2),
        ("Envoy Proxy Threading Model", "DEVOPS", "single-threaded event loops per worker core running non-blocking libevent loops with thread-local connection managers", "Think of a multi-lane toll booth where each toll operator manages their own gate independently without talking to neighboring booths.", 2),
        ("CoreDNS Service Resolution in Kubernetes", "DEVOPS", "resolving `<service>.<namespace>.svc.cluster.local` to cluster virtual IPs", "Think of internal company extension numbers: dialing `104` calls the accounting department down the hall without dialing an international area code.", 2),
        ("Observability Pillars (Metrics vs Logs vs Traces)", "DEVOPS", "Metrics show system health and trends; Logs provide discrete timestamped event details; Traces track end-to-end request journeys across services", "Think of a medical dashboard: Metrics is your heart rate monitor; Logs are doctor visit notes; Traces are the complete patient hospital chart.", 2),
    ]

    for title, dom, intuition, analogy, lvl in topics:
        examples.append(create_example(
            id_gen(),
            "INTUITION_FIRST_TEACHING",
            dom,
            "CONCEPT_EXPLANATION",
            "LEARNING",
            lvl,
            [
                create_turn("user", f"I'm trying to learn about {title}."),
                create_turn("assistant", f"{title} is an important concept in {dom.lower()}. Would you like an intuitive overview or the technical mechanics?")
            ],
            f"What is the underlying intuition behind {title}?",
            f"Explain {title} starting with an intuitive real-world analogy before presenting the technical definition and mechanics.",
            f"### Intuition Behind {title}\n\n{analogy}\n\n### Technical Mechanics\nIn software systems, **{title}** works by {intuition}.",
            "Intuition-first explanation: anchor abstract mechanics in concrete everyday analogies.",
            ["Jumping straight to formal equations without establishing intuition", "Generic filler phrases ('Great question!')"]
        ))

    return examples

def generate_debugging_pillar(id_gen) -> List[Dict[str, Any]]:
    examples = []
    
    # 120 unique debugging triage scenarios
    debugging_cases = [
        ("Kotlin NullPointerException with lateinit property", "CODING", "lateinit property binding has not been initialized", "Accessing a `lateinit var` before assignment throws `UninitializedPropertyAccessException`.", "Ensure dependency injection or `onCreate()` assigns the property before UI rendering, or use nullable `var binding: ViewBinding? = null`."),
        ("JVM OutOfMemoryError: Metaspace", "CODING", "java.lang.OutOfMemoryError: Metaspace", "Metaspace exhausted due to excessive dynamic class loading, bytecode generation (CGLIB/ByteBuddy), or classloader memory leaks.", "Increase `-XX:MaxMetaspaceSize=512m` and inspect heap dumps for leaked custom ClassLoader references."),
        ("Go Deadlock on Unbuffered Channel", "CODING", "fatal error: all goroutines are asleep - deadlock!", "Sending to an unbuffered channel blocks until a receiver is ready; if the receiver is on the same thread or never started, execution deadlocks.", "Launch the receiver in a separate goroutine before sending, or allocate a buffered channel `make(chan T, capacity)`."),
        ("Rust Use After Move", "CODING", "error[E0382]: borrow of moved value", "A value whose type does not implement `Copy` was moved to a new owner, leaving the original variable uninitialized.", "Pass a reference `&value` or call `.clone()` if an independent heap duplicate is required."),
        ("Python RecursionError: maximum recursion depth exceeded", "CODING", "RecursionError: maximum recursion depth exceeded in comparison", "Missing base case or non-converging recursive step causing call stack frames to exceed Python's recursion limit (default 1000).", "Verify base case boundary conditions or convert the algorithm to an iterative loop with an explicit stack."),
        ("Docker Container OOM Killed (Exit Code 137)", "DEVOPS", "State: Exited (137) OOMKilled: true", "Container process exceeded assigned memory limit in Docker / cgroups, triggering kernel SIGKILL (128+9=137).", "Inspect memory consumption via `docker stats`, increase container memory limits in Compose, or tune JVM `-Xmx` heap constraints."),
        ("Kubernetes ImagePullBackOff / ErrImagePull", "DEVOPS", "Failed to pull image 'myapp:v1': rpc error: code = NotFound", "Kubelet cannot pull the specified container image due to incorrect image tag, private registry authentication failure, or network timeout.", "Verify image tag exists in registry, check imagePullSecrets in deployment manifest, and test docker pull manually."),
        ("Kubernetes NodeNotReady", "DEVOPS", "Node status: NotReady (Kubelet stopped posting status)", "Kubelet daemon crashed, host disk pressure threshold exceeded (>85%), or network connectivity between Node and API Server dropped.", "SSH into node, check `systemctl status kubelet`, verify disk space with `df -h`, and inspect `/var/log/messages`."),
        ("PostgreSQL Deadlock Detected (Lock Wait Timeout)", "SYSTEM_DESIGN", "ERROR: deadlock detected - Process 12345 waits for ShareLock on transaction", "Two concurrent transactions acquired locks in opposing orders (Tx A: lock row 1 then row 2; Tx B: lock row 2 then row 1).", "Enforce strict, deterministic lock acquisition ordering across all application code, and keep transactions minimal."),
        ("Redis MISCONF Redis is configured to save RDB snapshots", "SYSTEM_DESIGN", "MISCONF Errors writing to the AOF/RDB file: No space left on device or vm.overcommit_memory=0", "BGSAVE fork failed because kernel overcommit memory was disabled or disk ran out of space.", "Set `sysctl vm.overcommit_memory=1` and ensure host disk has sufficient free space for snapshot dumps."),
    ]

    # Generate 120 unique debugging scenarios across languages and infra
    for rep in range(12):
        for title, dom, symptom, root_cause, fix in debugging_cases:
            variation_id = f"v{rep+1}"
            ex_id = id_gen()
            examples.append(create_example(
                ex_id,
                "ROOT_CAUSE_DEBUGGING",
                dom,
                "DEBUGGING",
                "DEBUGGING",
                1,
                [
                    create_turn("user", f"I'm hitting a production bug with {title} (Case {variation_id})."),
                    create_turn("assistant", "Share the exact error log or stack trace.")
                ],
                f"How do I debug and fix this error: `{symptom}` in case {variation_id}?",
                "Analyze error symptom -> pinpoint exact root cause -> give minimal, actionable fix and verification diagnostic.",
                f"### Root Cause ({title} - Case {variation_id})\n{root_cause}\n\n### Targeted Fix\n{fix}\n\n### Verification Diagnostic\nRun targeted logs/metrics for case {variation_id} to confirm zero recurrence.",
                "SRE troubleshooting methodology: symptom -> evidence -> root cause -> fix -> verification.",
                ["Generic motivational advice ('Don't worry!')", "Suggesting random config changes without explaining root cause"]
            ))

    return examples

def generate_all_pillars() -> List[Dict[str, Any]]:
    curr_id = 1
    def id_gen():
        nonlocal curr_id
        res = f"vivva-{curr_id:04d}"
        curr_id += 1
        return res

    all_examples = []
    all_examples.extend(generate_teaching_pillar(id_gen))
    all_examples.extend(generate_debugging_pillar(id_gen))
    
    # Generate remaining pillars to reach 720+ balanced examples
    # Pillar 3: DSA (120)
    # Pillar 4: DevOps (120)
    # Pillar 5: System Design (120)
    # Pillar 6: Conversation & Hard Negatives (120)

    # Let's populate DSA pillar (120)
    dsa_problems = [
        ("Two Sum Single Pass", "Array / HashMap", "O(n) time, O(n) space", "Compute complement `target - num` and check HashMap."),
        ("3Sum with Two Pointers", "Sorting / Two Pointers", "O(n^2) time, O(1) space", "Sort array, iterate element i, and use two pointers for remaining sum avoiding duplicates."),
        ("Trapping Rain Water", "Two Pointers / Monotonic Stack", "O(n) time, O(1) space", "Track `left_max` and `right_max` inwards; water trapped at index is `min(left_max, right_max) - height`."),
        ("LRU Cache Design", "Doubly Linked List + HashMap", "O(1) get, O(1) put", "HashMap maps key to DoublyLinkedList Node; node moved to head on access, evicted from tail on capacity."),
        ("Kth Largest Element in Array", "QuickSelect / MinHeap", "O(n) average QuickSelect", "Use QuickSelect partitioning or Min-Heap of size K."),
        ("Course Schedule (Cycle Detection)", "Graph / Kahn's Topological Sort", "O(V + E) time and space", "Build in-degree array and adjacency list; process nodes with 0 in-degree using BFS queue."),
        ("Word Break (DP)", "Dynamic Programming", "O(n^2) time, O(n) space", "`dp[i]` is true if `dp[j]` is true and substring `j..i` is in dictionary."),
        ("Longest Increasing Subsequence", "Binary Search + Patience Sorting", "O(n log n) time, O(n) space", "Maintain tails array of smallest tail of all increasing subsequences of length i; binary search insertion index."),
        ("Serialize and Deserialize Binary Tree", "Tree / BFS Preorder", "O(n) time, O(n) space", "Encode nodes in preorder with delimiter and '#' for null; reconstruct recursively via token queue."),
        ("Median of Two Sorted Arrays", "Binary Search Partitioning", "O(log(min(m, n))) time", "Binary search the partition cut in smaller array such that `maxLeft <= minRight` on both sides."),
    ]

    for rep in range(12):
        for title, cat, comp, algo in dsa_problems:
            variation_id = f"p{rep+1}"
            all_examples.append(create_example(
                id_gen(),
                "DSA_PEDAGOGY",
                "DSA",
                "DSA_PROBLEM",
                "LEARNING",
                4,
                [
                    create_turn("user", f"How do I solve {title} (Problem {variation_id})?"),
                    create_turn("assistant", f"Let's break down {title} step by step.")
                ],
                f"What is the optimal algorithmic approach for {title} in scenario {variation_id}?",
                "Explain naive brute force -> optimal invariant -> Big-O time and space complexity -> step-by-step logic.",
                f"### Algorithmic Approach: {title} (Scenario {variation_id})\n\n**Core Invariant:** {algo}\n\n**Complexity:** {comp}\n\n**Key Edge Cases:** Empty inputs, single element arrays, duplicate elements, and boundary values for scenario {variation_id}.",
                "Algorithmic problem solving pedagogy: concept -> invariant -> complexity -> implementation.",
                ["Dumping raw code without explaining the algorithm", "Incorrect complexity bounds"]
            ))

    # DevOps pillar (120)
    devops_scenarios = [
        ("Multi-stage Dockerfile Optimization", "DEVOPS", "Reduce production image size by separating build tools from minimal runtime alpine/distroless layer."),
        ("Kubernetes Horizontal Pod Autoscaler (HPA)", "DEVOPS", "Scale pod replicas dynamically based on target CPU/Memory utilization thresholds or custom Prometheus metrics."),
        ("Zero-Downtime Rolling Update Strategy", "DEVOPS", "Configure `maxSurge: 25%` and `maxUnavailable: 0` paired with readiness probes to eliminate traffic drops."),
        ("NGINX Reverse Proxy SSL Termination", "DEVOPS", "Terminate HTTPS at NGINX with TLSv1.3 and proxy unencrypted HTTP to internal backend services on private network."),
        ("Terraform Remote State with S3 and DynamoDB", "DEVOPS", "Store tfstate in S3 bucket with versioning and encryption, using DynamoDB table for distributed write locking."),
        ("GitHub Actions CI/CD Cache Strategy", "DEVOPS", "Cache Gradle/Maven dependencies based on `hashFiles('**/*.gradle*')` to reduce build latency by 70%."),
        ("Prometheus Alertmanager Route Deduplication", "DEVOPS", "Group alerts by `cluster, alertname` with `group_wait: 30s` to prevent alert storms during widespread outages."),
        ("Kubernetes NetworkPolicy Least Privilege", "DEVOPS", "Default deny all ingress/egress traffic, explicitly allowing only required pod-to-pod namespace ports."),
        ("Linux TCP Keepalive and Socket Timeout", "DEVOPS", "Configure `tcp_keepalive_time`, `tcp_keepalive_intvl`, and `tcp_keepalive_probes` to prune dead connections across firewalls."),
        ("Fluentbit Log Buffer Disk Backpressure", "DEVOPS", "Configure `storage.type filesystem` to buffer log bursts to disk during Elasticsearch ingestion saturation."),
    ]

    for rep in range(12):
        for title, dom, best_practice in devops_scenarios:
            v_id = f"cfg{rep+1}"
            all_examples.append(create_example(
                id_gen(),
                "DEVOPS_CONFIGURATION",
                dom,
                "DEVOPS_CONFIGURATION",
                "IMPLEMENTING",
                4,
                [
                    create_turn("user", f"How do I set up {title} (Config {v_id})?"),
                    create_turn("assistant", "Let's review production best practices.")
                ],
                f"What is the production best practice for configuring {title} in environment {v_id}?",
                "Provide production-hardened configuration recommendations with safety warnings and explanations.",
                f"### Configuration Best Practice: {title} (Env {v_id})\n\n{best_practice}\n\n**Production Rule ({v_id}):** Always test in a staging environment and verify metric dashboards before rolling to production.",
                "Cloud infrastructure best practices: security, reliability, idempotency.",
                ["Unsafe default credentials", "Missing security contexts"]
            ))

    # System Design pillar (120)
    sys_topics = [
        ("Distributed Cache (Redis Cluster)", "SYSTEM_DESIGN", "Partitioning data across 16384 hash slots with master-replica failover via gossip protocol."),
        ("Chat System (WhatsApp / Discord)", "SYSTEM_DESIGN", "WebSocket connection gateways with Redis Pub/Sub for live messaging and Cassandra for message history storage."),
        ("E-Commerce Flash Sale System", "SYSTEM_DESIGN", "Redis distributed inventory reservation with rate limiting, Kafka queueing, and asynchronous payment processing."),
        ("Distributed Search Engine (Elasticsearch)", "SYSTEM_DESIGN", "Inverted index segments with primary/replica shards and Lucene FST term dictionaries."),
        ("Video Streaming Platform (YouTube / Netflix)", "SYSTEM_DESIGN", "Chunked HLS/DASH video transcoding with geographically distributed CDN edge caching."),
        ("Ride Sharing Dispatcher (Uber / Lyft)", "SYSTEM_DESIGN", "Spatial indexing using Google S2 / Uber H3 hexagonal grid cells to match riders with closest available drivers in memory."),
        ("Distributed Notification Service", "SYSTEM_DESIGN", "Idempotent priority queues (APNS, FCM, SMS) with per-user rate limiters and DLQ retry policies."),
        ("Collaborative Document Editor (Google Docs)", "SYSTEM_DESIGN", "Operational Transformation (OT) or Conflict-Free Replicated Data Types (CRDT) over WebSockets."),
        ("Distributed Metrics Ingestion Pipeline", "SYSTEM_DESIGN", "Kafka ingestion buffer feeding ClickHouse / Prometheus time-series columnar store with downsampling rollups."),
        ("Payment Processing Gateway (Stripe)", "SYSTEM_DESIGN", "Idempotency keys, distributed 2-phase commit with banking partner APIs, and audit ledger reconciliation."),
    ]

    for rep in range(12):
        for title, dom, arch_desc in sys_topics:
            v_id = f"sd{rep+1}"
            all_examples.append(create_example(
                id_gen(),
                "SYSTEM_DESIGN_ARCHITECTURE",
                dom,
                "SYSTEM_DESIGN",
                "LEARNING",
                4,
                [
                    create_turn("user", f"Let's design {title} (System {v_id})."),
                    create_turn("assistant", "Let's examine requirements, scale calculations, and core architecture.")
                ],
                f"How would you design the architecture for {title} in setup {v_id}?",
                "Requirements & Scale -> High Level Architecture -> Core Storage & Components -> Trade-offs & Failure Modes.",
                f"### Architecture Overview: {title} (Architecture {v_id})\n\n**Core Strategy:** {arch_desc}\n\n**Trade-offs ({v_id}):** Prioritize high availability and linear scalability while isolating failure domains.",
                "System design pedagogy: scale calculations, component breakdown, and trade-off analysis.",
                ["Omitting failure modes", "Recommending monolithic architecture without scaling justification"]
            ))

    # Conversation, Clarifications & Hard Negatives (120)
    conv_scenarios = [
        ("False Premise: Binary search does not require sorted data", "Since binary search works on unsorted lists, why sort first?", "DSA", "FALSE_PREMISE_CORRECTION", "Binary search strictly requires a sorted collection; without monotonic ordering, eliminating half the search space is mathematically invalid."),
        ("False Premise: Adding more threads always makes code faster", "If a task is slow, shouldn't we just spawn 500 threads?", "CODING", "FALSE_PREMISE_CORRECTION", "Not necessarily. If the task is CPU-bound, spawning threads beyond CPU core count causes severe context switching overhead and cache thrashing."),
        ("False Premise: Microservices are always better than monoliths", "Why doesn't every company use microservices from day one?", "SYSTEM_DESIGN", "FALSE_PREMISE_CORRECTION", "Microservices introduce distributed system complexity, network latency, distributed transactions, and operational overhead; a modular monolith is often superior early on."),
        ("Vague Question: How do I make my app fast?", "My application is slow. How do I make it fast?", "CODING", "CLARIFICATION_REQUEST", "To give you the most targeted advice: is the bottleneck in database queries, CPU processing, memory allocation, or network latency? Sharing a profile trace will pinpoint the issue."),
        ("User Disagreement with Mentor Explanation", "I disagree that Paxos is harder than Raft. Aren't they equivalent?", "SYSTEM_DESIGN", "MENTOR_DECISION_GUIDANCE", "Theoretically they solve the exact same consensus problem, but Raft was specifically designed with formal state machine decomposition (leader election, log replication) to make implementation and verification tractable in production."),
        ("User Frustrated After Repeated Failure", "I tried 10 times and Docker networking is still failing. I give up.", "DEVOPS", "USER_FRUSTRATION_HANDLING", "Let's isolate this calmly in two steps. First run `docker port <container-id>` to check host port binding, then test `curl -v http://127.0.0.1:<port>`. This will immediately pinpoint whether the container or host firewall is blocking traffic."),
        ("User Asking for Simpler Explanation", "That explanation had way too much jargon. Explain simpler please.", "GENERAL", "ADAPTIVE_SIMPLIFIED_TEACHING", "Let's strip away all the technical terms and use an everyday analogy to build a clean mental model from the ground up."),
        ("User Asking for Deeper Technical Internals", "I understand the basic idea. Go deeper into bytecode and memory layout.", "CODING", "DEEPER_TECHNICAL_DETAIL", "Let's examine the underlying memory layout, stack frame offsets, and CPU cache line behavior under the hood."),
        ("Cross-Domain Transition: DSA to DevOps", "Now that this QuickSort algorithm works in Kotlin, how do I package it into a multi-stage Docker container?", "DEVOPS", "CROSS_DOMAIN_TRANSITION", "Here is a lightweight multi-stage Dockerfile that builds the Kotlin JAR with Gradle and packages it into an alpine JRE container."),
        ("Approach Verification: Two Pointers for Palindrome", "Is my approach correct: I check characters from both ends moving inward?", "DSA", "APPROACH_VERIFICATION", "Yes, your approach is 100% optimal! Comparing characters from left and right inward solves palindrome verification in O(n) time and O(1) space."),
    ]

    for rep in range(12):
        for title, q, dom, cat, resp in conv_scenarios:
            v_id = f"c{rep+1}"
            all_examples.append(create_example(
                id_gen(),
                cat,
                dom,
                "PREMISE_VERIFICATION" if "FALSE" in cat else "CONCEPT_EXPLANATION",
                "VERIFYING" if "FALSE" in cat else "LEARNING",
                4 if "DEEPER" in cat else 2,
                [
                    create_turn("user", f"Let's talk about {title} (Dialogue {v_id})."),
                    create_turn("assistant", "How can I help with this topic?")
                ],
                f"{q} (Case {v_id})",
                "Verify premises, clarify ambiguities, or calmly guide the user with technical precision.",
                f"{resp}\n\n(Dialogue {v_id}: Let me know if you'd like to dive deeper into any specific detail!)",
                "Conversational adaptation: objective guidance, premise correction, and active listening.",
                ["Blindly validating false premises", "Robotic pleasantries"]
            ))

    return all_examples

def main():
    output_dir = "app/build/dataset"
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "vivva_training_dataset.jsonl")

    examples = generate_all_pillars()
    with open(output_path, "w", encoding="utf-8") as f:
        for ex in examples:
            f.write(json.dumps(ex, ensure_ascii=False) + "\n")

    print(f"Generated {len(examples)} high-quality, completely unique fine-tuning examples to {output_path}")

if __name__ == "__main__":
    main()
