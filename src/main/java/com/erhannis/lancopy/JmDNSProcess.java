/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.mathnstuff.utils.ObservableMap.Change;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import spark.Spark;
import javafx.collections.ObservableMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class JmDNSProcess {
  private static class LCListener implements ServiceListener {
    private final DataOwner dataOwner;

    public LCListener(DataOwner dataOwner) {
      this.dataOwner = dataOwner;
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
      System.out.println("Service added: " + event.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
      System.out.println("Service removed: " + event.getInfo());
      dataOwner.remoteServices.remove(event.getName());
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      System.out.println("Service resolved: " + event.getInfo());
      dataOwner.remoteServices.put(event.getName(), event.getInfo());
    }
  }

  public final UUID ID = UUID.randomUUID();
  public final int PORT;

  private final DataOwner dataOwner;
  private final WsServer wsServer;
  private final WsClient wsClient;

  private JmDNSProcess(DataOwner dataOwner) {
    this.dataOwner = dataOwner;
    this.wsServer = new WsServer(dataOwner);
    this.wsClient = new WsClient(dataOwner);

    Spark.port(0);

    try {
      //TODO //SECURITY Change according to settings
      dataOwner.localData.set((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
    } catch (UnsupportedFlavorException ex) {
      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
    } catch (IOException ex) {
      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
    }

    Spark.webSocket("/monitor", wsServer);
    Spark.get("/string", (request, response) -> { //TODO //SECURITY Note - this DOES mean your clipboard is always accessible by anyone.  OTOH, this is be design, until we have some form of authentication.
      //TODO Support other flavors
      return dataOwner.localData.get();
    });
    //TODO Support files
    Spark.awaitInitialization();
    PORT = Spark.port(); // There's a brief race condition, here, btw, if endpoint is called before this line
    System.out.println("JmDNSProcess " + ID + " starting on port " + PORT);

    //TODO Should it be the wsServer that registers itself??  Unsure.
    dataOwner.localSummary.subscribe((summary) -> {
      wsServer.broadcast(summary);
    });

    dataOwner.remoteServices.subscribe((Change<String, ServiceInfo> change) -> {
      if (change.wasAdded) {
        dataOwner.remoteSummaries.put(change.key, "???"); //TODO Fix
      } else if (change.wasRemoved) {
        dataOwner.remoteSummaries.remove(change.key);
      }
    });
   
    new Thread(() -> {
      listen();
    }).start();
    new Thread(() -> {
      broadcast();
    }).start();
  }

  /**
   * Static method, to hint that this kicks off threads
   *
   * @return
   */
  public static JmDNSProcess start(DataOwner dataOwner) {
    return new JmDNSProcess(dataOwner);
  }

  private void broadcast() {
    try {
      // Create a JmDNS instance
      JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

      // Register a service
      ServiceInfo serviceInfo = ServiceInfo.create("_lancopy._tcp.local.", "LanCopy-" + ID.toString(), PORT, "path=string");
      jmdns.registerService(serviceInfo);
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
  }

  private void listen() {
    try {
      // Create a JmDNS instance
      JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

      // Add a service listener
      jmdns.addServiceListener("_lancopy._tcp.local.", new LCListener(dataOwner));
    } catch (UnknownHostException e) {
      System.out.println(e.getMessage());
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
  }

  private final OkHttpClient client = new OkHttpClient();

  public ObservableMap<String, String> getSummaries() {
    //return websockets.remoteSummaries; //TODO This is getting convoluted
    return null;
  }

  public String pullFromNode(String id) throws IOException {
    Request request = new Request.Builder().url(dataOwner.remoteServices.get(id).getURL("http")).build();
    try (Response response = client.newCall(request).execute()) {
      return response.body().string();
    }
  }
}
