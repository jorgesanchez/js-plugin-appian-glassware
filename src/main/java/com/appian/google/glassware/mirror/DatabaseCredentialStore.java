package com.appian.google.glassware.mirror;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import com.appian.google.glassware.oauth.types.GlassAuthType;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.CredentialStore;

/**
 * Created by Dayel Ostraco Date: 12/4/13 This extends the Google OAuth2 CredentialStore
 * interface and uses a Database backend to persist Google Credentials in a thread safe
 * manner.
 */
public class DatabaseCredentialStore implements CredentialStore {

  private static final Logger LOG = Logger.getLogger(DatabaseCredentialStore.class);

  private static URL resource = DatabaseCredentialStore.class.getResource("/com/appian/google/glassware/plugins/glassware.properties");

  private JdbcData getJdbcData() throws IOException {
    JdbcData jdbcData = new JdbcData();
    InputStream authPropertiesStream = resource.openStream();
    Properties authProperties = new Properties();
    authProperties.load(authPropertiesStream);
    jdbcData.setJdbcConnectionUri(authProperties.getProperty("credential_store.jdbc_connection_uri"));
    jdbcData.setJdbcDriverClass(authProperties.getProperty("credential_store.jdbc_driver_class"));
    jdbcData.setJdbcSchemaName(authProperties.getProperty("credential_store.jdbc_schema_name"));
    return jdbcData;
  }

  /**
   * Lock on access to the database Credential store.
   */
  private final Lock lock = new ReentrantLock();

  /**
   * Stores a Google OAuth2 Credential in the database.
   *
   * @param userId
   *          String
   * @param credential
   *          Google OAuth2 Credential
   */
  public void store(String userId, Credential credential) {
    lock.lock();
    try {
      GlassAuthType item = findByUserId(userId);
      if (item == null) {
        item = new GlassAuthType();
        item.setUserId(userId);
        item.setAccessToken(credential.getAccessToken());
        item.setRefreshToken(credential.getRefreshToken());
        item.setExpirationTimeInMillis(credential.getExpirationTimeMilliseconds().intValue());
        create(item);
      } else {
        item.setUserId(userId);
        item.setAccessToken(credential.getAccessToken());
        item.setRefreshToken(credential.getRefreshToken());
        item.setExpirationTimeInMillis(credential.getExpirationTimeMilliseconds().intValue());
        update(item);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Removes a persisted Google Credential from the database.
   *
   * @param userId
   *          String
   * @param credential
   *          Google OAuth2 Credential
   */
  public void delete(String userId, Credential credential) {
    lock.lock();
    try {
      GlassAuthType item = findByUserId(userId);
      delete(item);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Loads a Google Credential with the contents of the persisted Google Credentials.
   *
   * @param userId
   *          String
   * @param credential
   *          Google OAuth2 Credential
   * @return boolean
   */
  public boolean load(String userId, Credential credential) {
    lock.lock();
    try {
      GlassAuthType item = findByUserId(userId);
      if (item != null) {
        credential.setAccessToken(item.getAccessToken());
        credential.setRefreshToken(item.getRefreshToken());
        credential.setExpirationTimeMilliseconds(new Long(item.getExpirationTimeInMillis()));
      }
      return item != null;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns all users in the Google Credentials Table
   *
   * @return List of all persisted Google authenticated user IDs
   */
  public List<String> listAllUsers() {

    List<String> userIds = new ArrayList<>();

    for (GlassAuthType credential : findAll()) {
      userIds.add(credential.getUserId());
    }

    return userIds;
  }

  /****************
   * JDBC Methods *
   ***************/

  /**
   * Persists a new Google Credential to the database.
   *
   * @param credential
   *          DatabasePersistedCredential
   */
  private void create(GlassAuthType credential) {

    Connection connect;
    PreparedStatement preparedStatement;
    JdbcData jdbcData;

    try {
      jdbcData = getJdbcData();
      Class.forName(jdbcData.getJdbcDriverClass());
      connect = DriverManager.getConnection(jdbcData.getJdbcConnectionUri());

      preparedStatement = connect.prepareStatement("INSERT INTO " + jdbcData.getJdbcSchemaName() +
        ".glassauthtype(userid,accesstoken,refreshtoken,expirationtimeinmillis) VALUES(?,?,?,?)");
      preparedStatement.setString(1, credential.getUserId());
      preparedStatement.setString(2, credential.getAccessToken());
      preparedStatement.setString(3, credential.getRefreshToken());
      preparedStatement.setLong(4, credential.getExpirationTimeInMillis());
      preparedStatement.executeUpdate();

    } catch (ClassNotFoundException e) {
      LOG.error("Could not load Driver", e);
    } catch (SQLException e) {
      LOG.error("Could not persist Credentials.", e);
    } catch (IOException e) {
      LOG.error("There was a problem loading JDBC data", e);
    }
  }

  /**
   * Updates a new Google Credential to the database.
   *
   * @param credential
   *          DatabasePersistedCredential
   */
  private void update(GlassAuthType credential) {
    Connection connect;
    PreparedStatement preparedStatement;
    JdbcData jdbcData;
    try {
      jdbcData = getJdbcData();
      Class.forName(jdbcData.getJdbcDriverClass());
      connect = DriverManager.getConnection(jdbcData.getJdbcConnectionUri());
      preparedStatement = connect.prepareStatement("UPDATE " +
        jdbcData.getJdbcSchemaName() +
        ".glassauthtype SET userid = ?, accesstoken = ?, refreshtoken = ?, expirationtimeinmillis = ? WHERE id = ?");
      preparedStatement.setString(1, credential.getUserId());
      preparedStatement.setString(2, credential.getAccessToken());
      preparedStatement.setString(3, credential.getRefreshToken());
      preparedStatement.setInt(4, credential.getExpirationTimeInMillis());
      preparedStatement.setInt(5, credential.getId());
      preparedStatement.executeUpdate();

    } catch (ClassNotFoundException e) {
      LOG.error("Could not load Driver", e);
    } catch (SQLException e) {
      LOG.error("Could not persist Credentials.", e);
    } catch (IOException e) {
      LOG.error("There was a problem loading JDBC data", e);
    }
  }

  /**
   * Removes a Google credential from the database.
   *
   * @param credential
   *          DatabasePersistedCredential
   */
  private void delete(GlassAuthType credential) {
    Connection connect;
    PreparedStatement preparedStatement;
    JdbcData jdbcData;
    try {
      jdbcData = getJdbcData();
      Class.forName(jdbcData.getJdbcDriverClass());
      connect = DriverManager.getConnection(jdbcData.getJdbcConnectionUri());

      preparedStatement = connect.prepareStatement("DELETE FROM " + jdbcData.getJdbcSchemaName() +
        ".glassauthtype WHERE userId = ?;");
      preparedStatement.setString(1, credential.getUserId());
      preparedStatement.executeQuery();
    } catch (ClassNotFoundException e) {
      LOG.error("Could not load Driver", e);
    } catch (SQLException e) {
      LOG.error("Could not persist Credentials.", e);
    } catch (IOException e) {
      LOG.error("There was a problem loading JDBC data", e);
    }
  }

  /**
   * Returns the Google credentials for a user with the passed in userId.
   *
   * @param userId
   *          String
   * @return DatabasePersistedCredential
   */
  private GlassAuthType findByUserId(String userId) {

    Connection connect;
    PreparedStatement preparedStatement;
    ResultSet resultSet;
    JdbcData jdbcData;

    try {
      jdbcData = getJdbcData();
      Class.forName(jdbcData.getJdbcDriverClass());
      connect = DriverManager.getConnection(jdbcData.getJdbcConnectionUri());

      preparedStatement = connect.prepareStatement("SELECT * FROM " + jdbcData.getJdbcSchemaName() +
        ".glassauthtype WHERE userId = ?;");
      preparedStatement.setString(1, userId);
      resultSet = preparedStatement.executeQuery();

      List<GlassAuthType> credentials = convertResultsSet(resultSet);
      if (credentials.size() == 1) {
        return credentials.get(0);
      } else {
        return null;
      }

    } catch (ClassNotFoundException e) {
      LOG.error("Could not load Driver", e);
    } catch (SQLException e) {
      LOG.error("Could not persist Credentials.", e);
    } catch (IOException e) {
      LOG.error("There was a problem loading JDBC data", e);
    }
    return null;
  }

  /**
   * Returns all DatabasePersistedCredentials located in the database.
   *
   * @return List<DatabasePersistedCredential>
   */
  private List<GlassAuthType> findAll() {

    Connection connect;
    PreparedStatement preparedStatement;
    ResultSet resultSet;
    JdbcData jdbcData;

    try {
      jdbcData = getJdbcData();
      Class.forName(jdbcData.getJdbcDriverClass());
      connect = DriverManager.getConnection(jdbcData.getJdbcConnectionUri());

      preparedStatement = connect.prepareStatement("SELECT * FROM " + jdbcData.getJdbcSchemaName() +
        ".glassauthtype;");
      resultSet = preparedStatement.executeQuery();

      List<GlassAuthType> credentials = convertResultsSet(resultSet);
      return credentials;

    } catch (ClassNotFoundException e) {
      LOG.error("Could not load Driver", e);
    } catch (SQLException e) {
      LOG.error("Could not persist Credentials.", e);
    } catch (IOException e) {
      LOG.error("There was a problem loading JDBC data", e);
    }

    return null;
  }

  /**
   * Converts a ResultSet to a collection of DatabasePersistedCredentials.
   *
   * @param resultSet
   *          JDBC ResultSet
   * @return List<DatabasePersistedCredential>
   * @throws SQLException
   */
  private List<GlassAuthType> convertResultsSet(ResultSet resultSet) throws SQLException {

    List<GlassAuthType> credentials = new ArrayList<>();

    while (resultSet.next()) {
      GlassAuthType credential = new GlassAuthType();
      credential.setUserId(resultSet.getString("userid"));
      credential.setAccessToken(resultSet.getString("accesstoken"));
      credential.setRefreshToken(resultSet.getString("refreshtoken"));
      credential.setExpirationTimeInMillis(resultSet.getInt("expirationtimeinmillis"));
      credentials.add(credential);
    }

    return credentials;
  }
}