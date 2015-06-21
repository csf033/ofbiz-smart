package org.huihoo.ofbiz.smart.entity;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.huihoo.ofbiz.smart.base.pool.BoundedBlockingPool;
import org.huihoo.ofbiz.smart.base.pool.GenericPool;
import org.huihoo.ofbiz.smart.base.util.Log;

/**
 * <p>
 * 一个简单的数据源实现
 * </p>
 * <p>
 * 使用示例:
 * <pre style='border:solid thin;padding;1ex;'>
 *   SimpleDataSource dataSource = new SimpleDataSource();
 *   <code style="color:#0c0">//... 设置驱动名称</code>
 *   dataSource.setDriverClassName("org.h2.Driver");
 *   <code style="color:#0c0">//... 设置连接字符串</code>
 *   dataSource.setUrl("jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1");
 *   <code style="color:#0c0">//... 设置其它配置项</code>
 *   dataSource.setConnectionProperties("username=sa;password=");
 *   <code style="color:#0c0">//... 设置连接数</code>
 *   dataSource.setMaxConnections(10);
 *   <code style="color:#0c0">//... 获取连接</code>
 *   Connection con = dataSource.getConnection();
 *   <code style="color:#0c0">//... 接下来，你自己的操作.:)</code>
 * </pre>
 * </p>
 * @author huangbaihua
 * @version 1.0
 * @since 1.0
 */
public class SimpleDataSource implements DataSource, AutoCloseable {
  public static final String tag = SimpleDataSource.class.getName();
  /** 数据源 */
  private volatile DataSource dataSource = null;
  /** 日志处理 */
  private volatile PrintWriter logWriter = new PrintWriter(new OutputStreamWriter(System.out,StandardCharsets.UTF_8));
  /** 驱动名称 */
  private String driverClassName = null;
  /** 数据库驱动 */
  private Driver driver = null;
  /** 连接的用户名*/
  private String username;
  /** 连接的用户密码 */
  private String password;
  /** 连接字符串 */
  private String url = null;
  /** 最大连接数，默认32个 */
  private int maxConnections = 32;
  /** 数据源是否关闭标识，默认<code>false</code>*/
  private boolean closed = false;
  /** 数据源属性配置 */
  private Properties props;
  /** 对象池，用来保存数据库连接 */
  private volatile GenericPool<Connection> pool;
  /** JDBC连接工厂，用来创建JDBC连接 */
  private volatile JdbcConnectionFactory connectionFactory;
  /** JDBC连接验证*/
  private volatile JdbcConnectionValidator jdbcConnectionValidator;
  
  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return createDataSource().getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {
    createDataSource().setLogWriter(out);
    this.logWriter = out;
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    throw new UnsupportedOperationException("Not supported by SimpleDataSource");
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    throw new UnsupportedOperationException("Not supported by SimpleDataSource");
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException("SimpleDataSource is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return createDataSource().getConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    throw new UnsupportedOperationException("Not supported by SimpleDataSource");
  }


  @Override
  public void close() throws Exception {
    pool.shutDown();
    closed = true;
  }

  
  /**
   * 创建数据源
   * @return  创建好的数据源
   * @throws SQLException
   */
  protected DataSource createDataSource() throws SQLException {
    if (closed) throw new SQLException("The Data source is closed.");

    if (dataSource != null) {
      return dataSource;
    }

    synchronized (this) {
      if (dataSource != null) return dataSource;

      Driver driverToUse = this.driver;
      
      if (driverToUse == null) {
        Class<?> driverFromCCL = null;
        if (driverClassName != null) {
          try {
            driverFromCCL = Class.forName(driverClassName);
          } catch (ClassNotFoundException e) {
            Log.e(e, tag, "Cannot load JDBC driver class " + driverClassName);
            throw new SQLException(e);
          }
        }

        try {
          if (driverFromCCL == null) {
            driverToUse = DriverManager.getDriver(url);
          } else {
            driverToUse = (Driver) driverFromCCL.newInstance();
            if (!driverToUse.acceptsURL(url)) {
              throw new SQLException("No suitable driver", "08001");
            }
          }
        } catch (Exception e) {
          Log.e(e, tag, "Cannot create JDBC driver of class %s for connection url %s",
                  driverClassName != null ? driverClassName : "", url);
          throw new SQLException(e);
        }
        
        this.driver = driverToUse;
      }


      String user = username;
      if(user != null){
        props.put("username", user);
      }else{
        Log.w(tag, "DataSource configured without a 'username'");
      }
      
      String pwd = password;
      if(pwd != null){
        props.put("password", pwd);
      }else{
        Log.w(tag, "DataSource configured without a 'password'");
      }
      
      try {
        connectionFactory = new JdbcConnectionFactory(driver, url, props);
        jdbcConnectionValidator = new JdbcConnectionValidator();
        pool = new BoundedBlockingPool<>(maxConnections, jdbcConnectionValidator, connectionFactory);
        dataSource = new PooledDataSource<>(pool);
        dataSource.setLogWriter(logWriter);
      } catch (SQLException e) {
        Log.e(e,tag, "Cannot create datasource.");
        throw new SQLException(e);
      }

      return dataSource;
    }
  }



  public String getDriverClassName() {
    return driverClassName;
  }

  public Driver getDriver() {
    return driver;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getUrl() {
    return url;
  }

  public Properties getProps() {
    return props;
  }

  public void setDriverClassName(String driverClassName) {
    this.driverClassName = driverClassName;
  }

  public void setDriver(Driver driver) {
    this.driver = driver;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }
  
  /**
   * <p>
   *   设置数据库连接属性<br/>
   *   字符串格式为: [key=value;]* 
   * </p>
   * @param connectionProperties
   */
  public void setConnectionProperties(String connectionProperties){
    if (connectionProperties == null) {
      throw new NullPointerException("connectionProperties is null");
    }
  
    String[] entries = connectionProperties.split(";");
    Properties properties = new Properties();
    for (String entry : entries) {
        if (entry.length() > 0) {
            int index = entry.indexOf('=');
            if (index > 0) {
                String name = entry.substring(0, index);
                String value = entry.substring(index + 1);
                properties.setProperty(name, value);
            } else {
                properties.setProperty(entry, "");
            }
        }
    }
    this.props = properties;
  }

}