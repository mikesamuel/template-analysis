package com.google.template.autoesc.file;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.autoesc.out.Output;
import com.google.template.autoesc.out.PartialOutput;
import com.google.template.autoesc.out.PartialOutput.StandalonePartialOutput;
import com.google.template.autoesc.out.StringOutput;

/**
 * An output processor that processes leaves in the parse-tree by examining
 * their textual content.
 */
abstract class StringOutputProcessor<T> extends OutputProcessor<T> {
  @Override
  final Optional<T> pre(ImmutableList<PartialOutput> body, TreeProcessor t) {
    StringBuilder s = new StringBuilder();
    StringBuilder rawChars = new StringBuilder();
    for (PartialOutput po : body) {
      if (po instanceof StandalonePartialOutput) {
        StandalonePartialOutput spo = (StandalonePartialOutput) po;
        Output o = spo.output;
        if (spo.output instanceof StringOutput) {
          StringOutput so = (StringOutput) o;
          s.append(so.s);
          rawChars.append(so.rawChars);
          continue;
        }
      }
      return Optional.absent();
    }
    return process(s.toString(), rawChars.toString(), t);
  }

  @Override
  Optional<T> post(ImmutableList<Object> children, TreeProcessor t) {
    return Optional.absent();
  }

  abstract Optional<T> process(String s, String rawChars, TreeProcessor t);
}
