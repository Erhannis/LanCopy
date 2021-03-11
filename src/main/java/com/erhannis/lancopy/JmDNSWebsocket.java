/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import java.io.IOException;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 *
 * @author erhannis
 */
@WebSocket
public class JmDNSWebsocket {
  private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();

  @OnWebSocketConnect
  public void connected(Session session) throws IOException {
    sessions.add(session);
    session.getRemote().sendString("Hello!");
  }

  @OnWebSocketClose
  public void closed(Session session, int statusCode, String reason) {
    sessions.remove(session);
  }

  @OnWebSocketMessage
  public void message(Session session, String message) throws IOException {
    System.out.println("Got: " + message);   // Print message
    if ("GET".equals(message)) {
      session.getRemote().sendString(message);
    }
  }

  public static final int SUMMARY_LENGTH = 50;
  private String data = "";
  public void updateData(String str) {
    this.data = str;
    String summary = data.substring(0, Math.min(data.length(), SUMMARY_LENGTH));
    MultiException me = new MultiException();
    for (Session s : sessions) {
      try {
        s.getRemote().sendString(summary);
      } catch (IOException ex) {
        me.addSuppressed(ex);
      }
    }
    if (me.getSuppressed().length > 0) {
      try {
        throw me;
      } catch (MultiException ex) {
        Logger.getLogger(JmDNSWebsocket.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
  }
}
