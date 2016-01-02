package com.google.template.autoesc.var;

import com.google.template.autoesc.out.Output;

/** An output marker related to a variable. */
public interface VariableOutput extends Output {
  /** The associated variable. */
  Variable<?> getVariable();
}