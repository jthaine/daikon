package daikon.derive.binary;

import daikon.*;
import daikon.inv.binary.twoScalar.*; // for IntComparison

import utilMDE.*;
import java.util.*;

// *****
// Automatically generated from SequenceSubscriptFactory.java.jpp
// *****

// This controls derivations which use the scalar as an index into the
// sequence, such as getting the element at that index or a subsequence up
// to that index.

public final class SequenceStringSubscriptFactory  extends BinaryDerivationFactory {

  // When calling/creating the derivations, arrange that:
  //   base1 is the sequence
  //   base2 is the scalar

  public BinaryDerivation[] instantiate(VarInfo vi1, VarInfo vi2) {
    // This is not the very most efficient way to do this, but at least it is
    // comprehensible.
    VarInfo seqvar;
    VarInfo sclvar;

    if ((vi1.rep_type == ProglangType.STRING_ARRAY )
        && (vi2.rep_type == ProglangType.STRING )) {
      seqvar = vi1;
      sclvar = vi2;
    } else if ((vi2.rep_type == ProglangType.STRING_ARRAY )
               && (vi1.rep_type == ProglangType.STRING )) {
      seqvar = vi2;
      sclvar = vi1;
    } else {
      return null;
    }

    if (! sclvar.isIndex())
      return null;
    // Could also do a Lackwit/Ajax comparability test here.

    // For now, do nothing if the sequence is itself derived.
    if (seqvar.derived != null)
      return null;
    // For now, do nothing if the scalar is itself derived.
    if (sclvar.derived != null)
      return null;

    Assert.assert(sclvar.isCanonical());

    VarInfo seqsize = seqvar.sequenceSize().canonicalRep();
    // System.out.println("BinaryDerivation.instantiate: sclvar=" + sclvar.name
    //                    + ", sclvar_rep=" + sclvar.canonicalRep().name
    //                    + ", seqsize=" + seqsize.name
    //                    + ", seqsize_rep=" + seqsize.canonicalRep().name);
    // Since both are canonical, this is equivalent to
    // "if (sclvar.canonicalRep() == seqsize.canonicalRep()) ..."
    if (sclvar == seqsize) {
      // a[len] a[len-1] a[0..len] a[0..len-1] a[len..] a[len+1..]
      Global.tautological_suppressed_derived_variables += 6;
      return null;
    }

    // ***** This eliminates the derivation if it can *ever* be
    // nonsensical/missing.  Is that what I want?

    // Find an IntComparison relationship over the scalar and the sequence
    // size, if possible.
    Assert.assert(sclvar.ppt == seqsize.ppt);
    PptSlice compar_slice = sclvar.ppt.getView(sclvar, seqsize);
    if (compar_slice != null) {
      IntComparison compar = IntComparison.find(compar_slice);
      if (compar != null) {
        if ((sclvar.varinfo_index < seqsize.varinfo_index)
            ? compar.core.can_be_gt // sclvar can be more than seqsize
            : compar.core.can_be_lt // seqsize can be less than sclvar
            ) {
          Global.nonsensical_suppressed_derived_variables += 6;
          return null;
        }
      }
    }

    // Abstract out these next two.

    // If the scalar is a constant < 0:
    //   all derived variables are nonsensical
    // If the scalar is the constant 0:
    //   array[0] is already extracted
    //   array[-1] is nonsensical
    //   array[0..0] is already extracted
    //   array[0..-1] is nonsensical
    //   array[0..] is the same as array[]
    //   array[1..] should be extracted
    // If the scalar is the constant 1:
    //   array[1] is already extracted
    //   array[0] is already extracted
    //   array[0..1] should be extracted
    //   array[0..0] is already extracted
    //   array[1..] should be extracted
    //   array[2..] should be extracted
    if (sclvar.isConstant()) {
      long scl_constant = ((Long) sclvar.constantValue()).longValue();
      // System.out.println("It is constant (" + scl_constant + "): " + sclvar.name);
      if (scl_constant < 0) {
        Global.nonsensical_suppressed_derived_variables += 6;
	return null;
      }
      if (scl_constant == 0) {
        Global.tautological_suppressed_derived_variables += 3;
        Global.nonsensical_suppressed_derived_variables += 2;
        return new BinaryDerivation[] {
          new SequenceStringSubsequence (seqvar, sclvar, false, true),
        };
      }
      if (scl_constant == 1) {
        Global.tautological_suppressed_derived_variables += 3;
        return new BinaryDerivation[] {
          new SequenceStringSubsequence (seqvar, sclvar, true, false),
          new SequenceStringSubsequence (seqvar, sclvar, false, false),
          new SequenceStringSubsequence (seqvar, sclvar, false, true),
        };
      }
    }

    // Get the lower and upper bounds for the variable, if any.
    // [This nedds to be written.]

    // If, for some canonical j, j=index+1, don't create array[index+1..].
    // If j=index-1, then don't create array[index-1] or array[0..index-1].
    // (j can have higher or lower VarInfo index than i.)
    boolean suppress_minus_1 = false;
    boolean suppress_plus_1 = false;

    // This ought to be abstracted out, maybe
    {
      Assert.assert(sclvar.ppt == seqvar.ppt);
      Vector lbs = LinearBinary.findAll(sclvar);
      // System.out.println("For " + sclvar.name + ", " + lbs.size() + " LinearBinary invariants");
      for (int i=0; i<lbs.size(); i++) {
        LinearBinary lb = (LinearBinary) lbs.elementAt(i);
        if (lb.core.a == 1) {
          // Don't set unconditionally, and don't break:  we want to check
          // other variables as well.
          if (lb.core.b == -1) {
            if (lb.var1() == sclvar)
              suppress_minus_1 = true;
            else
              suppress_plus_1 = true;
          }
          if (lb.core.b == 1) {
            if (lb.var1() == sclvar)
              suppress_minus_1 = true;
            else
              suppress_plus_1 = true;
          }
          // System.out.println("For " + sclvar.name + " suppression: "
          //                    + "minus=" + suppress_minus_1
          //                    + " plus=" + suppress_plus_1
          //                    + " because of " + lb.format());
        }
      }
    }

    if (suppress_minus_1) {
      Global.tautological_suppressed_derived_variables += 2;
    }
    if (suppress_plus_1) {
      Global.tautological_suppressed_derived_variables += 1;
    }

    // End of applicability tests; now actually create the invariants

    Vector result = new Vector(6);
    // a[i]
    result.add(new SequenceStringSubscript (seqvar, sclvar, false));
    // a[i-1]
    if (! suppress_minus_1)
      result.add(new SequenceStringSubscript (seqvar, sclvar, true));
    // a[i..]
    result.add(new SequenceStringSubsequence (seqvar, sclvar, false, false));
    // a[i+1..]
    if (! suppress_plus_1)
      result.add(new SequenceStringSubsequence (seqvar, sclvar, false, true));
    // a[..i]
    result.add(new SequenceStringSubsequence (seqvar, sclvar, true, false));
    // a[..i-1]
    if (! suppress_minus_1)
      result.add(new SequenceStringSubsequence (seqvar, sclvar, true, true));

    return (BinaryDerivation[]) result.toArray(new BinaryDerivation[0]);
  }

}

