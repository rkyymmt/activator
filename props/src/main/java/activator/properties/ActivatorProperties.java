package activator.properties;

import java.util.Properties;

// This is a lame-o class that's kinda dirty.  maybe we can clean it up later, but we're using it across two scala versions right now.
public class ActivatorProperties {

  private static Properties loadProperties() {
    Properties props = new Properties();
    java.io.InputStream in = ActivatorProperties.class.getResourceAsStream("activator.properties");
    try {
      props.load(in);
    } catch(java.io.IOException e) { throw new RuntimeException(e); }
    finally { try { in.close(); } catch(java.io.IOException e) { throw new RuntimeException(e); }  }
    return props;
  }

  private static Properties props = loadProperties();
  /** Checks the system properties, before the environment, before the hard coded defaults. */
  private static String getProperty(String name) {
    String value = System.getProperty(name);
    if(value == null) {
      value = System.getenv("activator." + name);
    }
    if(value == null) {
      value = System.getenv("ACTIVATOR_" + name.replace('.', '_').toUpperCase());
    }
    if (value == null) {
      value = props.getProperty(name);
    }
    return value;
  }

  /** Looks up a property value, and parses its value as appropriate. */
  private static String lookupOr(String name, String defaultValue) {
    String value = getProperty(name);
    if(value == null) {
      value = defaultValue;
    }
    return value;
  }

  public static String TEMPLATE_UUID_PROPERTY_NAME = "template.uuid";
  public static String ABI_VERSION_PROPERTY_NAME = "activator.abi.version";
  public static String SCRIPT_NAME = "activator";


  public static String APP_VERSION() {
    return props.getProperty("app.version");
  }

  public static String APP_ABI_VERSION() {
    // TODO - Encode ABI version in metadata...
    return APP_VERSION();
  }

  public static String APP_SCALA_VERSION() {
    return props.getProperty("app.scala.version");
  }

  public static String SBT_VERSION() {
    return props.getProperty("sbt.version");
  }

  public static String SBT_SCALA_VERSION() {
    return props.getProperty("sbt.scala.version");
  }

  private static String uriToFilename(String uri) {
    try {
      return new java.io.File(new java.net.URI(uri)).getAbsolutePath();
    } catch(java.net.URISyntaxException ex) {
      // TODO - fix this error handling to not suck.
      throw new RuntimeException("BAD URI: " + uri);
    }
  }

  /** Returns the distribution home directory (or local project) as a URI string. */
  public static String ACTIVATOR_HOME_FILENAME() {
    return uriToFilename("file://" + ACTIVATOR_HOME());
  }

  /** Returns the distribution home directory (or local project) as a URI string. */
  public static String ACTIVATOR_HOME() {
    return getProperty("activator.home");
  }

  public static String GLOBAL_USER_HOME() {
    return getProperty("user.home");
  }

  public static String ACTIVATOR_USER_HOME() {
    return lookupOr("activator.user.home", getProperty("user.home") + "/.activator/" + APP_ABI_VERSION());
  }

  public static String ACTIVATOR_TEMPLATE_CACHE() {
    return lookupOr("activator.template.cache", ACTIVATOR_USER_HOME() + "/templates");
  }

  public static String ACTIVATOR_TEMPLATE_LOCAL_REPO() {
    String defaultValue = ACTIVATOR_HOME_FILENAME();
    if(defaultValue != null) {
      defaultValue = defaultValue + "/templates";
    }
    return lookupOr("activator.template.localrepo", defaultValue);
  }

  public static String ACTIVATOR_LAUNCHER_JAR() {
    String value = ACTIVATOR_HOME_FILENAME();
    String version = APP_VERSION();
    if(value != null && version != null) {
      // TODO - synch this with build in some better fashion!
      value = value+"/"+SCRIPT_NAME+"-launch-"+version+".jar";
    }
    return value;
  }

  public static String ACTIVATOR_LAUNCHER_BAT() {
    String value = ACTIVATOR_HOME_FILENAME();
    if(value != null) {
      value = value+"/"+SCRIPT_NAME+".bat";
    }
    return value;
  }
  public static String ACTIVATOR_LAUNCHER_BASH() {
    String value = ACTIVATOR_HOME_FILENAME();
    if(value != null) {
      value = value+"/"+SCRIPT_NAME;
    }
    return value;
  }

  public static java.io.File ACTIVATOR_LOCK_FILE() {
    return new java.io.File(ACTIVATOR_USER_HOME() + "/.lock");
  }

  public static java.io.File ACTIVATOR_PID_FILE() {
    return new java.io.File(ACTIVATOR_USER_HOME() + "/.currentpid");
  }
}
