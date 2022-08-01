package com.hmdp.service.impl;

import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }

        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        // 获取锁
//        boolean isLock = lock.tryLock(1200);
        boolean isLock = lock.tryLock();

        // 判断获取锁是否成功
        if(!isLock) {
            // 获取锁失败，返回错误信息或重试
            return Result.fail("不允许重复下单");
        }

        try {
//        synchronized (userId.toString().intern()) {
            // 获取当前对象的代理对象 确保事务不失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // this.createVoucherOrder(voucherId) 非代理对象，因此 @Transactional失效
            return proxy.createVoucherOrder(voucherId);
//        }
        } finally {
            lock.unlock();
        }
    }

    //  @Transactional针对代理对象
    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5. 一人一单
        Long userId = UserHolder.getUser().getId();

        // 5.1 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        // 5.2 判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过了");
        }


        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存不足");
        }


        // 6.创建订单
        VoucherOrder order = new VoucherOrder();
        // 6.1 订单id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        // 6.2 用户id
        order.setUserId(userId);
        // 6.3 代金券id
        order.setVoucherId(voucherId);

        save(order);

        // 7.返回订单信息
        return Result.ok(orderId);

    }
}
