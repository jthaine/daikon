// ***** This file is automatically generated from SequenceScalarFactory.java.jpp

package daikon.inv.binary.sequenceScalar;

import daikon.*;

import java.util.*;
import org.apache.log4j.Logger;

public final class SequenceScalarFactory {

  /** Main debug tracer **/
  public static final Logger debug =
   Logger.getLogger("daikon.inv.binary.sequenceScalar.SequenceScalarFactory");

  // public final static boolean debugSequenceScalarFactory = false;
  // public final static boolean debugSequenceScalarFactory = true;

  // Adds the appropriate new Invariant objects to the specified Invariants
  // collection.
  public static Vector instantiate(PptSlice ppt) {
    if (debug.isDebugEnabled()) {
      debug.debug("SequenceScalarFactory instantiate " + ppt.name);
    }

    boolean seq_first;

    VarInfo seqvar;
    VarInfo sclvar;
    {
      VarInfo vi0 = ppt.var_infos[0];
      VarInfo vi1 = ppt.var_infos[1];
      if ((vi0.rep_type == ProglangType.INT_ARRAY)
          && (vi1.rep_type == ProglangType.INT)) {
        seq_first = true;
        seqvar = ppt.var_infos[0];
        sclvar = ppt.var_infos[1];
      } else if ((vi0.rep_type == ProglangType.INT)
                 && (vi1.rep_type == ProglangType.INT_ARRAY)) {
        seq_first = false;
        seqvar = ppt.var_infos[1];
        sclvar = ppt.var_infos[0];
      } else {
        throw new Error("Bad types");
      }
    }

    if (! seqvar.eltsCompatible(sclvar)) {
      debug.debug("Elements not compatible, returning");
      return null;
    }

    Vector result = new Vector();
    // I could check that the length of the sequence isn't always 0.
    result.add(Member.instantiate(ppt, seq_first));
    result.add(SeqIntComparison.instantiate(ppt, seq_first));
    return result;
  }

  private SequenceScalarFactory() {
  }

}
