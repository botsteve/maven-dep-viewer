package com.botsteve.mavendepsearcher.exception;

public class DepViewerException extends RuntimeException {

  public DepViewerException(String message, Throwable cause) {
    super(message, cause);
  }

  public DepViewerException(String message) {
    super(message);
  }

  public DepViewerException(Throwable cause) {
    super(cause);
  }
}
