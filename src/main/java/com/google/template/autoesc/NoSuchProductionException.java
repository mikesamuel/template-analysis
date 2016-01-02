package com.google.template.autoesc;

/**
 * Raised when trying to access a production that has not been defined.
 */
public class NoSuchProductionException extends RuntimeException {
  private static final long serialVersionUID = -8267672189841945325L;

  /** The name of the production sought. */
  public final ProdName name;

  /** */
  public NoSuchProductionException(ProdName name) {
    this.name = name;
  }
}
