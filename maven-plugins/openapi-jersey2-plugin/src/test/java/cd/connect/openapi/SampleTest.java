package cd.connect.openapi;

import org.junit.Test;
import org.openapitools.codegen.OpenAPIGenerator;

import java.util.Arrays;

public class SampleTest {
  @Test
  public void runGenerator() {
    String location = getClass().getResource("/sample2.yaml").getFile();
    OpenAPIGenerator.main(Arrays.asList("generate",
      "--input-spec", location,
      "--generator-name", "jersey2-api",
      "--additional-properties", "server-security",
      "--additional-properties", "x-no-copy",
//      "--api-package", "api",
//      "--model-package", "model",
      "--output", "target/" + getClass().getSimpleName())
      .toArray(new String[0])); }

}
