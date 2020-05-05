###  MySQL高级—SpringBoot+MyBatis+Sharding-JDBC实现MySQL分库分表





####  一、什么是Sharding-JDBC

Sharding-JDBC官方文档：https://shardingsphere.apache.org/document/current/cn/overview/

<center><img src="http://image.easyblog.top/1588587147236c6ea922f-fae8-48d1-a41a-06061abb8670.png" style="width:85%;" /></center>

Sharding-JDBC定位为轻量级Java框架，在Java的JDBC层提供的额外服务。 它使用客户端直连数据库，以jar包形式提供服务，无需额外部署和依赖，可理解为增强版的JDBC驱动，完全兼容JDBC和各种ORM框架。

- 适用于任何基于JDBC的ORM框架，如：JPA, Hibernate, Mybatis, Spring JDBC Template或直接使用JDBC。
- 支持任何第三方的数据库连接池，如：DBCP, C3P0, BoneCP, Druid, HikariCP等。
- 支持任意实现JDBC规范的数据库。目前支持MySQL，Oracle，SQLServer，PostgreSQL以及任何遵循SQL92标准的数据库。



#### 二、数据库分库分表和读写分离基本概念扫盲

通过分库和分表进行数据的拆分来使得各个表的数据量保持在阈值以下（一般来讲，单一数据库实例的数据的阈值在1TB之内，是比较合理的范围），以及对流量进行疏导应对高访问量，是应对高并发和海量数据系统的有效手段。 数据分片的拆分方式又分为垂直分片和水平分片。

##### 1、垂直拆分

按照业务拆分的方式称为垂直分片，又称为纵向拆分，它的**核心理念是专库专用**。在拆分之前，一个数据库由多个数据表构成，每个表对应着不同的业务。而拆分之后，则是按照业务将表进行归类，分布到不同的数据库中，从而将压力分散至不同的数据库。如下图所示：

<center><img src="http://image.easyblog.top/1588588369693b492d10a-353e-41f1-9f2d-3730de08c0d6.png" style="width:75%;" /></center>



垂直拆分可以缓解数据量和访问量带来的问题，但无法根治。如果垂直拆分之后，表中的数据量依然超过单节点所能承载的阈值，则需要水平拆分来进一步处理。

##### 2、水平拆分

水平分片又称为横向拆分。 相对于垂直分片，它不再将数据根据业务逻辑分类，而是通过某个字段（或某几个字段），根据某种规则将数据分散至多个库或表中，每个分片仅包含数据的一部分。 例如：根据主键分片，偶数主键的记录放入0库（或表），奇数主键的记录放入1库（或表），如下图所示：

<center><img src="http://image.easyblog.top/158858854357802a9a8f0-ff2d-4e9c-9f92-7558e0c4c162.png" style="width:75%;" /></center>

水平分片从理论上突破了单机数据量处理的瓶颈，并且扩展相对自由，是分库分表的标准解决方案。



##### 3、读写分离

通过一主多从的配置方式，可以将查询请求均匀的分散到多个数据副本，能够进一步的提升系统的处理能力。 使用多主多从的方式，不但能够提升系统的吞吐量，还能够提升系统的可用性，可以达到在任何一个数据库宕机，甚至磁盘物理损坏的情况下仍然不影响系统的正常运行。

与将数据根据分片键打散至各个数据节点的水平分片不同，读写分离则是根据SQL语义的分析，将读操作和写操作分别路由至主库与从库。

<center><img src="http://image.easyblog.top/15885891824250097748a-2288-4191-8b3f-e506dcf2d131.png" style="width:75%"></center>

读写分离的数据节点中的数据内容是一致的，而水平分片的每个数据节点的数据内容却并不相同。将水平分片和读写分离联合使用，能够更加有效的提升系统性能。



分库分表和读写分离都可以提高系统的性能和稳定性，但是他们都有着同样的问题，使得应用开发和运维人员对数据库的操作和运维变得更加复杂。因此使用sharding-jdbc可以**透明化分库分表和读写分离所带来的影响，让我们像使用一个数据库一样使用主从数据库集群**



#### 三、配置实例

接下来，我就参考官方文档，基于SpringBoot 2.1.0.RELEASE，与Sharding-JDBC 4.0.0-RC1版本，写了一个demo，实现数据分片的基本功能。

#####  1、建库、建表

这里在同一台MySQL服务器上建立了两个数据库实例：ds0、ds1

然后在ds0上创建：t_order0、t_order_item0、t_config

然后在ds1上创建：t_order1、t_order_item1、t_config

```sql
-- 创建数据源
CREATE SCHEMA IF NOT EXISTS ds0;
CREATE SCHEMA IF NOT EXISTS ds1;
-- ds0建表
DROP TABLE IF EXISTS ds0.t_order0;
CREATE TABLE ds0.t_order0 (
	order_id INT PRIMARY KEY, 
	user_id INT NOT NULL, 
	config_id INT NOT NULL, 
	remark VARCHAR(50),
	create_time TIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
DROP TABLE IF EXISTS ds0.t_order1;
CREATE TABLE ds0.t_order1 (
	order_id INT PRIMARY KEY, 
	user_id INT NOT NULL, 
	config_id INT NOT NULL, 
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
 
DROP TABLE IF EXISTS ds0.t_order_item0;
CREATE TABLE ds0.t_order_item0 (
	item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
	order_id BIGINT NOT NULL,
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
DROP TABLE IF EXISTS ds0.t_order_item1;
CREATE TABLE ds0.t_order_item1 (
	item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
	order_id BIGINT NOT NULL,
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
-- 广播表
DROP TABLE IF EXISTS ds0.t_config;
CREATE TABLE IF NOT EXISTS ds0.t_config (
	id INT PRIMARY KEY, 
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
 
-- ds1建表
DROP TABLE IF EXISTS ds1.t_order0;
CREATE TABLE ds1.t_order0 (
	order_id INT PRIMARY KEY, 
	user_id INT NOT NULL, 
	config_id INT NOT NULL, 
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
DROP TABLE IF EXISTS ds1.t_order1;
CREATE TABLE ds1.t_order1 (
	order_id INT PRIMARY KEY, 
	user_id INT NOT NULL, 
	config_id INT NOT NULL, 
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
 
DROP TABLE IF EXISTS ds1.t_order_item0;
CREATE TABLE ds1.t_order_item0 (
	item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
	order_id BIGINT NOT NULL,
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
DROP TABLE IF EXISTS ds1.t_order_item1;
CREATE TABLE ds1.t_order_item1 (
	item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
	order_id BIGINT NOT NULL,
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
-- 广播表
DROP TABLE IF EXISTS ds1.t_config;
CREATE TABLE ds1.t_config (
	id INT PRIMARY KEY, 
	remark VARCHAR(50),
	create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
	last_modify_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```



##### 2、创建SpringBoot工程并配置分片

创建Maven工程，这里SpringBoot使用的是2.1.13.RELEASE版本（为了与实际工作中的项目一致），ORM使用的MyBatis，Sharding-JDBC使用的是当前最新版本4.0.0-RC1。

######  （1）完整maven依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.1.13.RELEASE</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>top.easyblog</groupId>
    <artifactId>mysql_sharding-jdbc</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>mysql_sharding-jdbc</name>
    <description>Demo project for Spring Boot</description>

    <properties>
        <java.version>1.8</java.version>
        <sharding-sphere.version>4.0.0-RC1</sharding-sphere.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.1.2</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!--sharding-jdbc for SpringBoot-->
        <dependency>
            <groupId>org.apache.shardingsphere</groupId>
            <artifactId>sharding-jdbc-spring-boot-starter</artifactId>
            <version>${sharding-sphere.version}</version>
        </dependency>
        <!--sharding-core-->
        <dependency>
            <groupId>org.apache.shardingsphere</groupId>
            <artifactId>sharding-core-common</artifactId>
            <version>${sharding-sphere.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <!-- mybatis generator 自动生成代码插件 -->
            <plugin>
                <groupId>org.mybatis.generator</groupId>
                <artifactId>mybatis-generator-maven-plugin</artifactId>
                <version>1.3.6</version>
                <configuration>
                    <configurationFile>${basedir}/src/main/resources/generator/generatorConfig.xml</configurationFile>
                    <overwrite>true</overwrite>
                    <verbose>true</verbose>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

###### （2）实体类

**Config**

```
@Setter
@Getter
@ToString
public class Config {

    private Integer id;

    private String remark;

    private Date createTime;

    private Date lastModifyTime;
}
```

**Order**

```
@Getter
@Setter
@ToString
public class Order {

    private Integer orderId;

    private Integer userId;

    private Integer configId;

    private String remark;

    private Date createTime;

    private Date lastModifyTime;
}
```

**OrderItem**

```
@Getter
@Setter
@ToString
public class OrderItem {

    private Long itemId;

    private Integer orderId;

    private String remark;

    private Date createTime;

    private Date lastModifyTime;
}
```



###### （3）Mapper

**ConfigMapper**

```
@Mapper
public interface ConfigMapper {
    @Insert("insert into t_config(id,remark) values(#{id},#{remark})")
    Integer save(Config config);

    @Select("select * from t_config  where id = #{id}")
    Config selectById(Integer id);
}
```

**OrderItemMapper**

```
@Mapper
public interface OrderItemMapper {
    @Insert("insert into t_order_item(order_id,remark) values(#{orderId},#{remark})")
    Integer save(OrderItem orderItem);
}
```

**OrderMapper**

```
@Mapper
public interface OrderMapper {

    @Insert("insert into t_order(order_id,user_id,config_id,remark) values(#{orderId},#{userId},#{configId},#{remark})")
    Integer save(Order order);

    @Select("select order_id orderId, user_id userId, config_id configId, remark from t_order  " +
            "where user_id = #{userId} and order_id = #{orderId}")
    Order selectBySharding(Integer userId, Integer orderId);

    @Select("select o.order_id orderId, o.user_id userId, o.config_id configId, o.remark from " +
            "t_order o inner join t_order_item i on o.order_id = i.order_id " +
            "where o.user_id =#{userId} and o.order_id =#{orderId}")
    List<Order> selectOrderJoinOrderItem(Integer userId, Integer orderId);

    @Select("select  o.order_id orderId, o.user_id userId, o.config_id configId, o.remark " +
            "from t_order o inner join t_config c on o.config_id = c.id " +
            "where o.user_id =#{userId} and o.order_id =#{orderId}")
    List<Order> selectOrderJoinConfig(Integer userId, Integer orderId);
}
```



###### （4）application.yml

Sharding-JDBC提供了4种配置方式（Java类配置、yml配置文件配置、SpringBoot配置和Spring命名空间配置），用于不同的使用场景。通过不同的配置方式，可以灵活的使用分库分表、读写分离以及分库分表 + 读写分离共用。

```xml
spring:
  application:
    name: sharding-jdbc-test
  profiles:
    active: sharding


# mybatis  configuration
mybatis:
  config-location: classpath:/mybatis/mybatis-config.xml
  # mapper-locations: classpath:/mybatis/mapper/*.xml

# log configuration
logging:
  config: classpath:logback-spring.xml
```



###### （5）application-sharding.xml （实现分片配置的重点）

```xml
spring:
  shardingsphere:
    datasource:
      #定义两个数据源：ds0和ds1，名字可以随便起
      names: ds0,ds1
      ds0:
        #一个类似于DruidDataSource的数据库连接池
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://192.168.92.131:3307/ds0?useUnicode=true&characterEncoding=utf8&tinyInt1isBit=false&useSSL=false&serverTimezone=GMT
        username: root
        password: 123456
      ds1:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://192.168.92.131:3307/ds1?useUnicode=true&characterEncoding=utf8&tinyInt1isBit=false&useSSL=false&serverTimezone=GMT
        username: root
        password: 123456
    sharding:
      tables:
        # 配置分库分表的表 ，其中key 就是表名 value 就写一些配置
        t_order:
          actual-data-nodes: ds$->{0..1}.t_order$->{0..1}
          ## 指定分库规则
          database-strategy:
            inline:
              sharding-column: user_id
              algorithm-expression: ds$->{user_id % 2}
          ## 指定分表规则
          table-strategy:
            inline:
              sharding-column: order_id
              algorithm-expression: t_order$->{order_id % 2}

        t_order_item:
          actual-data-nodes: ds$->{0..1}.t_order_item$->{0..1}
          ## 通过hint方式自定义分库规则
          database-strategy:
            hint:
              algorithmClassName: top.easyblog.sharding.demo.hint.HintSharding
          ## 指定分表规则
          table-strategy:
            inline:
              sharding-column: order_id
              algorithm-expression: t_order_item$->{order_id % 2}
          ## 生成分布式主键
          key-generator:
            column: item_id
            type: SNOWFLAKE     #雪花算法

      ## 绑定主表与子表，避免关联查询导致的全数据源路由
      binding-tables: t_order,t_order_item

      ## 配置广播表：以广播的形式保存（如果只涉及查询的话可以不配置，会随机取一个数据源）
      broadcast-tables: t_config

    ## 打印sql
    props:
      sql:
        show: true
```



###### （6）自定义分库规则 top.easyblog.sharding.demo.hint.HintSharding

```
public class HintSharding implements HintShardingAlgorithm<Integer> {
    /**
     *
     * @author huangxin
     * @date 2019-09-22 12:23
     * @param availableTargetNames 分片表名的集合
     * @param hintShardingValue 分片键集合
     * @return java.util.Collection<java.lang.String>
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, HintShardingValue<Integer> hintShardingValue) {
        Collection<String> result = new ArrayList<>();
        for (String each : availableTargetNames) {
            for (Integer value : hintShardingValue.getValues()) {
                if (each.endsWith(String.valueOf(value % 2))) {
                    System.out.println("*********************");
                    result.add(each);
                }
            }
        }
        return result;
    }
}
```



###### （7）Service

**OrderServiceImpl**

```
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
    public Integer saveConfig(Config config) {
        return configMapper.save(config);
    }

    @Override
    public Config selectConfig(Integer id) {
        return configMapper.selectById(id);
    }
}
```



#####  3、测试

（1）向t_order_item和t_order表中插入数据

```java
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AppTest {

    @Resource
    private OrderService orderService;

    /**
     * 批量插入：偶数ID的在一个表中，奇数ID的在另一个表中
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
}
```
插入测试的结果：

<center>
<img src="http://image.easyblog.top/1588608888129f138e054-a28a-47a0-a5b4-c5955f116f4a.png" style="width:75%">
<img src="http://image.easyblog.top/1588608993392e6d99f28-0588-4bd6-8e89-343ac216e6d5.png" style="width:75%"></center>



查询测试：

（2）查询OrderItem

分别从两个不同的数据表中查询到数据后，由sharding-JDBC合并数据之后返回

```java
@Test                                                                    
public void testSelectItem(){                                            
    //从两个数据表中查询数据，由sharding-jdbc汇总数据之后返回                                 
    System.out.println(orderService.selectItemById(464201566069456897L));
    System.out.println(orderService.selectItemById(464201566786682880L));
}                                                                        
```

![](http://image.easyblog.top/1588647348555f658eb36-48df-4668-8d4f-812ad40f7fb8.png)



（3）通过UserId查询Order

分别从两个不同的数据表中查询到数据后，由sharding-JDBC合并数据之后返回

```java
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
```

![](http://image.easyblog.top/158864747157952862fa4-2f30-418d-b0eb-1e085e1f2d61.png)



（4）关联查询（join）测试

```java
 @Test
 public void testSelectOrderJoinOrderItem() {
     // 指定了子表分片规则
     List<Order> o1 = orderService.selectOrderJoinOrderItem(12, 1002);
     System.out.println(o1);
     // 未指定子表分片规则：导致子表的全路由
     List<Order> o2 = orderService.selectOrderJoinOrderItemNoSharding(12, 1002);
     System.out.println(o2);
 }
```

![](http://image.easyblog.top/1588647911765deef70be-8064-4e0e-892b-12e4cbac37bb.png)



（5）向Config表插入记录并查询


``` java
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
     * @author huangxin
     * @date 2019-09-20 11:23
     */
    @Test
    public void testSelectConfig() {
        Config config1 = orderService.selectConfig(2);
        System.out.println(config1);

        Config config2 = orderService.selectConfig(7);
        System.out.println(config2);
    }
```

Config表没有做分片，保存了所有的数据，但是在查询的时候sharding-jdbc会随机选择一个数据表进行查询，因此下面这两条数据是来着同一个数据表的。

![](http://image.easyblog.top/1588648237572b8b948ff-0026-4c28-b25e-028d5344ce48.png)