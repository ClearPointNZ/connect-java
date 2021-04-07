package org.glassfish.jersey.logging;

import cd.connect.jersey.common.logging.JerseyFiltering;
import cd.connect.jersey.common.logging.JerseyFilteringConfiguration;
import com.google.common.collect.ImmutableList;
import org.fest.assertions.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

public class BaseFilteringLoggerTest {

    private ConcreteFilteringLogger filteringLogger;

    @Before
    public void init() {
        JerseyFilteringConfiguration jerseyFiltering = new JerseyFilteringConfiguration();
        filteringLogger = new ConcreteFilteringLogger(jerseyFiltering);
    }

    @Test
    public void testAuthorizationHeader_multiple() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("Authorization", ImmutableList.of("Bearer ABCDEFGHIJKL", "Bearer MNOPQRSTUV")); // <-- bad structure
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: Bearer AB..(OBFUSCATED)..L,Bearer M..(OBFUSCATED)\n");
    }

    /**
     * <p>This is where the typical form of <code>[type] [credentials]</code> is not present.</p>
     */

    @Test
    public void testAuthorizationHeader_corrupted() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("Authorization", "NOTVALID!"); // <-- bad structure
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: (OBFUSCATED)\n");
    }

    @Test
    public void testBasicAuthorizationHeader() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("Authorization", "Basic YW5keTpwYXNzd29yZA=="); // andy:password
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: Basic andy:(OBFUSCATED)\n");
    }

    /**
     * <p>This checks to see what happens if the Base64 is not valid.</p>
     */

    @Test
    public void testBasicAuthorizationHeader_corrupted() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("Authorization", "Basic !@%#^&!@#@($"); // <-- bad base64
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: Basic (CORRUPT-BASE64?)\n");
    }

    @Test
    public void testBearerAuthorizationHeader() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("Authorization", "Bearer ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: Bearer AB..(OBFUSCATED)..Z\n");
    }

    @Test
    public void testBearerAuthorizationHeader_short() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("Authorization", "Bearer ABCXYZ");
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: Bearer A..(OBFUSCATED)\n");
    }

    @Test
    public void testBearerAuthorizationHeader_veryShort() {
        // GIVEN
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.putSingle("Authorization", "Bearer AXYZ");
        StringBuilder result = new StringBuilder();

        // WHEN
        filteringLogger.printPrefixedHeaders(result, 123L, ">", headers);

        // THEN
        Assertions.assertThat(result.toString()).isEqualTo("123 >Authorization: Bearer (OBFUSCATED)\n");
    }

    /**
     * <p>This is just here so that the abstract superclass is able to be tested.</p>
     */

    public static class ConcreteFilteringLogger extends BaseFilteringLogger {

        public ConcreteFilteringLogger(JerseyFiltering jerseyFiltering) {
            super(jerseyFiltering);
        }

    }

}
