package com.appian.google.glassware.mirror;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.appiancorp.services.ServiceContext;
import com.appiancorp.suiteapi.common.ServiceLocator;
import com.appiancorp.suiteapi.common.exceptions.InvalidPriorityException;
import com.appiancorp.suiteapi.common.exceptions.InvalidStateException;
import com.appiancorp.suiteapi.common.exceptions.InvalidUserException;
import com.appiancorp.suiteapi.common.exceptions.InvalidVersionException;
import com.appiancorp.suiteapi.common.exceptions.StorageLimitException;
import com.appiancorp.suiteapi.process.ProcessDesignService;
import com.appiancorp.suiteapi.process.ProcessStartConfig;
import com.appiancorp.suiteapi.process.ProcessVariable;
import com.appiancorp.suiteapi.type.AppianType;
import com.appiancorp.suiteapi.type.NamedTypedValue;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.Attachment;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.Notification;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.api.services.mirror.model.UserAction;

/**
 * Handled the notifications sent back from subscriptions
 *
 * @author jorge.sanchez
 */
public class GlasswareMirrorNotifyServlet extends HttpServlet {

  private static final long serialVersionUID = 9169350625721783901L;
  private static final Logger LOG = Logger.getLogger(GlasswareMirrorNotifyServlet.class);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException,
    IOException {
    printServletAck(req, res);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    // Respond with OK and status 200 in a timely fashion to prevent redelivery
    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.append("OK");
    writer.close();
    // Validate notification and log it
    String notificationString = validateNotification(request);
    LOG.info("got raw notification " + notificationString);
    JsonFactory jsonFactory = new JacksonFactory();
    Notification notification = jsonFactory.fromString(notificationString, Notification.class);
    LOG.info("Got a notification with ID: " + notification.getItemId());

    // Figure out the impacted user and get their credentials for API calls
    String userId = notification.getUserToken();
    Credential credential = AuthUtil.getCredential(userId);
    Mirror mirrorClient = MirrorClient.getMirror(credential);

    if (notification.getCollection().equals("timeline")) {
      // Get the impacted timeline item
      TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
      LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

      // If it was a share, and contains a photo, update the photo's caption to
      // acknowledge that we got it.
      if (notification.getUserActions().contains(new UserAction().setType("SHARE")) &&
        timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {
        try {
          handlePhotoShare(timelineItem, mirrorClient, notification, userId);
        } catch (Exception e) {
          LOG.error("There was an error handling the photo share", e);
        }
      } else if (notification.getUserActions().contains(new UserAction().setType("REPLY"))) {
        try {
          handleUserReply(timelineItem, mirrorClient, userId);
        } catch (Exception e) {
          LOG.error("There was an error launching the process", e);
        }
      } else if ("CUSTOM".equals(notification.getUserActions().get(0).getType())) {
        LOG.info("User clicked on a CUSTOM menu option.");
        try {
          handleCustomAction(timelineItem, mirrorClient, notification, userId);
        } catch (Exception e) {
          LOG.error("There was an error handling CUSTOM action.", e);
        }
      } else {
        LOG.info("I don't know what to do with this notification, so I'm ignoring it.");
      }
    }
  }

  private void handleCustomAction(TimelineItem timelineItem, Mirror mirrorClient,
    Notification notification, String userId) throws InvalidPriorityException,
    InvalidVersionException, InvalidStateException, StorageLimitException, InvalidUserException,
    IllegalArgumentException, Exception {
    String customAction = notification.getUserActions().get(0).getPayload();
    LOG.info("Selected option was: " + customAction +
      ". Launching Appian process to handle CUSTOM actions.");
    String bundleId = timelineItem.getBundleId();
    InputStream authPropertiesStream = AppianGlasswareUtils.resource.openStream();
    Properties glasswareProperties = new Properties();
    glasswareProperties.load(authPropertiesStream);
    launchProcessForCustomAction(glasswareProperties.getProperty("custom_target_pm.uuid"),
      customAction, bundleId, AppianGlasswareUtils.getAppianUserForUserId(userId));
    LOG.info("Information sent to Appian. Updating the TimelineItem to indicate this.");
    timelineItem.setHtml(makeHtmlForCard("<p class='text-auto-size'>" + customAction +
      " Received.</p>", "Custom Action processed in Appian!"));
    timelineItem.setText(null);
    timelineItem.setMenuItems(Collections.singletonList(new MenuItem().setAction("DELETE")));
    mirrorClient.timeline().update(timelineItem.getId(), timelineItem).execute();

  }

  private void launchProcessForCustomAction(String pmUui, String customAction, String bundleId,
    String appianUserForUserId) throws InvalidPriorityException, InvalidVersionException,
    InvalidStateException, StorageLimitException, InvalidUserException, IllegalArgumentException,
    Exception {
    ServiceContext sc = ServiceLocator.getAdministratorServiceContext();
    ProcessDesignService pds = ServiceLocator.getProcessDesignService(sc);
    Long pmId = pds.getProcessModelIdByUuid(pmUui);
    LOG.info("Found Model Id for UUID: " + pmId);
    ProcessStartConfig psc = new ProcessStartConfig();
    ProcessVariable pvAppianUserId = createRequiredParamPv("appianUserId", AppianType.STRING,
      appianUserForUserId);
    ProcessVariable pvBundleId = createRequiredParamPv("bundleId", AppianType.STRING, bundleId);
    ProcessVariable pvCustomAction = createRequiredParamPv("customAction", AppianType.STRING,
      customAction);
    ProcessVariable[] processParams = new ProcessVariable[] {pvAppianUserId, pvBundleId,
      pvCustomAction};
    psc.setProcessParameters(processParams);
    LOG.info("Starting Process with parameters: " + bundleId + "; " + appianUserForUserId + "; " +
      customAction);
    Long pid = pds.initiateProcess(pmId, psc);
    LOG.info("Successfully started process to handle Glass [CUSTOM] action with pid: " + pid);
  }

  private void handleUserReply(TimelineItem timelineItem, Mirror mirrorClient, String userId)
    throws InvalidPriorityException, InvalidVersionException, InvalidStateException,
    StorageLimitException, InvalidUserException, IllegalArgumentException, Exception {
    LOG.info("User replied to the timeline. Capturing the reply transcription and launching"
      + " appian process to handle");
    String bundleId = timelineItem.getBundleId();
    String userComments = timelineItem.getText();

    InputStream authPropertiesStream = AppianGlasswareUtils.resource.openStream();
    Properties glasswareProperties = new Properties();
    glasswareProperties.load(authPropertiesStream);
    launchProcessForReply(glasswareProperties.getProperty("reply_target_pm.uuid"), bundleId,
      userComments, AppianGlasswareUtils.getAppianUserForUserId(userId));
    LOG.info("Information sent to Appian. Updating the TimelineItem to indicate this.");
    timelineItem.setHtml(makeHtmlForCard("<p class='text-auto-size'>" + timelineItem.getText() +
      "</p>", "Your comments have been processed in Appian"));
    timelineItem.setText(null);
    timelineItem.setMenuItems(Collections.singletonList(new MenuItem().setAction("DELETE")));
    mirrorClient.timeline().update(timelineItem.getId(), timelineItem).execute();

  }

  private String validateNotification(HttpServletRequest request) throws IOException {
    // Get the notification object from the request body (into a string so we can log it)
    BufferedReader notificationReader = new BufferedReader(new InputStreamReader(
      request.getInputStream()));
    String notificationString = "";

    // Count the lines as a very basic way to prevent Denial of Service attacks
    int lines = 0;
    String line;
    while ((line = notificationReader.readLine()) != null) {
      notificationString += line;
      lines++;
      // No notification would ever be this long. Something is very wrong.
      if (lines > 1000) {
        throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
      }
    }
    return notificationString;
  }

  /**
   * Prints an Acknowledgment of the Get request. Simply to demonstrate the servlet is up
   * and running.
   *
   * @param req
   *          Request
   * @param res
   *          Response
   * @throws IOException
   */
  private void printServletAck(HttpServletRequest req, HttpServletResponse res) throws IOException {
    PrintWriter out = new PrintWriter(res.getOutputStream());
    out.println("<!doctype html>");
    out.println("<html><body>");
    Map<String, String[]> params = req.getParameterMap();
    for (String key : params.keySet()) {
      out.println("<div>");
      out.println(key + " = ");
      String[] vals = params.get(key);
      if (vals.length == 1) {
        out.println(vals[0]);
      } else {
        out.println(Arrays.asList(vals));
      }
      out.println("</div>");
    }
    out.println("<br/><h1>Mirror Notify Servlet is up!</h1>");
    out.println("</body></html>");
    res.setContentType("text/html");
    out.close();
  }

  /**
   * Downloads and send the picture to the specified Appian process for handling.
   */
  private void handlePhotoShare(TimelineItem timelineItem, Mirror mirrorClient,
    Notification notification, String userId) throws InvalidPriorityException,
    InvalidVersionException, InvalidStateException, StorageLimitException, InvalidUserException,
    IllegalArgumentException, Exception {
    LOG.info("It was a share of a photo. Sending to Appian Process to handle.");
    String caption = timelineItem.getText();
    if (caption == null) {
      caption = "";
    }
    Attachment sharedPhoto = timelineItem.getAttachments().get(0);
    InputStream photoStream = null;
    InputStream authPropertiesStream = null;
    ByteArrayOutputStream collect = null;
    Base64OutputStream b64os = null;
    String codedImg = null;
    String sharePmUUID = null;
    String aeUserId = null;
    String bundleId = null;
    try {
      photoStream = downloadAttachment(mirrorClient, sharedPhoto);
      LOG.info("Photo stream retrieved. Converting to Base64 encoded string");
      authPropertiesStream = AppianGlasswareUtils.resource.openStream();
      Properties glasswareProperties = new Properties();
      glasswareProperties.load(authPropertiesStream);
      sharePmUUID = glasswareProperties.getProperty("share_target_pm.uuid");
      aeUserId = AppianGlasswareUtils.getAppianUserForUserId(userId);
      bundleId = timelineItem.getBundleId() == null ? "NONE" : timelineItem.getBundleId();
      collect = new ByteArrayOutputStream();
      b64os = new Base64OutputStream(collect);
      b64os.write(IOUtils.toByteArray(photoStream));
      b64os.close();
      codedImg = new String(collect.toByteArray());
      LOG.info("Base64 conversion complete!. Launching Process");
      launchProcessForShare(sharePmUUID, bundleId, aeUserId, codedImg);
    } finally {
      photoStream.close();
      authPropertiesStream.close();
    }
    // Create a new item with just the values that we want to patch.
    TimelineItem itemPatch = new TimelineItem();
    itemPatch.setText("Appian glassware got your photo! " + caption);

    mirrorClient.timeline().patch(notification.getItemId(), itemPatch).execute();

  }

  private void launchProcessForShare(String sharePmUUID, String bundleId, String aeUserId,
    String codedImg) throws InvalidPriorityException, InvalidVersionException,
    InvalidStateException, StorageLimitException, InvalidUserException, IllegalArgumentException,
    Exception {
    ServiceContext sc = ServiceLocator.getAdministratorServiceContext();
    ProcessDesignService pds = ServiceLocator.getProcessDesignService(sc);
    Long pmId = pds.getProcessModelIdByUuid(sharePmUUID);
    LOG.info("Found Model Id for UUID: " + pmId);
    ProcessStartConfig psc = new ProcessStartConfig();
    ProcessVariable pvUserId = createRequiredParamPv("appianUserId", AppianType.STRING, aeUserId);
    ProcessVariable pvBundleId = createRequiredParamPv("bundleId", AppianType.STRING, bundleId);
    ProcessVariable pvCodedImg = createRequiredParamPv("sharedImageBase64String",
      AppianType.STRING, codedImg);
    ProcessVariable[] processParams = new ProcessVariable[] {pvUserId, pvBundleId, pvCodedImg};
    psc.setProcessParameters(processParams);
    LOG.info("Starting Process with parameters: " + bundleId + "; " + aeUserId +
      "; encoded img text");
    Long pid = pds.initiateProcess(pmId, psc);
    LOG.info("Successfully started process to handle Glass [SHARE] action with pid: " + pid);
  }

  /**
   * Takes the target UUID of the process to launch. The process is assumed to need
   * parameters 'appianUserId', 'feedEntryEventId', 'userComments'.
   */
  private void launchProcessForReply(String pmUui, String bundleId, String comments, String userId)
    throws InvalidPriorityException, InvalidVersionException, InvalidStateException,
    StorageLimitException, InvalidUserException, IllegalArgumentException, Exception {
    ServiceContext sc = ServiceLocator.getAdministratorServiceContext();
    ProcessDesignService pds = ServiceLocator.getProcessDesignService(sc);
    Long pmId = pds.getProcessModelIdByUuid(pmUui);
    LOG.info("Found Model Id for UUID: " + pmId);
    ProcessStartConfig psc = new ProcessStartConfig();
    ProcessVariable pvAppianUserId = createRequiredParamPv("appianUserId", AppianType.STRING,
      userId);
    ProcessVariable pvBundleId = createRequiredParamPv("bundleId", AppianType.STRING, bundleId);
    ProcessVariable userComments = createRequiredParamPv("userComments", AppianType.STRING,
      comments);
    ProcessVariable[] processParams = new ProcessVariable[] {pvAppianUserId, pvBundleId,
      userComments};
    psc.setProcessParameters(processParams);
    LOG.info("Starting Process with parameters: " + bundleId + "; " + userId + "; " + comments);
    Long pid = pds.initiateProcess(pmId, psc);
    LOG.info("Successfully started process to handle Glass [REPLY] action with pid: " + pid);
  }

  private ProcessVariable createRequiredParamPv(String name, int type, Object value) {
    ProcessVariable pv = new ProcessVariable(new NamedTypedValue(name, (long) type, value));
    pv.setParameter(true);
    pv.setRequired(true);
    return pv;
  }

  private InputStream downloadAttachment(Mirror service, Attachment attachment) {
    try {
      HttpResponse resp = service.getRequestFactory()
        .buildGetRequest(new GenericUrl(attachment.getContentUrl()))
        .execute();
      return resp.getContent();
    } catch (IOException e) {
      // An error occurred.
      LOG.error("There was an error getting the shared photo", e);
      return null;
    }
  }

  /**
   * Wraps some HTML content in article/section tags and adds a footer identifying the
   * card as originating from the Java Quick Start.
   *
   * @param content
   *          the HTML content to wrap
   * @return the wrapped HTML content
   */
  private static String makeHtmlForCard(String content, String message) {
    return "<article class='auto-paginate'>" + content + "<footer><p>" + message +
      "</p></footer></article>";
  }

}
