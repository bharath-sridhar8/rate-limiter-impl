package com.vj.rateLimiter.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/fixed")
public class FixedWindowCounterAlgorithm {
  
  private Map<String, AtomicInteger> currentWindow;
  private AtomicLong currentWindowStart = new AtomicLong();
  private AtomicLong currentWindowEnd = new AtomicLong();
  private final int MAX_REQUESTS = 10;
  private AtomicInteger totalCounter = new AtomicInteger(0);
  private AtomicInteger allowedCounter = new AtomicInteger(0);
  private AtomicInteger disallowedCounter = new AtomicInteger(0);

  @GetMapping("/check")
  public ResponseEntity<Map<String, String>> checkLimits(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    long nowMillis = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    if (nowMillis > currentWindowEnd.get()) {
      setWindow();
    }
    totalCounter.incrementAndGet();
    currentWindow.putIfAbsent(remoteAddr, new AtomicInteger(0));
    int prevValue = currentWindow.get(remoteAddr).getAndUpdate(i -> {
      if (i == MAX_REQUESTS)
        return MAX_REQUESTS;
      return i + 1;
    });
    
    if (prevValue == MAX_REQUESTS) {
      disallowedCounter.incrementAndGet();
      return new ResponseEntity<>(getWindowStats(), HttpStatus.TOO_MANY_REQUESTS);
    }
    allowedCounter.incrementAndGet();
    return new ResponseEntity<>(getWindowStats(), HttpStatus.OK);
  }
  
  @GetMapping(value = "/stats")
  public ResponseEntity<Map<String, String>> getStats() {
    Map<String, String> response = getWindowStats();
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  private Map<String, String> getWindowStats() {
    Map<String, String> response = new HashMap<>();
    response.put("Window Start", LocalDateTime.ofInstant(Instant.ofEpochMilli(currentWindowStart.get()), ZoneId.systemDefault()).toString());
    response.put("Window End", LocalDateTime.ofInstant(Instant.ofEpochMilli(currentWindowEnd.get()), ZoneId.systemDefault()).toString());
    response.put("Total", String.valueOf(totalCounter.get()));
    response.put("Allowed", String.valueOf(allowedCounter.get()));
    response.put("Disallowed", String.valueOf(disallowedCounter.get()));
    return response;
  }

  @PostConstruct
  public void init(){
    setWindow();
  }
  
  public synchronized void setWindow(){
    printWindowStats();
    LocalDateTime now = LocalDateTime.now();
    currentWindow = new ConcurrentHashMap<>();
    currentWindowStart.set(now.withSecond(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    currentWindowEnd.set(now.withSecond(0).plusMinutes(1).atZone(ZoneId.systemDefault())
        .toInstant().toEpochMilli());
    totalCounter = new AtomicInteger(0);
    allowedCounter = new AtomicInteger(0);
    disallowedCounter = new AtomicInteger(0);
  }

  private void printWindowStats() {
    if (currentWindow != null) {
      Map<String, String> response = getWindowStats();
      System.out.println(response);
    }
  }
}
