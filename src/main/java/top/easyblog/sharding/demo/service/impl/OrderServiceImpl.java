package top.easyblog.sharding.demo.service.impl;

import org.apache.shardingsphere.api.hint.HintManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.easyblog.sharding.demo.entity.Config;
import top.easyblog.sharding.demo.entity.Order;
import top.easyblog.sharding.demo.entity.OrderItem;
import top.easyblog.sharding.demo.mapper.ConfigMapper;
import top.easyblog.sharding.demo.mapper.OrderItemMapper;
import top.easyblog.sharding.demo.mapper.OrderMapper;
import top.easyblog.sharding.demo.service.OrderService;

import java.util.List;

/**
 * Description
 *
 * @author hujy
 * @version 1.0
 * @date 2019-09-18 10:47
 */
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ConfigMapper configMapper;

    @Override
    public Integer saveOrder(Order order) {
        return orderMapper.save(order);
    }

    @Override
    public Integer saveOrderItem(OrderItem orderItem, Integer userId) {
        try (HintManager hintManager = HintManager.getInstance()) {
            hintManager.addDatabaseShardingValue("t_order_item", userId);
            return orderItemMapper.save(orderItem);
        }
    }

    @Override
    public Order selectBySharding(Integer userId, Integer orderId) {
        return orderMapper.selectBySharding(userId, orderId);
    }

    @Override
    public List<Order> selectOrderJoinOrderItem(Integer userId, Integer orderId) {
        try (HintManager hintManager = HintManager.getInstance()) {
            hintManager.addDatabaseShardingValue("t_order_item", userId);
            return orderMapper.selectOrderJoinOrderItem(userId, orderId);
        }
    }

    @Override
    public List<Order> selectOrderJoinOrderItemNoSharding(Integer userId, Integer orderId) {
        return orderMapper.selectOrderJoinOrderItem(userId, orderId);
    }

    @Override
    public List<Order> selectOrderJoinConfig(Integer userId, Integer orderId) {
        return orderMapper.selectOrderJoinConfig(userId, orderId);
    }

    @Override
    public OrderItem selectItemById(Long id){
        return orderItemMapper.selectById(id);
    }

    @Override
    public Integer saveConfig(Config config) {
        return configMapper.save(config);
    }

    @Override
    public Config selectConfig(Integer id) {
        return configMapper.selectById(id);
    }
}
