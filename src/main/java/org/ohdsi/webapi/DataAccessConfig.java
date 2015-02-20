package org.ohdsi.webapi;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 */
@Configuration
@EnableJpaRepositories
@EnableTransactionManagement
public class DataAccessConfig {
    
    @Autowired
    private Environment env;
    
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        
        String driver = this.env.getRequiredProperty("datasource.driverClassName");
        String url = this.env.getRequiredProperty("datasource.url");
        String user = this.env.getRequiredProperty("datasource.username");
        String pass = this.env.getRequiredProperty("datasource.password");
        boolean autoCommit = false;
        
        //pooling - currently issues with (at least) oracle with use of temp tables and "on commit preserve rows" instead of "on commit delete rows";
        //http://forums.ohdsi.org/t/transaction-vs-session-scope-for-global-temp-tables-statements/333/2
        /*final PoolConfiguration pc = new org.apache.tomcat.jdbc.pool.PoolProperties();
        pc.setDriverClassName(driver);
        pc.setUrl(url);
        pc.setUsername(user);
        pc.setPassword(pass);
        pc.setDefaultAutoCommit(autoCommit);*/
        
        //non-pooling
        //explicitly load driver (for cases when driver jar is not yet loaded - dependency outside of archive)
        try {
            Class<?> driverClass = Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        DataSource ds = new DriverManagerDataSource(url, user, pass);
        //note autocommit defaults vary across vendors. use provided @Autowired TransactionTemplate
        
        return ds;
        //return new org.apache.tomcat.jdbc.pool.DataSource(pc);        
    }
    
    @Bean
    public EntityManagerFactory entityManagerFactory() {
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(Boolean.getBoolean(this.env.getRequiredProperty("spring.jpa.show-sql")));
        //hibernate.dialect is resolved based on driver
        //vendorAdapter.setDatabasePlatform(hibernateDialect);
        
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("org.ohdsi.webapi");
        factory.setDataSource(primaryDataSource());
        factory.afterPropertiesSet();
        
        return factory.getObject();
    }
    
    @Bean
    @Primary
    //This is needed so that JpaTransactionManager is used for autowiring, instead of DataSourceTransactionManager
    public PlatformTransactionManager jpaTransactionManager() {//EntityManagerFactory entityManagerFactory) {
    
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory());
        return txManager;
    }
    
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(transactionManager);
        return transactionTemplate;
    }
}
