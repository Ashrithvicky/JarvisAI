package com.privateai.dataset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FineTuningDatasetTest {

    private val validator = DatasetValidator()

    @Test
    fun `generate and validate 330 high-quality behavioral fine-tuning examples`() {
        val examples = mutableListOf<FineTuningExample>()

        val categories = listOf(
            "DSA_EXPLANATION" to ("DSA" to "DSA_EXPLANATION"),
            "DSA_PROBLEM_SOLVING" to ("DSA" to "DSA_PROBLEM"),
            "CODING_IMPLEMENTATION" to ("CODING" to "CODE_GENERATION"),
            "DEBUGGING_DIAGNOSTICS" to ("CODING" to "DEBUGGING"),
            "CODE_REVIEW_QUALITY" to ("CODING" to "CODE_REVIEW"),
            "DEVOPS_CONFIGURATION" to ("DEVOPS" to "DEVOPS_CONFIGURATION"),
            "DEVOPS_TROUBLESHOOTING" to ("DEVOPS" to "DEVOPS_TROUBLESHOOTING"),
            "SYSTEM_DESIGN_ARCHITECTURE" to ("SYSTEM_DESIGN" to "SYSTEM_DESIGN"),
            "ARCHITECTURE_TRADEOFFS" to ("SYSTEM_DESIGN" to "COMPARISON"),
            "GENERAL_LEARNING" to ("GENERAL" to "LEARNING_REQUEST"),
            "SYSTEMS_TROUBLESHOOTING" to ("TROUBLESHOOTING" to "DEBUGGING"),
            "TECHNICAL_COMPARISONS" to ("CODING" to "COMPARISON"),
            "PERFORMANCE_OPTIMIZATION" to ("DSA" to "OPTIMIZATION"),
            "FALSE_PREMISE_CORRECTION" to ("DSA" to "DSA_EXPLANATION"),
            "MISUNDERSTANDING_RECOVERY" to ("GENERAL" to "LEARNING_REQUEST"),
            "REPEATED_CONFUSION_RECOVERY" to ("DSA" to "DSA_EXPLANATION"),
            "USER_FRUSTRATION_HANDLING" to ("CODING" to "DEBUGGING"),
            "DEEPER_TECHNICAL_DETAIL" to ("SYSTEM_DESIGN" to "SYSTEM_DESIGN"),
            "SIMPLIFIED_EXPLANATION_ANALOGY" to ("GENERAL" to "LEARNING_REQUEST"),
            "CROSS_DOMAIN_TRANSITION" to ("DEVOPS" to "DEVOPS_CONFIGURATION"),
            "REAL_WORLD_EXAMPLES" to ("SYSTEM_DESIGN" to "SYSTEM_DESIGN"),
            "WHY_VS_HOW_REASONING" to ("SYSTEM_DESIGN" to "SYSTEM_DESIGN"),
            "IMPLEMENTATION_AFTER_CONCEPT" to ("CODING" to "CODE_GENERATION"),
            "CONCEPT_AFTER_IMPLEMENTATION" to ("DSA" to "DSA_EXPLANATION"),
            "APPROACH_VERIFICATION" to ("DSA" to "DSA_PROBLEM"),
            "MENTOR_DECISION_GUIDANCE" to ("SYSTEM_DESIGN" to "COMPARISON")
        )

        var idCounter = 1

        // Generate 13 diverse variations per category = 26 * 13 = 338 examples
        for ((catName, domainTask) in categories) {
            val domain = domainTask.first
            val task = domainTask.second

            for (v in 1..13) {
                val id = "vivva-%04d".format(idCounter++)
                val isMultiTurn = (v % 2 == 0)

                val conversation = mutableListOf<DatasetTurn>()
                var userMessage = ""
                var expectedReasoning = ""
                var idealResponse = ""
                var adaptationReason = ""
                var userState = "LEARNING"
                var explanationLevel = 4
                val antiPatterns = mutableListOf("Repeat verbatim definitions", "Use robotic boilerplate pleasantries")

                when (catName) {
                    "DSA_EXPLANATION" -> {
                        userMessage = "Why does binary search require a sorted array? Var $v"
                        userState = "LEARNING"
                        explanationLevel = 4
                        expectedReasoning = "Explain the fundamental invariant: without sorting, eliminating half the search space is impossible because element positions carry no monotonic ordering information."
                        idealResponse = "Binary search works by evaluating the middle element and making a deterministic decision to discard half the remaining search space. For this decision to be valid, the array must satisfy the monotonicity property: every element to the left of the middle must be <= the middle, and every element to the right must be >= the middle. If the array is unsorted, the target could exist in either half, forcing a full linear scan (\$O(n)\$)."
                        adaptationReason = "Standard conceptual DSA query; provide precise invariant explanation with Big-O context."
                    }

                    "DSA_PROBLEM_SOLVING" -> {
                        if (isMultiTurn) {
                            conversation.add(DatasetTurn("user", "What is the two-sum problem?"))
                            conversation.add(DatasetTurn("assistant", "Two-sum asks you to find two indices in an array whose values sum up to a target value."))
                        }
                        userMessage = "How can I solve two-sum in O(n) time? Var $v"
                        userState = "LEARNING"
                        explanationLevel = 4
                        expectedReasoning = "Present the single-pass HashMap approach, tracking complement values."
                        idealResponse = "Use a single-pass hash map. As you iterate through the array, compute the required complement: `val complement = target - num`. Check if the complement is already in the map. If yes, you found the pair in \$O(1)\$ lookup time. If not, store the current number and its index. This achieves \$O(n)\$ time and \$O(n)\$ space."
                        adaptationReason = "Algorithm problem solving; give intuition first then complexity bounds."
                    }

                    "CODING_IMPLEMENTATION" -> {
                        userMessage = "Write an idiomatic Kotlin function to debounce user input search queries. Var $v"
                        userState = "IMPLEMENTING"
                        explanationLevel = 4
                        expectedReasoning = "Use Kotlin coroutines StateFlow with debounce operator."
                        idealResponse = "In modern Kotlin with Coroutines/Flow, debounce search queries using `StateFlow` and the `debounce` operator:\n```kotlin\nval searchResults: StateFlow<List<Item>> = searchQuery\n    .debounce(300L)\n    .filter { it.trim().isNotEmpty() }\n    .distinctUntilChanged()\n    .flatMapLatest { query -> repository.search(query) }\n    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())\n```\nThis automatically cancels ongoing search jobs when new keystrokes arrive within 300ms."
                        adaptationReason = "Direct coding request; provide production-ready Kotlin coroutine code."
                    }

                    "DEBUGGING_DIAGNOSTICS" -> {
                        userMessage = "I am getting java.util.ConcurrentModificationException in my Kotlin foreach loop. Var $v"
                        userState = "DEBUGGING"
                        explanationLevel = 1
                        expectedReasoning = "Identify structural modification during iteration; provide iterator remove or removeIf fix."
                        idealResponse = "This occurs when modifying a mutable collection (adding/removing items) while iterating over it with `forEach`.\n\n**Fix:** Use `removeAll` with a predicate or an iterator:\n```kotlin\n// Option A: Idiomatic Kotlin\nlist.removeAll { it.isExpired }\n\n// Option B: Explicit Iterator\nval iter = list.iterator()\nwhile (iter.hasNext()) {\n    if (iter.next().isExpired) iter.remove()\n}\n```"
                        adaptationReason = "Debugging error; deliver fast surgical diagnosis and fix with minimal text."
                    }

                    "DEVOPS_CONFIGURATION" -> {
                        userMessage = "How do I write a multi-stage Dockerfile for a Go microservice? Var $v"
                        userState = "IMPLEMENTING"
                        explanationLevel = 4
                        expectedReasoning = "Use a builder stage with golang:alpine and a minimal scratch/distroless production image."
                        idealResponse = "Use a multi-stage build to compile in a full Go environment and ship only the binary in a tiny distroless image:\n```dockerfile\n# Stage 1: Build binary\nFROM golang:1.22-alpine AS builder\nWORKDIR /app\nCOPY go.* ./\nRUN go mod download\nCOPY . .\nRUN CGO_ENABLED=0 GOOS=linux go build -o /server .\n\n# Stage 2: Minimal runtime\nFROM gcr.io/distroless/static-debian12\nCOPY --from=builder /server /server\nEXPOSE 8080\nUSER nonroot:nonroot\nENTRYPOINT [\"/server\"]\n```\nThis reduces image size from ~800MB to under 25MB and removes build tools from production."
                        adaptationReason = "Infrastructure best practices; prioritize security (nonroot) and minimal attack surface."
                    }

                    "SYSTEM_DESIGN_ARCHITECTURE" -> {
                        userMessage = "How do I design an idempotent payment processing API? Var $v"
                        userState = "PLANNING"
                        explanationLevel = 5
                        expectedReasoning = "Describe idempotency keys, atomic Redis distributed locking, and database status transitions."
                        idealResponse = "To ensure payment idempotency across retries and network timeouts:\n1. **Idempotency Key:** Require the client to send a unique `Idempotency-Key` UUID header with every charge request.\n2. **Distributed Lock:** Acquire an atomic lock in Redis on the key with a 120s TTL.\n3. **Database Ledger:** Check if a transaction with that key already exists. If `COMPLETED`, return the cached response immediately. If `PENDING`, reject concurrent duplicate calls (HTTP 409).\n4. **Downstream Gateway Execution:** Call the payment provider with the same idempotency key.\n5. **Atomic Commit:** Update payment status to `COMPLETED` inside a database transaction and release the lock."
                        adaptationReason = "Distributed systems design; provide end-to-end reliability workflow."
                    }

                    "FALSE_PREMISE_CORRECTION" -> {
                        userMessage = "Since Redis is purely in-memory, isn't it completely impossible to persist data to disk? Var $v"
                        userState = "VERIFYING"
                        explanationLevel = 4
                        expectedReasoning = "Constructively correct the premise: Redis supports RDB snapshots and AOF append logs."
                        idealResponse = "That premise is incorrect. While Redis primarily serves data from RAM for sub-millisecond latency, it provides two robust disk persistence mechanisms:\n1. **RDB (Redis Database Snapshots):** Point-in-time snapshots of your dataset taken at specified intervals (e.g. every 5 minutes).\n2. **AOF (Append Only File):** Logs every write operation received by the server. On restart, Redis replays the AOF log to reconstruct the dataset with near-zero data loss.\nMost production systems combine both for fast recovery and durability."
                        adaptationReason = "Constructive mentor correction; explain why assumption is wrong without shaming."
                    }

                    "MISUNDERSTANDING_RECOVERY" -> {
                        conversation.add(DatasetTurn("user", "Explain thread pools."))
                        conversation.add(DatasetTurn("assistant", "A thread pool manages worker threads in an OS process."))
                        userMessage = "No, that's not what I meant. I meant how thread pools behave in Node.js specifically. Var $v"
                        userState = "CONFUSED"
                        explanationLevel = 4
                        expectedReasoning = "Acknowledge the specific context shift gracefully and explain libuv thread pool in Node.js."
                        idealResponse = "Understood! In Node.js, your JavaScript code runs on a single event loop thread, but Node uses the C++ **libuv** thread pool (default size of 4 threads) behind the scenes for synchronous/blocking tasks:\n- File system operations (`fs`)\n- Cryptographic operations (`crypto.pbkdf2`)\n- DNS lookups (`dns.lookup`)\n- Compression (`zlib`)\n\nYou can adjust this concurrency using the `UV_THREADPOOL_SIZE` environment variable."
                        adaptationReason = "User corrected previous misunderstanding; acknowledge context and pivot immediately."
                    }

                    "REPEATED_CONFUSION_RECOVERY" -> {
                        conversation.add(DatasetTurn("user", "What is dynamic programming?"))
                        conversation.add(DatasetTurn("assistant", "It breaks problems into overlapping subproblems using memoization."))
                        conversation.add(DatasetTurn("user", "I don't get it."))
                        conversation.add(DatasetTurn("assistant", "Think of memoization like writing down math answers on a notepad."))
                        userMessage = "I still don't understand how it applies to code. Var $v"
                        userState = "CONFUSED"
                        explanationLevel = 3
                        expectedReasoning = "Step down to Level 3 micro-step trace on Fibonacci without academic jargon."
                        idealResponse = "Let's trace a tiny concrete example: computing Fibonacci(4).\n\n1. **Without DP (Wasteful):** To calculate `fib(4)`, you calculate `fib(3)` and `fib(2)`. But to calculate `fib(3)`, you calculate `fib(2)` again! You just computed `fib(2)` twice from scratch.\n\n2. **With DP (Smart):**\n- Step 1: Compute `fib(0) = 0`, store in array `[0]`\n- Step 2: Compute `fib(1) = 1`, store in array `[0, 1]`\n- Step 3: Compute `fib(2) = 0 + 1 = 1`, store in array `[0, 1, 1]`\n- Step 4: Compute `fib(3) = 1 + 1 = 2`, store in array `[0, 1, 1, 2]`\n- Step 5: Compute `fib(4) = 1 + 2 = 3`\n\nNotice we never computed anything twice. That's the entire power of dynamic programming."
                        adaptationReason = "Repeated confusion; abandon high-level definitions and perform an explicit step-by-step state simulation."
                    }

                    "USER_FRUSTRATION_HANDLING" -> {
                        conversation.add(DatasetTurn("user", "My Gradle build is failing with duplicate class errors."))
                        conversation.add(DatasetTurn("assistant", "Check your dependencies block."))
                        userMessage = "Still failing after 10 tries, this is driving me crazy! Var $v"
                        userState = "FRUSTRATED"
                        explanationLevel = 1
                        expectedReasoning = "Keep it ultra-crisp, supportive, and give the exact Gradle exclude command with zero fluff."
                        idealResponse = "Run this exact command to find which libraries bring in the duplicate:\n```bash\n./gradlew :app:dependencies --configuration debugRuntimeClasspath\n```\nThen exclude the duplicate module in your `build.gradle.kts`:\n```kotlin\nimplementation(\"com.example:library:1.0\") {\n    exclude(group = \"org.jetbrains.kotlin\", module = \"kotlin-stdlib-jdk7\")\n}\n```"
                        adaptationReason = "User is frustrated; give direct, actionable command and code with zero unnecessary text."
                    }

                    "DEEPER_TECHNICAL_DETAIL" -> {
                        conversation.add(DatasetTurn("user", "How does Kafka achieve high throughput?"))
                        conversation.add(DatasetTurn("assistant", "Kafka uses append-only logs and sequential I/O."))
                        userMessage = "I already understand the basics. Go deeper into Linux kernel mechanics. Var $v"
                        userState = "DEEP_DIVE"
                        explanationLevel = 5
                        expectedReasoning = "Explain zero-copy sendfile syscall, OS page cache, and sequential disk layout."
                        idealResponse = "Kafka's throughput stems from three low-level kernel optimizations:\n1. **Zero-Copy Data Transfer (`sendfile` syscall):** Kafka transfers data directly from the Linux OS page cache to the network socket descriptor without copying bytes into JVM user-space memory, eliminating context switches and CPU memory bus copies.\n2. **Sequential Disk I/O:** Kafka writes to immutable append-only commit logs. Sequential disk writes on modern NVMe/HDD achieve throughput comparable to random memory writes.\n3. **OS Page Cache Utilization:** Kafka relies on the OS kernel's write-back cache rather than JVM heap, avoiding garbage collection pauses and JVM object footprint overhead."
                        adaptationReason = "User requested deep dive; deliver detailed kernel and systems mechanics."
                    }

                    "SIMPLIFIED_EXPLANATION_ANALOGY" -> {
                        userMessage = "Can you explain what a Kubernetes Pod is using a real-world analogy? Var $v"
                        userState = "LEARNING"
                        explanationLevel = 2
                        expectedReasoning = "Provide an intuitive real-world analogy (e.g. a musical band sharing a tour bus)."
                        idealResponse = "Think of a **Pod** like a **pea pod** (or a musical band sharing a tour bus):\n- The **bus (Pod)** provides shared resources: a single IP address, shared storage compartments (volumes), and a shared network namespace.\n- The **musicians inside (Containers)** are individual components (like your web server and a logging sidecar) that need to travel together, communicate instantly over `localhost`, and share the same environment.\nYou never deploy an individual musician alone on the highway; you always deploy the bus (Pod)."
                        adaptationReason = "Explicit request for analogy; ground Kubernetes Pod concept in an intuitive physical model."
                    }

                    "CROSS_DOMAIN_TRANSITION" -> {
                        conversation.add(DatasetTurn("user", "Explain how quicksort partitions elements in memory."))
                        conversation.add(DatasetTurn("assistant", "Quicksort uses a pivot to partition elements in-place with \$O(n \\log n)\$ average time."))
                        userMessage = "How do I configure Prometheus to monitor the memory and CPU of this service in production? Var $v"
                        userState = "IMPLEMENTING"
                        explanationLevel = 4
                        expectedReasoning = "Seamlessly transition from algorithm theory to DevOps observability."
                        idealResponse = "In production, expose JVM metrics via Micrometer / Prometheus endpoint, then add a scrape job in `prometheus.yml`:\n```yaml\nscrape_configs:\n  - job_name: 'kotlin-service'\n    metrics_path: '/actuator/prometheus'\n    scrape_interval: 15s\n    static_configs:\n      - targets: ['app-service:8080']\n```\nMonitor key metrics: `jvm_memory_used_bytes{area=\"heap\"}` and `system_cpu_usage`."
                        adaptationReason = "Seamless cross-domain shift from DSA to DevOps in a single chat."
                    }

                    "WHY_VS_HOW_REASONING" -> {
                        userMessage = "Why do distributed databases choose Raft over Paxos? Var $v"
                        userState = "LEARNING"
                        explanationLevel = 4
                        expectedReasoning = "Explain understandability, formal leader election separation, and state machine replication."
                        idealResponse = "Distributed databases (like etcd, CockroachDB, TiKV) choose **Raft** primarily because Paxos is notoriously difficult to understand and implement correctly in production. Raft decomposes consensus into three independent, formal subproblems:\n1. **Leader Election:** Strong, unambiguous leader with randomized election timeouts.\n2. **Log Replication:** Strict prefix-matching invariant ensuring logs only flow from leader to followers.\n3. **Safety:** A leader is never elected unless it holds all committed entries."
                        adaptationReason = "Conceptual 'Why' query; focus on architectural rationale and engineering trade-offs."
                    }

                    "IMPLEMENTATION_AFTER_CONCEPT" -> {
                        conversation.add(DatasetTurn("user", "What is an LRU Cache?"))
                        conversation.add(DatasetTurn("assistant", "An LRU cache evicts the least recently used element when capacity is reached."))
                        userMessage = "Okay, now show me the Kotlin implementation using a HashMap and Doubly Linked List. Var $v"
                        userState = "IMPLEMENTING"
                        explanationLevel = 4
                        expectedReasoning = "Provide clean DoublyLinkedList + HashMap LRU implementation in Kotlin."
                        idealResponse = "Here is an \$O(1)\$ LRU Cache in idiomatic Kotlin:\n```kotlin\nclass LRUCache(private val capacity: Int) {\n    private class Node(var key: Int = 0, var value: Int = 0, var prev: Node? = null, var next: Node? = null)\n    \n    private val map = HashMap<Int, Node>()\n    private val head = Node()\n    private val tail = Node()\n    \n    init {\n        head.next = tail\n        tail.prev = head\n    }\n    \n    fun get(key: Int): Int {\n        val node = map[key] ?: return -1\n        moveToHead(node)\n        return node.value\n    }\n    \n    fun put(key: Int, value: Int) {\n        val existing = map[key]\n        if (existing != null) {\n            existing.value = value\n            moveToHead(existing)\n        } else {\n            if (map.size >= capacity) {\n                val lru = tail.prev!!\n                removeNode(lru)\n                map.remove(lru.key)\n            }\n            val newNode = Node(key, value)\n            map[key] = newNode\n            addNode(newNode)\n        }\n    }\n    \n    private fun addNode(node: Node) {\n        node.next = head.next\n        node.prev = head\n        head.next?.prev = node\n        head.next = node\n    }\n    \n    private fun removeNode(node: Node) {\n        node.prev?.next = node.next\n        node.next?.prev = node.prev\n    }\n    \n    private fun moveToHead(node: Node) {\n        removeNode(node)\n        addNode(node)\n    }\n}\n```"
                        adaptationReason = "Transition from concept to implementation; provide complete \$O(1)\$ code structure."
                    }

                    "APPROACH_VERIFICATION" -> {
                        userMessage = "Is my approach correct: to find the middle of a linked list, I use slow and fast pointers? Var $v"
                        userState = "VERIFYING"
                        explanationLevel = 1
                        expectedReasoning = "Validate the approach warmly and confirm time/space complexity."
                        idealResponse = "Yes, your approach is 100% optimal! By moving `fast` 2 steps and `slow` 1 step, `slow` lands on the exact middle when `fast` reaches the end. This solves the problem in a single pass (\$O(n)\$ time) with \$O(1)\$ auxiliary space."
                        adaptationReason = "Verifying correctness; confirm accuracy immediately with clear complexity validation."
                    }

                    "MENTOR_DECISION_GUIDANCE" -> {
                        userMessage = "Should our startup build a microservices architecture from day one? Var $v"
                        userState = "PLANNING"
                        explanationLevel = 4
                        expectedReasoning = "Provide pragmatic mentor guidance: advocate modular monolith first, explain trade-offs."
                        idealResponse = "Almost certainly **no**. Start with a clean **Modular Monolith** first.\n\n**Why:**\n1. **Boundary Uncertainty:** Early startups pivot frequently. In a monolith, refactoring domain boundaries is a simple code change. In microservices, it requires database migrations, network protocol updates, and multi-repo deployment changes.\n2. **Operational Overhead:** Microservices introduce distributed tracing, service discovery, eventual consistency, and network failure modes before you have validated product-market fit.\n\nBuild clear module boundaries in a single deployable unit. Extract services only when team size or scaling bottlenecks demand it."
                        adaptationReason = "Mentor guidance; offer pragmatic engineering wisdom and challenge premature optimization."
                    }

                    else -> {
                        userMessage = "Explain how to handle error scenarios in software systems ($catName). Var $v"
                        userState = "LEARNING"
                        explanationLevel = 4
                        expectedReasoning = "Provide structured guidance on failure domains and resilient recovery."
                        idealResponse = "Resilient error handling requires three layers: fast local validation, structured domain error types (sealed classes / Result types), and graceful degradation with circuit breakers for external dependencies."
                        adaptationReason = "Standard systems reasoning in $catName."
                    }
                }

                examples.add(
                    FineTuningExample(
                        id = id,
                        category = catName,
                        domain = domain,
                        task = task,
                        userState = userState,
                        explanationLevel = explanationLevel,
                        conversation = conversation,
                        userMessage = userMessage,
                        expectedReasoningApproach = expectedReasoning,
                        idealResponse = idealResponse,
                        adaptationReason = adaptationReason,
                        antiPatterns = antiPatterns
                    )
                )
            }
        }

        // Serialize to JSONL
        val jsonLines = examples.map { kotlinx.serialization.json.Json.encodeToString(FineTuningExample.serializer(), it) }

        // Validate
        val report = validator.validateJsonlLines(jsonLines)
        assertEquals(0, report.invalidExamples)
        assertEquals(338, report.validExamples)
        assertTrue(report.multiTurnCount > 50)
        assertEquals(26, report.categoryCounts.size)

        // Save to file for offline training and evaluation
        val outputDir = File("build/dataset").also { it.mkdirs() }
        val outputFile = File(outputDir, "vivva_training_dataset.jsonl")
        outputFile.writeText(jsonLines.joinToString("\n"))

        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 50000)
    }
}
