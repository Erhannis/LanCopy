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
    private final WsClient wsClient;

    public LCListener(DataOwner dataOwner, WsClient wsClient) {
      this.dataOwner = dataOwner;
      this.wsClient = wsClient;
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
      System.out.println("Service added: " + event.getInfo());
    }

    //TODO The updating responsibilities seem split weirdly between here and WsClient
    
    @Override
    public void serviceRemoved(ServiceEvent event) {
      System.out.println("Service removed: " + event.getInfo());
      //dataOwner.remoteServices.remove(event.getName()); //TODO Change
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      System.out.println("Service resolved: " + event.getInfo());
      dataOwner.remoteServices.put(event.getName(), event.getInfo()); //TODO Change
      wsClient.addService(event.getInfo());
    }
  }

  public final UUID ID = UUID.randomUUID();
  public final int PORT;

  private final DataOwner dataOwner;
  private final WsServer wsServer;
  private final WsClient wsClient;

  private final JmDNS jmdns;
  
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
      System.out.println("LS: " + summary);
      wsServer.broadcast(summary);
    });

    dataOwner.remoteServices.subscribe((Change<String, ServiceInfo> change) -> {
      if (change.wasAdded) {
        dataOwner.remoteSummaries.put(change.key, "???"); //TODO Fix
      } else if (change.wasRemoved) {
        dataOwner.remoteSummaries.remove(change.key);
      }
    });
    
    JmDNS jmdns0 = null;
    try {
      // Create a JmDNS instance
      jmdns0 = JmDNS.create(InetAddress.getLocalHost());

      ServiceInfo serviceInfo = ServiceInfo.create("_lancopy._tcp.local.", "LanCopy-" + ID.toString(), PORT, "");
      jmdns0.registerService(serviceInfo);
      
      jmdns0.addServiceListener("_lancopy._tcp.local.", new LCListener(dataOwner, wsClient));
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
    this.jmdns = jmdns0;
  }

  /**
   * Static method, to hint that this kicks off threads
   *
   * @return
   */
  public static JmDNSProcess start(DataOwner dataOwner) {
    return new JmDNSProcess(dataOwner);
  }

  private final OkHttpClient client = new OkHttpClient();

  public String pullFromNode(String id) throws IOException {
    Request request = new Request.Builder().url(dataOwner.remoteServices.get(id).getURL("http")+"/string").build();
    try (Response response = client.newCall(request).execute()) {
      return response.body().string();
    }
  }

  public void shutdown() {
    jmdns.unregisterAllServices();
    wsClient.shutdown();
  }
}
