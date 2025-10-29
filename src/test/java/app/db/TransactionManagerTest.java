package app.db;

import app.exception.DataAccessException;
import app.testutil.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionManagerTest {

    private TestDatabase testDatabase;
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() throws Exception {
        testDatabase = new TestDatabase();
        transactionManager = new TransactionManager(testDatabase.getDatabase());
    }

    @AfterEach
    void tearDown() {
        if (testDatabase != null) {
            testDatabase.close();
        }
    }

    @Test
    @DisplayName("読み取り専用トランザクションは相互にブロックしない")
    void readOnlyTransactionsDoNotBlockEachOther() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger concurrentCount = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();

        Callable<Void> task = () -> {
            readyLatch.countDown();
            startLatch.await();
            transactionManager.executeInReadOnlyTransaction(() -> {
                int current = concurrentCount.incrementAndGet();
                maxConcurrent.updateAndGet(prev -> Math.max(prev, current));
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DataAccessException("読み取り専用トランザクションが中断されました", e);
                } finally {
                    concurrentCount.decrementAndGet();
                }
                return null;
            });
            return null;
        };

        Future<Void> first = executor.submit(task);
        Future<Void> second = executor.submit(task);

        readyLatch.await();
        startLatch.countDown();

        first.get();
        second.get();
        executor.shutdown();

        assertThat(maxConcurrent.get()).isGreaterThanOrEqualTo(2);
    }
}
