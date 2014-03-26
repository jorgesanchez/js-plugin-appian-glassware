package com.appian.googleglass.mirror;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.appiancorp.suiteapi.cfg.Configuration;
import com.appiancorp.suiteapi.cfg.ConfigurationLoader;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.MenuValue;
import com.google.api.services.mirror.model.TimelineItem;

public class AppianGlasswareUtils {

  private static final Logger LOG = Logger.getLogger(AppianGlasswareUtils.class);
  public static URL resource = DatabaseCredentialStore.class.getResource("/com/appian/googleglass/plugins/glassware.properties");

  /**
   * Allows access to configuration parameters not available in the Appian plugin
   * framework
   */
  public static final Configuration CONFIG = ConfigurationLoader.getConfiguration();
  /**
   * Combines getScheme(), getServerAndPort(), getContextPath() to form the URL base of
   * public links. E.g., "https://myserver.mydomain.com/suite"
   */
  public static final String URI_PREFIX = CONFIG.getPublicSchemeLinkRoot().toString();
  /**
   * The required scopes to call Google Glass Mirror APIs
   */
  public static final ArrayList<String> SCOPES = new ArrayList<String>(Arrays.asList("profile",
    "email", "https://www.googleapis.com/auth/glass.timeline",
    "https://www.googleapis.com/auth/glass.location"));
  /**
   * URI path to the Servlet plugin that handled the callback code response from the
   * Google Oauth code.
   */
  public static final String AUTH_SERVLET_URI = "/plugins/servlet/ok.glass";
  /**
   * URI path to the Servlet plugin that handled the callback code response from the
   * Google Oauth code.
   */
  public static final String NOTIFY_SERVLET_URI = "/plugins/servlet/stateless/glass.notify";
  /**
   * Helper function that returns the proper callback uri
   *
   * @param redirectUri
   *          The redirect path to call
   * @return
   */
  public static String getCallbackUri(String redirectUri) {
    return URI_PREFIX + redirectUri;
  }
  /**
   * Returns the callback URI to the Authentication Servlet.
   * @return
   * @throws IOException
   */
  public static String getAuthCallbackUri() throws IOException {
    //return getCallbackUri(AUTH_SERVLET_URI);
    InputStream glasswarePropsStream = resource.openStream();
    Properties glasswareProps = new Properties();
    glasswareProps.load(glasswarePropsStream);
    return glasswareProps.getProperty("servlet_callback_linkroot") + AUTH_SERVLET_URI;
  }
  /**
   * Returns the callback URI to the Subscription Notification Servlet.
   * @return
   * @throws IOException
   */
  public static String getNotificationCallbackUri() throws IOException {
    InputStream glasswarePropsStream = resource.openStream();
    Properties glasswareProps = new Properties();
    glasswareProps.load(glasswarePropsStream);
    return glasswareProps.getProperty("servlet_callback_linkroot") + NOTIFY_SERVLET_URI;
  }

  public static List<MenuItem> createMenus(List<MenuItem> menus, boolean addDelete,
    boolean addReply, boolean addTogglePinned) {
    menus.add(getDeleteMenuItem());
    menus.add(new MenuItem().setAction("TOGGLE_PINNED"));
    menus.add(new MenuItem().setAction("REPLY"));
    return menus;
  }

  public static MenuItem getDeleteMenuItem() {
    return new MenuItem().setAction("DELETE");
  }

  public static TimelineItem createTimelineItem(String text, String html, Boolean isCover,
    String bundleId, String speakableType, String speakableText) {
    return new TimelineItem().setText(text)
      .setHtml(html)
      .setIsBundleCover(isCover)
      .setBundleId(bundleId)
      .setSpeakableType(speakableType)
      .setSpeakableText(speakableText);
  }

  public static MenuItem createCustomReplyMenuItem() {
    MenuItem myReply = new MenuItem();
    myReply.setAction("REPLY");
    myReply.setId("123");
    myReply.setValues(Arrays.asList(new MenuValue().setDisplayName("Reply to event")));
    return null;
  }

  public static void deleteAllCards(Mirror service) throws IOException {
    ArrayList<TimelineItem> cards = (ArrayList<TimelineItem>) service.timeline()
      .list()
      .execute()
      .getItems();
    for (TimelineItem timelineItem : cards) {
      service.timeline().delete(timelineItem.getId()).execute();
    }
  }

  public static String getAppianUserForUserId(String userId) {
    Connection connect;
    PreparedStatement preparedStatement;
    JdbcData jdbcData;
    ResultSet resultSet;

    try {
      jdbcData = getJdbcData();
      Class.forName(jdbcData.getJdbcDriverClass());
      connect = DriverManager.getConnection(jdbcData.getJdbcConnectionUri());

      preparedStatement = connect.prepareStatement("SELECT appianuserid FROM " + jdbcData.getJdbcSchemaName() +
        ".glassauthtype WHERE userId = ?;");
      preparedStatement.setString(1, userId);
      resultSet = preparedStatement.executeQuery();
      resultSet.next();
      return resultSet.getString("appianuserid");

    } catch (ClassNotFoundException e) {
      LOG.error("Could not load Driver", e);
    } catch (SQLException e) {
      LOG.error("Could not persist Credentials.", e);
    } catch (IOException e) {
      LOG.error("There was a problem loading JDBC data", e);
    }
    return null;
  }

  private static JdbcData getJdbcData() throws IOException {
    JdbcData jdbcData = new JdbcData();
    InputStream authPropertiesStream = resource.openStream();
    Properties authProperties = new Properties();
    authProperties.load(authPropertiesStream);
    jdbcData.setJdbcConnectionUri(authProperties.getProperty("credential_store.jdbc_connection_uri"));
    jdbcData.setJdbcDriverClass(authProperties.getProperty("credential_store.jdbc_driver_class"));
    jdbcData.setJdbcSchemaName(authProperties.getProperty("credential_store.jdbc_schema_name"));
    return jdbcData;
  }
}
