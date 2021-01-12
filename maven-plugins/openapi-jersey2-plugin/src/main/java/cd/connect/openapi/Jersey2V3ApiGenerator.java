package cd.connect.openapi;


import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.AbstractJavaJAXRSServerCodegen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.openapitools.codegen.utils.StringUtils.camelize;

public class Jersey2V3ApiGenerator extends AbstractJavaJAXRSServerCodegen implements CodegenConfig {
  private static final Logger log = LoggerFactory.getLogger(Jersey2V3ApiGenerator.class);
  private static final String LIBRARY_NAME = "jersey2-api";
  private static final String JERSEY2_TEMPLATE_FOLDER = "jersey2-v3template";
  private static final String SERVICE_ADDRESS = "serviceAddress";
  private static final String SERVICE_NAME = "serviceName";
  private static final String SERVICE_PORT = "servicePort";
  private static final String SERVICE_DEFAULT_URL = "serviceDefaultUrl";

  public Jersey2V3ApiGenerator() {
    super();
    library = LIBRARY_NAME;
    dateLibrary = "java8";
    supportedLibraries.clear();
    supportedLibraries.put(LIBRARY_NAME, LIBRARY_NAME);

    // tell the model about extra mustache files to generate

    // if we are using Kubernetes, we should get a service url. We separate these because the serviceName
    // is used for the Spring configuration class
    // this should appear in your config as:
    // <configOptions>
    //   <serviceName>...</serviceName> etc
    // </configOptions>
    cliOptions.add(new CliOption(SERVICE_NAME, "Name of service to use for @enable"));
    cliOptions.add(new CliOption(SERVICE_ADDRESS, "Name of service to use for @enable"));
    cliOptions.add(new CliOption(SERVICE_PORT, "Port of service to use for @enable"));
    cliOptions.add(new CliOption("suppressIgnoreUnknown", "Don't add the ignore unknown to the generated models"));


    if (this.typeMapping == null) {
      this.typeMapping = new HashMap<>();
    }

    this.typeMapping.put("void", "Void");

    // override the location
    embeddedTemplateDir = templateDir = JERSEY2_TEMPLATE_FOLDER;
  }

//  @Override
//  public List<CodegenArgument> getLanguageArguments() {
//    List<CodegenArgument> args = super.getLanguageArguments() == null ? new ArrayList<>() : new ArrayList<>(super.getLanguageArguments());
//    CodegenArgument e = new CodegenArgument();
//    e.setOption(CodegenConstants.API_TESTS_OPTION);
//    e.setValue("false");
//    args.add(e);
//    return args;
//  }

  public String getName() {
    return LIBRARY_NAME;
  }

  public String getHelp() {
    return "jersey2 api generator. generates all classes and interfaces with jax-rs annotations with jersey2 extensions as necessary";
  }

  @Override
  public void preprocessOpenAPI(OpenAPI openAPI) {
    super.preprocessOpenAPI(openAPI);
    if(openAPI.getServers()!=null && openAPI.getServers().size()==1) {
      additionalProperties.put(SERVICE_DEFAULT_URL, openAPI.getServers().get(0).getUrl());
    }
  }

  // stoplight has a tendency to insert rubbish in the oas.json file
  @Override
  public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
    super.postProcessModelProperty(model, property);

    if("null".equals(property.example)) {
      property.example = null;
    }

    model.imports.remove("ApiModelProperty");
    model.imports.remove("ApiModel");
  }

  /**
   * This gets called once we have been passed the configuration read in by the plugin.
   */
  @Override
  public void processOpts() {
    super.processOpts();

    apiTemplateFiles.remove("api.mustache");

    // no documentation, we're british
    modelDocTemplateFiles.clear();
    apiDocTemplateFiles.clear();

    modelTemplateFiles.put("model.mustache", ".java");

    if (additionalProperties.get("client") != null) {
      apiTemplateFiles.put("Impl.mustache", ".java");
      apiTemplateFiles.put("ClientService.mustache", ".java");
    }
    if (additionalProperties.get("server") != null) {
      apiTemplateFiles.put("Service.mustache", ".java");
    }
    if (additionalProperties.get("server-security") != null) {
      apiTemplateFiles.put("SecurityService.mustache", ".java");
    }
    if (additionalProperties.get("server-delegate") != null) {
      apiTemplateFiles.put("DelegateServerService.mustache", ".java");
      apiTemplateFiles.put("DelegateService.mustache", ".java");
      apiTemplateFiles.put("SecurityService.mustache", ".java");
    }

//    apiTemplateFiles.put("Configuration.mustache", ".java");

    // this is the name of the library and the date package we use

    apiTestTemplateFiles.clear();



    if (additionalProperties.get(SERVICE_NAME) != null) {
      String serviceName = additionalProperties.get(SERVICE_NAME).toString();
      if (additionalProperties.get(SERVICE_ADDRESS) != null) {
        addJersey2Client(serviceName, additionalProperties.get(SERVICE_ADDRESS).toString());
      } else if (additionalProperties.get(SERVICE_PORT) != null) {
        addJersey2Client(serviceName, String.format("%s-service:%s", serviceName, additionalProperties.get(SERVICE_PORT).toString()));
      }
    }

    if ( additionalProperties.containsKey(CodegenConstants.IMPL_FOLDER) ) {
      implFolder = (String) additionalProperties.get(CodegenConstants.IMPL_FOLDER);
    }
  }



  @Override
  public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
    List<CodegenOperation> codegenOperations = getCodegenOperations(objs);

    if (codegenOperations.size() > 0) {
      objs.put("apiName", codegenOperations.get(0).baseName);
    }
    if(additionalProperties.containsKey(SERVICE_DEFAULT_URL)){
      objs.put(SERVICE_DEFAULT_URL, additionalProperties.get(SERVICE_DEFAULT_URL));
    }

    for (CodegenOperation op : codegenOperations) {
      // need to ensure the path if it has params as <> that it uses {} instead
      op.path = op.path.replace('<', '{').replace('>', '}');

      // an Object is actually a Response header. Ideally we don't ever want to return these but occasionally they
      // are required.
      if ("Object".equals(op.returnBaseType)) {
        op.returnBaseType = "Response";
      }

      if (op.produces != null) {
        int lastProducesHasNext = op.produces.size();
        for (Map<String, String> produce : op.produces) {
          lastProducesHasNext --;
          if (lastProducesHasNext > 0) {
            produce.put("hasMore", "true");
          }
        }
      }

      if (op.getHasQueryParams()) {
        final List<CodegenParameter> optionalQueryParams = op.allParams.stream().filter(p -> p.isQueryParam && !p.required).collect(Collectors.toList());
        if (optionalQueryParams.size() > 0) {
          op.vendorExtensions.put("x-has-delegator-holder", Boolean.TRUE);
          op.vendorExtensions.put("x-delegator-holder-params", optionalQueryParams);
          op.vendorExtensions.put("x-class-delegator-holder", camelize(op.operationId + "-holder", false));
        }
      }

      // regardless we don't want un-required query params in this one
      final String params = op.allParams.stream().filter(p -> !p.isQueryParam || p.required).map(p -> p.paramName).collect(Collectors.joining(","));
      if (params.length() > 0) {
        op.vendorExtensions.put("x-has-java-params", Boolean.TRUE);
        op.vendorExtensions.put("x-java-params", params);
        op.vendorExtensions.put("x-java-params-plus-types",
          op.allParams.stream().filter(p -> !p.isQueryParam || p.required).map(p -> p.dataType + " " + p.paramName).collect(Collectors.joining(","))
          );
      }
    }

    return super.postProcessOperationsWithModels(objs, allModels);
  }



  Map<String, CodegenModel> modelNames = new HashMap<>();


  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Object> postProcessModels(Map<String, Object> objs) {
    objs = super.postProcessModels(objs);

    List<Map<String, Object>> models = (List<Map<String, Object>>) objs.get("models");
    List<HashMap<String, String>> imports = (List<HashMap<String, String>> )objs.get("imports");
    imports.forEach((map) -> {
      if (map.containsKey("import")) {
        if (map.get("import").startsWith("io.swagger")) {
          map.remove("import");
        }
      }
    });
    imports.removeIf(Map::isEmpty);
    models.stream().forEach(model -> {
      CodegenModel m = (CodegenModel) model.get("model");
      modelNames.put(m.classname, m);
    });

    return objs;
  }

  @Override
  public void postProcessFile(File file, String fileType) {
    if ("java".equals(FilenameUtils.getExtension(file.toString()))) {
      try {
        final FileReader ifile = new FileReader(file);
        String inputFile = IOUtils.toString(ifile);
        ifile.close();
        String result = new Formatter(
          JavaFormatterOptions.builder()
            .style(JavaFormatterOptions.Style.GOOGLE)
            .build()).formatSourceAndFixImports(inputFile);
        if (result.trim().length() == 0) {
          log.error("Unable to format `{}`", file.getAbsolutePath());
        } else {
          final FileWriter ofile = new FileWriter(file);
          IOUtils.write(result, ofile);
          ofile.flush();
          ofile.close();
        }
      } catch (FormatterException | IOException e) {
        log.error("Failed to format file `{}`", file.getAbsolutePath(), e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<CodegenOperation> getCodegenOperations(Map<String, Object> objs) {
    return (List<CodegenOperation>) getOperations(objs).get("operation");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object>getOperations(Map<String, Object> objs) {
    return (Map<String, Object>) objs.get("operations");
  }

  @Override
  public String toModelName(String name) {
    return (name != null) ? super.toModelName(name) : "<<unknown-to-model-name-is-null>>";
  }



  @Override
  public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
    String basePath = resourcePath;

    // ensure all paths are relative -> /customer/get ==> customer/get
    if (basePath.startsWith("/")) {
      basePath = basePath.substring(1);
    }

    // extracts the base of the reference -> customer/get => basePath = customer
    int pos = basePath.indexOf("/");
    if (pos > 0) {
      basePath = basePath.substring(0, pos);
    }

    // if the url is /customer originally, then basePath will be empty put it in the "default" group.
    //
    if (basePath.equals("")) {
      basePath = additionalProperties.get(SERVICE_NAME) == null ? "default" : additionalProperties.get(SERVICE_NAME).toString();
    } else {
      if (co.path.startsWith("/" + basePath)) {
        co.path = co.path.substring(("/" + basePath).length());
      }
      co.subresourceOperation = !co.path.isEmpty();
    }

    // we create a list we throw away???
    List<CodegenOperation> opList = operations.computeIfAbsent(tag != null ? tag : basePath, k -> new ArrayList<CodegenOperation>());
    opList.add(co);
    co.baseName = basePath;
  }

  private void addJersey2Client(String serviceName, String serviceAddress) {
    System.out.printf("Service %s - located at `%s`\n", serviceName , serviceAddress);

    // standard Spring style naming
    String className = "Enable" + camelize(serviceName, false) + "Service";
    additionalProperties.put(SERVICE_NAME, className);
    additionalProperties.put(SERVICE_ADDRESS, serviceAddress);
    additionalProperties.put("package", modelPackage());

    supportingFiles.add(new SupportingFile("enable.mustache",
      sourceFolder + "/" + apiPackage().replace('.', '/'), className + ".java"));
  }

  @Override
  public String apiFilename(String templateName, String tag) {
    String suffix = (String)this.apiTemplateFiles().get(templateName);
    String result = this.apiFileFolder() + '/' + this.toApiFilename(tag) + suffix;
    int ix;
    if (templateName.endsWith("Impl.mustache")) {
      ix = result.lastIndexOf(47);
      result = result.substring(0, ix) + "/impl" + result.substring(ix, result.length() - 5) + "ServiceImpl.java";
    } else if (templateName.endsWith("Factory.mustache")) {
      ix = result.lastIndexOf(47);
      result = result.substring(0, ix) + "/factories" + result.substring(ix, result.length() - 5) + "ServiceFactory.java";
    } else if (templateName.endsWith("ClientService.mustache")) {
      ix = result.lastIndexOf(46);
      result = result.substring(0, ix) + "Client.java";
    } else if (templateName.endsWith("SecurityService.mustache")) {
      ix = result.lastIndexOf(46);
      result = result.substring(0, ix) + ".java";
    } else if (templateName.endsWith("DelegateServerService.mustache")) {
      ix = result.lastIndexOf(46);
      result = result.substring(0, ix) + "Delegator.java";
    } else if (templateName.endsWith("DelegateService.mustache")) {
      ix = result.lastIndexOf(46);
      result = result.substring(0, ix) + "Delegate.java";
    } else if (templateName.endsWith("Service.mustache")) {
      ix = result.lastIndexOf(46);
      result = result.substring(0, ix) + ".java";
    }

    return result;
  }


  public String toApiName(String name) {
    if (additionalProperties.get(SERVICE_NAME) != null) {
      return additionalProperties.get(SERVICE_NAME).toString();
    }

    if (name.length() == 0) {
      return "DefaultApi";
    }

    return org.openapitools.codegen.utils.StringUtils.camelize(name);
  }
}
