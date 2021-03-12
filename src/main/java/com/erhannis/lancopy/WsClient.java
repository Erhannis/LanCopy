/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

/**
 * Registers with remote services to listen for remote summary updates.
 * 
 * @author erhannis
 */
public class WsClient {
  private final DataOwner dataOwner;

  public WsClient(DataOwner dataOwner) {
    this.dataOwner = dataOwner;
  }
}
