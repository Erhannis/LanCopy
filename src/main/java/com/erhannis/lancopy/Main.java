/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import java.util.HashMap;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener.Change;
import javafx.collections.ObservableMap;
import javax.jmdns.JmDNS;

/**
 *
 * @author erhannis
 */
public class Main {
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    if (1 == 0) {
      /*
      Test results: OM does not fire if the map is updated to be the same as it was - ignores false changes
      */
      ObservableMap<String, String> om = FXCollections.observableMap(new HashMap<String, String>());
      om.addListener((Change<? extends String, ? extends String> change) -> {
        if (change.wasRemoved()) {
          System.out.println("removed key: " + change.getKey());
          System.out.println("removed val: " + change.getValueRemoved());
        }
        if (change.wasAdded()) {
          System.out.println("added key: " + change.getKey());
          System.out.println("added val: " + change.getValueAdded());
        }
      });

      om.put("a", "1");
      om.put("a", "2");
      om.put("b", "1");
      om.put("b", "1");
      om.put("b", "1");
      om.put("b", "1");
      if (1 == 1) {
        return;
      }
    }

    JmDNSProcess.start();
  }
}
