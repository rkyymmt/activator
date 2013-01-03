package snap.properties;

import java.util.Properties;

// This is a lame-o class that's kinda dirty.  maybe we can clean it up later, but we're using it across two scala versions right now.
public class SnapProperties {

  private static Properties loadProperties() {
    Properties props = new Properties();
    java.io.InputStream in = SnapProperties.class.getResourceAsStream("snap.properties");
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
      value = System.getenv(name);
    }
    if(value == null) {
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

  public static String APP_VERSION() {
    return props.getProperty("app.version");
  }

  public static String APP_ABI_VERSION() {
    // TODO - Encode ABI version in SNAP metadata...
    return APP_VERSION();
  }

  public static String SBT_VERSION() {
    return props.getProperty("sbt.version");
  }

  public static String SNAP_HOME() {
    return getProperty("snap.home");
  }

  public static String SNAP_USER_HOME() {
    return lookupOr("snap.user.home", getProperty("user.home") + "/.snap/" + APP_ABI_VERSION());
  }

  public static String SNAP_TEMPLATE_CACHE() {
    return lookupOr("snap.template.cache", SNAP_USER_HOME() + "/templates");
  }

  public static String SNAP_TEMPLATE_LOCAL_REPO() {
    String defaultValue = SNAP_HOME();
    if(defaultValue != null) {
      defaultValue = defaultValue + "/templates";
    }
    return lookupOr("snap.template.localrepo", defaultValue);
  }

  public static String SNAP_LAUNCHER_JAR() {
    String value = SNAP_HOME();
    String version = APP_VERSION();
    if(value != null && version != null) {
      // TODO - synch this with build in some better fashion!
      value = value + "/snap-launch-"+version+".jar";
    }
    return value;
  }

  public static String SNAP_LAUNCHER_BAT() {
    String value = SNAP_HOME();
    if(value != null) {
      value = value + "/snap.bat";
    }
    return value;
  }
  public static String SNAP_LAUNCHER_BASH() {
    String value = SNAP_HOME();
    if(value != null) {
      value = value + "/snap";
    }
    return value;
  }
}
