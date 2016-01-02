package com.google.template.autoesc;

/**
 * A parse watcher that raises a
 * {@link StepLimitingParseWatcher.StepLimitExceededException}
 * if a parse takes more than a specified number of steps.
 */
public final class StepLimitingParseWatcher implements ParseWatcher {
  /** Step limit for grammar test cases if no step limit is specified. */
  public static final int DEFAULT_TEST_STEP_LIMIT = 4096;

  private int stepLimit = -1;

  /**
   * @param stepLimit -1 for unlimited or a maximum number of steps.
   */
  public void setStepLimit(int stepLimit) {
    this.stepLimit = stepLimit;
  }

  @Override
  public final void started(Parse p) {
    // Do nothing.
  }

  @Override
  public final void entered(Combinator c, Parse p) {
    decrLimit();
  }

  @Override
  public final void paused(Combinator c, Parse p) {
    decrLimit();
  }

  @Override
  public final void inputAdded(Parse p) {
    // Do nothing.
  }

  @Override
  public final void passed(Combinator c, Parse p) {
    decrLimit();
  }

  @Override
  public final void failed(Combinator c, Parse p) {
    decrLimit();
  }

  @Override
  public final void forked(Parse p, Branch from, Branch to) {
    // Do nothing
  }

  @Override
  public void joinStarted(Branch from, Branch to) {
    decrLimit();
  }

  @Override
  public void joinFinished(Parse p, Branch from, Parse q, Branch to) {
    // Do nothing
  }

  @Override
  public final void finished(Parse p, Branch b, Completion endState) {
    // Do nothing
  }

  void decrLimit() {
    if (this.stepLimit < 0) { return; }
    if (this.stepLimit == 0) {
      throw new StepLimitExceededException();
    }
    --this.stepLimit;
  }

  /**
   * Raised when the step limit is exceeded to abort a parse.
   */
  public final static class StepLimitExceededException
  extends RuntimeException {
    private static final long serialVersionUID = 4553858040836767948L;
  }
}
