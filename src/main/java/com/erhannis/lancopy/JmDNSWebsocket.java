/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.util.Pair;
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
  public final ObservableMap<String, String> remoteSummaries = FXCollections.observableMap(new HashMap<String, String>());

  private String lastSummary = "";
  
  @OnWebSocketConnect
  public void connected(Session session) throws IOException {
    sessions.add(session);
    session.getRemote().sendString(lastSummary);
  }

  @OnWebSocketClose
  public void closed(Session session, int statusCode, String reason) {
    sessions.remove(session);
  }

  private ArrayList<Consumer<Pair<String, String>>> callbacks = new ArrayList<>();
  @OnWebSocketMessage
  public void message(Session session, String message) throws IOException {
    
  }

  public void broadcast(String str) {
    lastSummary = str;
    MultiException me = new MultiException();
    for (Session s : sessions) {
      try {
        s.getRemote().sendString(str);
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
