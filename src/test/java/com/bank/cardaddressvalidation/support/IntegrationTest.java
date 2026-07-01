package com.bank.cardaddressvalidation.support;

import com.bank.cardaddressvalidation.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-context integration test wired to real Postgres and Redis via
 * Testcontainers ({@code @ServiceConnection}). One shared context, cached and
 * reused across all integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
