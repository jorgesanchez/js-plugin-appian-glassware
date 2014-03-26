package com.appian.googleglass.mirror;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.log4j.Logger;

import com.appian.googleglass.oauth.types.GlassAuthType;
import com.appiancorp.suiteapi.common.Name;
import com.appiancorp.suiteapi.process.exceptions.SmartServiceException;
import com.appiancorp.suiteapi.process.framework.AppianSmartService;
import com.appiancorp.suiteapi.process.framework.Input;
import com.appiancorp.suiteapi.process.framework.MessageContainer;
import com.appiancorp.suiteapi.process.framework.Required;
import com.appiancorp.suiteapi.process.framework.SmartServiceContext;
import com.appiancorp.suiteapi.process.palette.PaletteInfo;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.NotificationConfig;
import com.google.api.services.mirror.model.TimelineItem;

@PaletteInfo(paletteCategory = "Integration Services", palette = "Google Glass Services")
public class PostTimelineItemFromNewsEventActivity extends AppianSmartService {

  private static final Logger LOG = Logger.getLogger(PostTimelineItemFromNewsEventActivity.class);
  private final SmartServiceContext smartServiceCtx;
  private static final String MISSING_PARAMS_ERROR = "error.missing.parameters.userMessage";
  private static final String USER_AUTH_ERROR = "error.auth.userMessage";
  private static final String USER_EXECUTION_ERROR = "error.execution.userMessage";
  private GlassAuthType authData;
  private String htmlData;
  private String moreInfoHtmlData;
  private String newsEntryEventId;
  private String timelineId;

  @Override
  public void run() throws SmartServiceException {
    TokenResponse response = null;
    Credential creds = null;
    Mirror service = null;
    TimelineItem card = null;
    TimelineItem moreInfo = null;
    ArrayList<MenuItem> menus = new ArrayList<MenuItem>();
    // Abort if neither text or html is set
    if (htmlData == null || htmlData.isEmpty()) {
      throw createException(new InvalidParameterException(), MISSING_PARAMS_ERROR,
        MISSING_PARAMS_ERROR, null);
    }
    try {
      // Figure out the impacted user and get their credentials for API calls
      creds = AuthUtil.getCredential(authData.getUserId());
      service = MirrorClient.getMirror(creds);
    } catch (IOException e) {
      throw createException(e, USER_AUTH_ERROR, USER_AUTH_ERROR, null);
    }
    card = new TimelineItem();
    card.setHtml(htmlData);
    if (moreInfoHtmlData != null && !moreInfoHtmlData.isEmpty()) {
      card.setIsBundleCover(Boolean.TRUE);
      card.setBundleId(newsEntryEventId);
      moreInfo = new TimelineItem();
      moreInfo.setHtml(moreInfoHtmlData);
      moreInfo.setId(newsEntryEventId);
      moreInfo.setBundleId(newsEntryEventId);
      menus.add(new MenuItem().setAction("REPLY"));
      menus.add(new MenuItem().setAction("DELETE"));
      moreInfo.setMenuItems(menus);
      moreInfo.setNotification(new NotificationConfig().setLevel("DEFAULT"));
      card.setMenuItems(Collections.singletonList(new MenuItem().setAction("DELETE")));
    } else {
      card.setNotification(new NotificationConfig().setLevel("DEFAULT"));
      card.setId(newsEntryEventId);
      menus.add(new MenuItem().setAction("REPLY"));
      menus.add(new MenuItem().setAction("DELETE"));
      card.setMenuItems(menus);
    }

    try {
      timelineId = service.timeline().insert(card).execute().getId();
      service.timeline().insert(moreInfo).execute();
    } catch (IOException e) {
      LOG.error(e);
      throw createException(e, USER_EXECUTION_ERROR, USER_EXECUTION_ERROR, null);
    }
  }

  public PostTimelineItemFromNewsEventActivity(SmartServiceContext smartServiceCtx) {
    super();
    this.smartServiceCtx = smartServiceCtx;
  }

  @Override
  public void onSave(MessageContainer messages) {
  }

  @Override
  public void validate(MessageContainer messages) {
  }

  @Input(required = Required.ALWAYS)
  @Name("authData")
  public void setAuthData(GlassAuthType val) {
    this.authData = val;
  }

  @Input(required = Required.ALWAYS)
  @Name("htmlData")
  public void setHtmlData(String val) {
    this.htmlData = val;
  }

  @Input(required = Required.OPTIONAL)
  @Name("moreInfoHtmlData")
  public void setMoreInfoHtmlData(String val) {
    this.moreInfoHtmlData = val;
  }

  @Input(required = Required.ALWAYS)
  @Name("newsEntryEventId")
  public void setNewsEntryEventId(String val) {
    this.newsEntryEventId = val;
  }

  @Name("timelineId")
  public String getTimelineId() {
    return timelineId;
  }

  private SmartServiceException createException(Throwable t, String userKey, String alertKey,
    Object args[]) {
    SmartServiceException.Builder b = new SmartServiceException.Builder(getClass(), t);
    b.userMessage(userKey, args);
    b.alertMessage(alertKey, args);
    b.addCauseToUserMessageArgs();
    b.addCauseToAlertMessageArgs();
    return b.build();
  }

}
