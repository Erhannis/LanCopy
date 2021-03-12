/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.mathnstuff.utils.Observable;
import com.erhannis.mathnstuff.utils.ObservableMap;
import javax.jmdns.ServiceInfo;

/**
 *
 * @author erhannis
 */
public class DataOwner {
  public static final int SUMMARY_LENGTH = 50;
  
  public final Observable<String> localSummary = new Observable<>();
  public final Observable<String> localData = new Observable<>();
  public final ObservableMap<String, String> remoteSummaries = new ObservableMap<>();
  public final ObservableMap<String, ServiceInfo> remoteServices = new ObservableMap<>();
  
  public DataOwner() {
    localData.subscribe((data) -> {
      String summary = data.substring(0, Math.min(data.length(), SUMMARY_LENGTH));
      localSummary.set(summary);
    });
  }
}
