package com.example.ticketbooking.config;

import static org.assertj.core.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Configuration/driver parsing only. Never opens a connection or executes SQL. */
class ProductionProfileTest {
    @Test
    void standardDatasourceVariablesOverrideLegacyLocalVariables() throws Exception {
        Map<String, Object> variables = credentials();
        variables.put("DB_URL", "jdbc:postgresql://localhost:5432/legacy");
        variables.put("DB_USERNAME", "legacy");
        variables.put("DB_PASSWORD", "legacy-test-placeholder");
        var datasource = datasource(environment(false, variables));
        assertThat(datasource.getUrl()).isEqualTo(variables.get("SPRING_DATASOURCE_URL"));
        assertThat(datasource.getUsername()).isEqualTo("neondb_owner");
        assertThat(datasource.getPassword()).isEqualTo("test-placeholder-only");
    }

    @Test
    void localConfigurationStillAcceptsLegacyDbVariables() throws Exception {
        var datasource = datasource(environment(false, Map.of("DB_URL", "jdbc:postgresql://localhost:5432/local_test",
                "DB_USERNAME", "local_user", "DB_PASSWORD", "local-test-placeholder")));
        assertThat(datasource.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/local_test");
        assertThat(datasource.getUsername()).isEqualTo("local_user");
    }

    @Test
    void productionDoesNotSilentlyFallBackToLocalCredentials() throws Exception {
        var environment = environment(true, Map.of("DB_URL", "jdbc:postgresql://localhost:5432/wrong",
                "DB_USERNAME", "wrong", "DB_PASSWORD", "unused-test-placeholder"));
        assertThatThrownBy(() -> environment.getRequiredProperty("spring.datasource.url"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SPRING_DATASOURCE_URL");
    }

    @Test
    void renderPortTakesPrecedenceOverServerPortAndBindsAllInterfaces() throws Exception {
        Map<String, Object> variables = credentials();
        variables.put("PORT", "12345");
        variables.put("SERVER_PORT", "8080");
        var environment = environment(true, variables);
        assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(12345);
        assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(environment(true, credentials()).getProperty("server.port", Integer.class)).isEqualTo(10000);
    }

    @Test
    void driverUnderstandsTlsAndChannelBindingDefaultsWithoutOpeningSocket() throws Exception {
        var environment = environment(true, credentials());
        var pool = Binder.get(environment).bind("spring.datasource.hikari", Bindable.of(HikariConfig.class)).get();
        var properties = Driver.parseURL(datasource(environment).getUrl(), pool.getDataSourceProperties());
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("sslmode")).isEqualTo("require");
        assertThat(properties.getProperty("channelBinding")).isEqualTo("require");
        assertThat(properties.getProperty("connectTimeout")).isEqualTo("15");
        assertThat(properties.getProperty("socketTimeout")).isEqualTo("60");
        assertThat(properties.getProperty("user")).isNull();
        assertThat(properties.getProperty("password")).isNull();
        assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
        assertThat(pool.getMinimumIdle()).isZero();
    }

    @Test
    void migrationNeedsExplicitOptInAndHibernateNeverRecreatesSchema() throws Exception {
        var environment = environment(true, credentials());
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.flyway.validate-on-migrate", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        Map<String, Object> variables = credentials(); variables.put("SPRING_FLYWAY_ENABLED", "true");
        assertThat(environment(true, variables).getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
    }

    private Map<String, Object> credentials() {
        Map<String, Object> values = new HashMap<>();
        values.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://database.example.invalid:5432/neondb");
        values.put("SPRING_DATASOURCE_USERNAME", "neondb_owner");
        values.put("SPRING_DATASOURCE_PASSWORD", "test-placeholder-only");
        return values;
    }

    private DataSourceProperties datasource(StandardEnvironment environment) {
        return Binder.get(environment).bind("spring.datasource", Bindable.of(DataSourceProperties.class)).get();
    }

    private StandardEnvironment environment(boolean prod, Map<String, Object> variables) throws IOException {
        var environment = new StandardEnvironment();
        // Ignore the machine's DB/SMTP/JWT values and .env files in these pure configuration tests.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var loader = new YamlPropertySourceLoader();
        for (var source : loader.load("base", new ClassPathResource("application.yml"))) environment.getPropertySources().addLast(source);
        if (prod) {
            for (var source : loader.load("prod", new ClassPathResource("application-prod.yml"))) environment.getPropertySources().addFirst(source);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("test-input", variables));
        return environment;
    }
}
