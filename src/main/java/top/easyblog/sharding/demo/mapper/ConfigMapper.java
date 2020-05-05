package top.easyblog.sharding.demo.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.easyblog.sharding.demo.entity.Config;

/**
 * Description
 *
 * @author huangxin
 * @version 1.0
 * @date 2019-09-20 10:22
 */
@Mapper
public interface ConfigMapper {
    @Insert("insert into t_config(id,remark) values(#{id},#{remark})")
    Integer save(Config config);

    @Select("select * from t_config  where id = #{id}")
    Config selectById(Integer id);
}
