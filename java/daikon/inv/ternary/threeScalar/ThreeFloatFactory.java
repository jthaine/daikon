package daikon.inv.ternary.threeScalar;

import daikon.*;
import daikon.inv.*;

import utilMDE.*;

import java.util.*;


import org.apache.log4j.Logger;


public final class ThreeFloatFactory {

  /**
   * Debug tracer
   **/
  final static Logger debug = Logger.getLogger("daikon.inv.ternary.threeScalar.ThreeFloatFactory");


  public final static int max_instantiate
    =  ((FunctionsFloat.binarySymmetricFunctionNames.length
         * FunctionBinaryCoreFloat.order_symmetric_max)
        + (FunctionsFloat.binaryNonSymmetricFunctionNames.length
           * FunctionBinaryCoreFloat.order_nonsymmetric_max));

  // Add the appropriate new Invariant objects to the specified Invariants
  // collection.
  public static Vector instantiate(PptSlice ppt) {

    VarInfo var1 = ppt.var_infos[0];
    VarInfo var2 = ppt.var_infos[1];
    VarInfo var3 = ppt.var_infos[2];

    Assert.assertTrue((var1.rep_type == ProglangType.DOUBLE)
                  && (var2.rep_type == ProglangType.DOUBLE)
                  && (var3.rep_type == ProglangType.DOUBLE));

    if (debug.isDebugEnabled()) {
      debug.debug ("Instantiating for " + ppt.name);
      debug.debug ("Vars: " + var1.name + " " + var2.name + " " + var3.name);
    }

    if (! var1.compatible(var2)) {
      debug.debug ("Not comparable 1 to 2.  Returning");
      return null;
    }
    if (! var2.compatible(var3)) {
      debug.debug ("Not comparable 2 to 3.  Returning");
      return null;
    }
    // Check transitivity of "compatible" relationship.
    Assert.assertTrue(var1.compatible(var3));

    { // previously only if (pass == 2)
      // FIXME for equality
      Vector result = new Vector();
      for (int var_order = FunctionBinaryCoreFloat.order_symmetric_start;
           var_order <= FunctionBinaryCoreFloat.order_symmetric_max;
           var_order++) {
        for (int j=0; j<FunctionsFloat.binarySymmetricFunctionNames.length; j++) {
          FunctionBinaryFloat fb = FunctionBinaryFloat.instantiate(ppt, FunctionsFloat.binarySymmetricFunctionNames[j], j, var_order);
          // no need to increment noninstantiated-invariants counters if
          // null; they were already incremented.
          if (fb != null) {
            result.add(fb);
          }
        }
      }
      for (int var_order = FunctionBinaryCoreFloat.order_nonsymmetric_start;
           var_order <= FunctionBinaryCoreFloat.order_nonsymmetric_max;
           var_order++) {
        for (int j=0; j<FunctionsFloat.binaryNonSymmetricFunctionNames.length; j++) {
          FunctionBinaryFloat fb = FunctionBinaryFloat.instantiate(ppt, FunctionsFloat.binaryNonSymmetricFunctionNames[j], j+FunctionsFloat.binarySymmetricFunctionNames.length, var_order);
          // no need to increment noninstantiated-invariants counters if
          // null; they were already incremented.

          if (fb != null)
            result.add(fb);
        }
      }
      result.add(LinearTernaryFloat.instantiate(ppt));
      if (debug.isDebugEnabled()) {
        debug.debug ("Instantiated invs " + result);
      }
      return result;
    }
  }

  private ThreeFloatFactory() {
  }

}
