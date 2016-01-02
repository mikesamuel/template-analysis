package com.google.template.autoesc.demo;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.autoesc.Language;
import com.google.template.autoesc.ProdName;
import com.google.template.autoesc.StepLimitingParseWatcher;
import com.google.template.autoesc.demo.BranchRunner.Branch;

/**
 * A query parameter name recognized by {@link DemoServer}.
 */
public enum DemoServerQuery {
  /**
   * Specifies how to find the language.
   * Value has form <i>Class</i><tt>.</tt><i>Field</i>
   * {@code com.google.template.autoesc.grammars.Html.LANG}.
   */
  LANG("lang"),
  /**
   * A maximum count of steps before execution is terminated.
   * @see StepLimitingParseWatcher
   */
  STEP_LIMIT("stepLimit"),
  /** The name of the start production to use. */
  START_PROD("start"),
  /** If present, then {@link #LANG} will be optimized. */
  OPTIMIZED("optimized"),

  /**
   * False/0/no to not
   * {@link com.google.template.autoesc.Parser#finishParse finish} the parse.
   */
  FINISH_PARSE("finish"),

  /**
   * Value is the ID of the branch that {@link #INPUT} and {@link #PRECEDES}
   * statements affect.
   */
  BRANCH("branch"),
  /**
   * May be specified multiple times to provide multiple input chunks for the
   * current branch.
   * Value is a string of raw input chars.
  */
  INPUT("input"),
  /**
   * May be specified multiple times to specify that the current branch
   * precedes the branch id that is the value.
   */
  PRECEDES("precedes"),
  ;

  /** String that appears in the URL query. */
  public final String queryParameterName;

  DemoServerQuery(String queryParameterName) {
    this.queryParameterName = queryParameterName;
  }

  private static final ImmutableMap<String, DemoServerQuery> BY_PARAM_NAME;

  static {
    ImmutableMap.Builder<String, DemoServerQuery> b = ImmutableMap.builder();
    for (DemoServerQuery q : values()) {
      b.put(q.queryParameterName, q);
    }
    BY_PARAM_NAME = b.build();
  }

  /** {@code x == fromQueryParam(x.queryParameterName)}. */
  public static DemoServerQuery fromQueryParam(String queryParamKey) {
    return Preconditions.checkNotNull(
        BY_PARAM_NAME.get(queryParamKey), queryParamKey);
  }


  /** A builder for a query string. */
  public static Builder builder() { return builder(""); }

  /** A builder for a query string that builds upon an earlier string. */
  public static Builder builder(String queryString) {
    return new Builder(queryString);
  }

  /** A builder for a query string. */
  public static final class Builder {
    private final StringBuilder sb = new StringBuilder();
    private final Map<BranchRunner.Branch<?>, Integer> branchToId
        = new LinkedHashMap<>();

    Builder(String query) {
      sb.append(query);
    }

    private void addField(DemoServerQuery key, @Nullable String value) {
      try {
        if (sb.length() != 0) { sb.append('&'); }
        String name = key.queryParameterName;
        sb.append(URLEncoder.encode(name, "UTF-8"));
        if (value != null) {
          sb.append('=');
          sb.append(URLEncoder.encode(value, "UTF-8"));
        }
      } catch (UnsupportedEncodingException ex) {
        throw new AssertionError(ex);
      }
    }

    /**
     * A public static Java field of type
     * {@link com.google.template.autoesc.Language} that contains the grammar to
     * parse with.
     */
    public Builder grammarField(Field field) {
      Preconditions.checkArgument(
          Modifier.isStatic(field.getModifiers())
          && Modifier.isPublic(field.getModifiers())
          && Language.class.isAssignableFrom(field.getType()));
      addField(
          LANG, field.getDeclaringClass().getName() + "." + field.getName());
      return this;
    }

    /**
     * Use the optimized version of the language.
     * @see DemoServerQuery#OPTIMIZED
     */
    public Builder optimized() {
      addField(OPTIMIZED, null);
      return this;
    }

    /**
     * The maximum number of execution steps.
     * @see DemoServerQuery#STEP_LIMIT
     */
    public Builder stepLimit(int stepLimit) {
      Preconditions.checkArgument(stepLimit >= -1);
      addField(STEP_LIMIT, "" + stepLimit);
      return this;
    }

    /**
     * The start production name.
     * @see DemoServerQuery#START_PROD
     */
    public Builder startProd(ProdName startProduction) {
      addField(START_PROD, startProduction.text);
      return this;
    }

    /**
     * Use the forking/joining structure from branch to define
     * a multi-stage parse run.
     */
    public Builder branch(BranchRunner.Branch<?> branch) {
      if (!branchToId.containsKey(branch)) {
        branchToId.put(branch, branchToId.size());
        defBranch(branch);
      }
      return this;
    }

    private void defBranch(BranchRunner.Branch<?> branch) {
      int branchId = branchToId.get(branch);
      addField(BRANCH, "" + branchId);
      for (BranchRunner.StringSourcePair input : branch.inputs) {
        addField(INPUT, input.text);
      }
      List<Branch<?>> toDefine = new ArrayList<>();
      for (Branch<?> follower : branch.followers) {
        int followerBranchId;
        if (branchToId.containsKey(follower)) {
          followerBranchId = branchToId.get(follower);
        } else {
          followerBranchId = branchToId.size();
          branchToId.put(follower, followerBranchId);
          toDefine.add(follower);
        }
        addField(PRECEDES, "" + followerBranchId);
      }
      for (Branch<?> undefined : toDefine) {
        defBranch(undefined);
      }
    }

    /**
     * @return a URL query string.
     */
    public String build() {
      return sb.toString();
    }
  }
}
