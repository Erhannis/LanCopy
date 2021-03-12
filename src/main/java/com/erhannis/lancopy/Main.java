/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.mathnstuff.utils.ObservableMap;
import com.erhannis.mathnstuff.utils.ObservableMap.Change;
import java.util.HashMap;
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
      ObservableMap<String, String> om = new ObservableMap<String, String>();
      om.subscribe((Change<String, String> change) -> {
        if (change.wasRemoved) {
          System.out.println("removed key: " + change.key);
          System.out.println("removed val: " + change.valueRemoved);
        }
        if (change.wasAdded) {
          System.out.println("added key: " + change.key);
          System.out.println("added val: " + change.valueAdded);
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
    
    Frame.main(args);
  }
}
