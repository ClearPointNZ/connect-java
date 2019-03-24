package cd.connect.jersey.common.logging;

import javax.ws.rs.core.Configurable;

/**
 * This class must be usable as soon as it is injected. Typically the payload exclusions
 * can change at runtime, but the filter definitions cannot.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public interface JerseyFiltering {
	boolean excludePayloadForUri(String uriPath);
	boolean excludeForUri(String uriPath);
	int maxBodySize();
  String getExclude();
  String getTracing();
}
