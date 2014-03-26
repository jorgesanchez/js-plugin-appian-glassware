package com.appian.glass.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.appian.googleglass.oauth.types.GlassAuthType;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.MenuValue;
import com.google.api.services.mirror.model.Subscription;
import com.google.api.services.mirror.model.TimelineItem;

public class PostTimelineCard {
  private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
  private static JsonFactory JSON_FACTORY = new JacksonFactory();
  private static final String USER_TOKEN = "sanchez@wetheinter.net";
  public static final ArrayList<String> SCOPES = new ArrayList<String>(Arrays.asList("profile",
    "email", "https://www.googleapis.com/auth/glass.timeline",
    "https://www.googleapis.com/auth/glass.location"));

  public static void main(String[] args) throws IOException {
    Mirror service = null;
    Subscription subscription = null;
    TimelineItem card = null, card2 = null, card3 = null;
    ArrayList<MenuItem> menus = new ArrayList<MenuItem>();
    try {
      service = createMirrorService(getSampleAuthData());

      card = createTimelineItem(null, getSampleHTMLContent(), Boolean.TRUE, "f-44", "Appian Event",
        "This is an example of what a feed event could look like using HTML");
      card.setMenuItems(menus);
      card2 = createTimelineItem("Sample more info card", null, Boolean.FALSE, "f-44", "More Info",
        "This is more info");
      card2.setMenuItems(menus);

      //executeInsert(service, card);
      //executeInsert(service, card2);
      deleteAllCards(service);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static Mirror createMirrorService(GlassAuthType authData) throws IOException {
    TokenResponse response = new GoogleRefreshTokenRequest(HTTP_TRANSPORT, JSON_FACTORY,
      authData.getRefreshToken(), authData.getClientId(), authData.getClientSecret()).setScopes(
        SCOPES).execute();
    GoogleCredential creds = new GoogleCredential.Builder().setTransport(HTTP_TRANSPORT)
      .setJsonFactory(JSON_FACTORY)
      .setClientSecrets(authData.getClientId(), authData.getClientSecret())
      .build()
      .setFromTokenResponse(response);
    return new Mirror.Builder(HTTP_TRANSPORT, JSON_FACTORY, creds).setApplicationName(
      "Jorge's Test").build();
  }

  private static List<MenuItem> createMenus(List<MenuItem> menus, boolean addDelete,
    boolean addReply, boolean addTogglePinned) {
    menus.add(getDeleteMenuItem());
    menus.add(new MenuItem().setAction("TOGGLE_PINNED"));
    menus.add(new MenuItem().setAction("REPLY"));
    return menus;
  }

  private static TimelineItem createTimelineItem(String text, String html, Boolean isCover,
    String bundleId, String speakableType, String speakableText) {
    return new TimelineItem().setText(text)
      .setHtml(html)
      .setIsBundleCover(isCover)
      .setBundleId(bundleId)
      .setSpeakableType(speakableType)
      .setSpeakableText(speakableText);
  }

  private static MenuItem createCustomReplyMenuItem() {
    MenuItem myReply = new MenuItem();
    myReply.setAction("REPLY");
    myReply.setId("123");
    myReply.setValues(Arrays.asList(new MenuValue().setDisplayName("Reply to event")));
    return null;
  }

  private static GlassAuthType getSampleAuthData() {
    GlassAuthType sample = new GlassAuthType();
    sample.setClientId("1087990820452.apps.googleusercontent.com");
    sample.setClientSecret("mQa-km4WjWE114mtVHQn5mXc");
    sample.setAccessToken("ya29.1.AADtN_UclbUpHoOmq8dHsdpJiOceqLUAo5sSFotYEAyJkVonouEVT2GkfM-GA7jy_VMr");
    sample.setRefreshToken("1/dXGtlv5IT4Yh22u5sE9t-iIsqLRUgchbmdjDPhWNWVU");
    return sample;
  }

  private static String getSampleHTMLContent() {
    StringBuffer html = new StringBuffer();
    html.append("<article class=\"author\">");
    html.append("<header><img src=\"http://www.appian.com/export/images/appian-twitter.jpeg\"/>");
    html.append("<h1>Jorge Sanchez</h1><h2>Appian Corporation</h2></header>");
    html.append("<section>");
    html.append("<p class=\"text-auto-size\">This is an example of what a feed event could look like using <span class=\"blue\">#HTML</span></p>");
    html.append("</section></article>");
    return html.toString();
  }

  private static String getSampleTextContent() {
    return "Hello, this is a sample text from Jorge";
  }

  private static MenuItem getDeleteMenuItem() {
    return new MenuItem().setAction("DELETE");
  }

  private static void deleteAllCards(Mirror service) throws IOException {
    ArrayList<TimelineItem> cards = (ArrayList<TimelineItem>) service.timeline()
      .list()
      .execute()
      .getItems();
    for (TimelineItem timelineItem : cards) {
      service.timeline().delete(timelineItem.getId()).execute();
    }
  }

  private static TimelineItem executeInsert(Mirror service, TimelineItem card) throws IOException {
    return service.timeline().insert(card).execute();
  }

}
