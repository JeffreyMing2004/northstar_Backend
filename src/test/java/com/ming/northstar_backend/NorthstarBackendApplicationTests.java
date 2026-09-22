package com.ming.northstar_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NorthstarBackendApplicationTests {

    @Test
    void contextLoads() {
        // Disabled: requires MySQL + Redis connection
    }
}
