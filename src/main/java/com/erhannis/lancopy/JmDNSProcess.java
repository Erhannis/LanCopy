/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import jcsp.lang.AltingChannelInput;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.Any2OneChannel;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.CSProcess;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutput;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.Parallel;
import jcsp.util.InfiniteBuffer;
import spark.Spark;
import javafx.collections.ObservableMap;

public class JmDNSProcess {
  private static class SampleListener implements ServiceListener {
    @Override
    public void serviceAdded(ServiceEvent event) {
      System.out.println("Service added: " + event.getInfo());
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
      System.out.println("Service removed: " + event.getInfo());
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      System.out.println("Service resolved: " + event.getInfo());
    }
  }

  public final UUID ID = UUID.randomUUID();
  public final int PORT;
  
  private final ObservableMap<String, String> summaries = FXCollections.observableMap(new HashMap<String, String>());;
  private final JmDNSWebsocket websockets = new JmDNSWebsocket();

  private JmDNSProcess() {
    Spark.port(0);
    
    Spark.webSocket("/monitor", websockets);
    Spark.get("/string", (request, response) -> { //TODO //SECURITY Note - this DOES mean your clipboard is always accessible by anyone.  OTOH, this is be design, until we have some form of authentication.
      //TODO Support other flavors
      return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    });
    //TODO Support files
    Spark.awaitInitialization();
    PORT = Spark.port(); // There's a brief race condition, here, btw, if endpoint is called before this line
    System.out.println("JmDNSProcess " + ID + " starting on port " + PORT);

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
  public static JmDNSProcess start() {
    return new JmDNSProcess();
  }

  private void broadcast() {
    try {
      // Create a JmDNS instance
      JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

      // Register a service
      ServiceInfo serviceInfo = ServiceInfo.create("_http._tcp.local.", "LanCopy-" + ID.toString(), PORT, "path=index.html");
      jmdns.registerService(serviceInfo);

      // Wait a bit
      Thread.sleep(25000);

      // Unregister all services
      jmdns.unregisterAllServices();

    } catch (IOException e) {
      System.out.println(e.getMessage());
    } catch (InterruptedException ex) {
      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  private void listen() {
    try {
      // Create a JmDNS instance
      JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

      // Add a service listener
      jmdns.addServiceListener("_http._tcp.local.", new SampleListener());

      // Wait a bit
      Thread.sleep(30000);
    } catch (UnknownHostException e) {
      System.out.println(e.getMessage());
    } catch (IOException e) {
      System.out.println(e.getMessage());
    } catch (InterruptedException ex) {
      Logger.getLogger(JmDNSProcess.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public void updateData(String data) {
    websockets.updateData(data);
  }

  public ObservableMap<String, String> getSummaries() {
    return summaries;
  }
}
