package com.shreyas.saleslens;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.kafka.listener.auto-startup=false",
    "jwt.secret=test-secret-key-for-testing-purposes-only"
})
@ActiveProfiles("test")
class SaleslensApplicationTests {

    @MockitoBean
    private DataSource dataSource;

    @MockitoBean
    private org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisConnectionFactory;

    @BeforeEach
    void setUp() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(dataSource.getConnection()).thenReturn(conn);
    }

	@Test
	void contextLoads() {
	}

}
