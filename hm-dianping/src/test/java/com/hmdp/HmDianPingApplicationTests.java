package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorkder() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(500);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id=" + id);
            }
            latch.countDown();
        };

        long startTime = System.currentTimeMillis();
        for (int j = 0; j < 500; j++) {
            es.submit(task);
        }
        latch.await();
        long endTime = System.currentTimeMillis();
        System.out.println("time=" + (endTime - startTime) );
    }


}
