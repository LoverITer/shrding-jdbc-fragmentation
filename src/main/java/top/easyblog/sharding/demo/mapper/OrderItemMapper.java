package top.easyblog.sharding.demo.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.easyblog.sharding.demo.entity.OrderItem;

/**
 * Description
 *
 * @author hujy
 * @version 1.0
 * @date 2019-09-19 16:46
 */
@Mapper
public interface OrderItemMapper {
    @Insert("insert into t_order_item(order_id,remark) values(#{orderId},#{remark})")
    Integer save(OrderItem orderItem);

    @Select("select * from t_order_item where item_id=#{id}")
    OrderItem selectById(@Param(value = "id") Long id);
}
