package com.ruoyi.ai.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ReadonlyDataSourceConfig {

    @Bean(name = "aiReadonlyJdbcTemplate")
    public JdbcTemplate aiReadonlyJdbcTemplate(
        @Value("${ai.datasource.url}") String url,
        @Value("${ai.datasource.username}") String username,
        @Value("${ai.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(4);
        ds.setReadOnly(true);
        ds.setConnectionTestQuery("SELECT 1");
        JdbcTemplate template = new JdbcTemplate(ds);
        template.setQueryTimeout(15);
        template.setMaxRows(200);
        return template;
    }
}
