/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

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
    JmDNSProcess.start();
  }
}