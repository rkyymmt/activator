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

  public static String APP_VERSION() {
    return props.getProperty("app.version");
  }

  public static String SBT_VERSION() {
    return props.getProperty("sbt.version");
  }
}
