package top.easyblog.sharding.demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import top.easyblog.sharding.demo.entity.Config;
import top.easyblog.sharding.demo.entity.Order;
import top.easyblog.sharding.demo.entity.OrderItem;
import top.easyblog.sharding.demo.service.OrderService;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/04/30 15:49
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AppTest {

    @Resource
    private OrderService orderService;

    /**
     * 路由保存
     *
     * @param
     * @return void
     * @author huangxin
     * @date 2019-09-20 11:36
     */
    @Test
    public void testSaveOrder() {
        for (int i = 0; i < 10000; i++) {
            Integer orderId = 2000 + i;
            Integer userId = 20 + i;

            Order o = new Order();
            o.setOrderId(orderId);
            o.setUserId(userId);
            o.setConfigId(i);
            o.setRemark("save.order");
            orderService.saveOrder(o);

            OrderItem oi = new OrderItem();
            oi.setOrderId(orderId);
            oi.setRemark("save.orderItem");
            orderService.saveOrderItem(oi, userId);
        }
    }

    @Test
    public void testSelectItem(){
        //从两个数据表中查询数据，由sharding-jdbc汇总数据之后返回
        System.out.println(orderService.selectItemById(464201566069456897L));
        System.out.println(orderService.selectItemById(464201566786682880L));
    }

    /**
     * 根据分片键查询
     *
     * @param
     * @return void
     * @author huangxin
     * @date 2019-09-20 11:26
     */
    @Test
    public void testSelectByUserId() {
        Integer userId = 12;
        Integer orderId = 1002;
        Order o1 = orderService.selectBySharding(userId, orderId);
        System.out.println(o1);

        userId = 17;
        orderId = 1007;
        Order o2 = orderService.selectBySharding(userId, orderId);
        System.out.println(o2);

    }

    /**
     * 与分片子表关联
     *
     * @param
     * @return void
     * @author huangxin
     * @date 2019-09-20 11:24
     */
    @Test
    public void testSelectOrderJoinOrderItem() {
        // 指定了子表分片规则
        List<Order> o1 = orderService.selectOrderJoinOrderItem(12, 1002);
        System.out.println(o1);
        // 未指定子表分片规则：导致子表的全路由
        List<Order> o2 = orderService.selectOrderJoinOrderItemNoSharding(12, 1002);
        System.out.println(o2);
    }

    /**
     * 与广播表关联
     *
     * @param
     * @return void
     * @author hujy
     * @date 2019-09-20 11:24
     */
    @Test
    public void testSelectOrderJoinConfig() {
        List<Order> o1 = orderService.selectOrderJoinConfig(12, 1002);
        System.out.println(o1);
        List<Order> o2 = orderService.selectOrderJoinConfig(17, 1007);
        System.out.println(o2);
    }

    /**
     * 广播表保存
     * 对所有数据源进行广播
     *
     * @param
     * @return void
     * @author huangxin
     * @date 2019-09-20 11:23
     */
    @Test
    public void testSaveConfig() {
        for (int i = 0; i < 10; i++) {
            Config config = new Config();
            config.setId(i);
            config.setRemark("config " + i);
            orderService.saveConfig(config);
        }
    }

    /**
     * 广播表查询
     * 随机选择数据源
     *
     * @param
     * @return void
     * @author hujy
     * @date 2019-09-20 11:23
     */
    @Test
    public void testSelectConfig() {
        Config config1 = orderService.selectConfig(2);
        System.out.println(config1);

        Config config2 = orderService.selectConfig(7);
        System.out.println(config2);
    }
}
