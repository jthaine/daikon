package daikon.diff;

import daikon.inv.*;
import daikon.inv.Invariant.OutputFormat;
import daikon.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import utilMDE.*;
import gnu.getopt.*;


/**
 * Diff is the main class for the invariant diff program.  The
 * invariant diff program outputs the differences between two sets of
 * invariants.
 *
 * The following is a high-level description of the program.  Each
 * input file contains a serialized PptMap or InvMap.  PptMap and
 * InvMap are similar structures, in that they both map program points
 * to invariants.  However, PptMaps are much more complicated than
 * InvMaps.  PptMaps are output by Daikon, and InvMaps are output by
 * this program.
 *
 * First, if either input is a PptMap, it is converted to an InvMap.
 * Next, the two InvMaps are combined to form a tree.  The tree is
 * exactly three levels deep.  The first level contains the root,
 * which holds no data.  Each node in the second level is a pair of
 * Ppts, and each node in the third level is a pair of Invariants.
 * The tree is constructed by pairing the corresponding Ppts and
 * Invariants in the two PptMaps.  Finally, the tree is traversed via
 * the Visitor pattern to produce output.  The Visitor pattern makes
 * it easy to extend the program, simply by writing a new Visitor.
 **/
public final class Diff {

  private static String usage =
    UtilMDE.join(new String[] {
      "Usage:",
      "    java daikon.diff.Diff [flags...] file1 [file2]",
      "  file1 and file2 are serialized invariants produced by Daikon.",
      "  If file2 is not specified, file1 is compared with the empty set.",
      "  For a list of flags, see the Daikon manual, which appears in the ",
      "  Daikon distribution and also at http://pag.lcs.mit.edu/daikon/."},
                 Global.lineSep);

  // added to disrupt the tree when bug hunting -LL
  private static boolean treeManip = false;

  // this is set only when the manip flag is set "-z"
  private static PptMap manip1 = null;
  private static PptMap manip2 = null;

  /** The long command line options **/
  private static final String INV_SORT_COMPARATOR1_SWITCH =
    "invSortComparator1";
  private static final String INV_SORT_COMPARATOR2_SWITCH =
    "invSortComparator2";
  private static final String INV_PAIR_COMPARATOR_SWITCH =
    "invPairComparator";
  private static final String IGNORE_UNJUSTIFIED_SWITCH =
    "ignore_unjustified";



  /** Determine which ppts should be paired together in the tree. **/
  private static final Comparator PPT_COMPARATOR = new Ppt.NameComparator();

  /**
   * Comparators to sort the sets of invs, and to combine the two sets
   * into the pair tree.  Can be overriden by command-line options.
   **/
  private Comparator invSortComparator1;
  private Comparator invSortComparator2;
  private Comparator invPairComparator;

  private boolean examineAllPpts;

  public Diff() {
    this(false);
    setAllInvComparators(new Invariant.ClassVarnameComparator());
  }

  public Diff(boolean examineAllPpts) {
    this.examineAllPpts = examineAllPpts;
  }

  /**
   * Read two PptMap or InvMap objects from their respective files.
   * Convert the PptMaps to InvMaps as necessary, and diff the
   * InvMaps.
   **/
  public static void main(String[] args) throws FileNotFoundException,
  StreamCorruptedException, OptionalDataException, IOException,
  ClassNotFoundException, InstantiationException, IllegalAccessException {

    boolean printDiff = false;
    boolean printUninteresting = false;
    boolean printAll = false;
    boolean includeUnjustified = true;
    boolean stats = false;
    boolean tabSeparatedStats = false;
    boolean minus = false;
    boolean xor = false;
    boolean union = false;
    boolean examineAllPpts = false;
    boolean printEmptyPpts = false;
    boolean verbose = false;
    boolean continuousJustification = false;
    boolean logging = false;
    File outputFile = null;
    String invSortComparator1Classname = null;
    String invSortComparator2Classname = null;
    String invPairComparatorClassname = null;

    boolean optionSelected = false;

    //    daikon.LogHelper.setupLogs (daikon.LogHelper.INFO);

    LongOpt[] longOpts = new LongOpt[] {
      new LongOpt(INV_SORT_COMPARATOR1_SWITCH,
                  LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(INV_SORT_COMPARATOR2_SWITCH,
                  LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(INV_PAIR_COMPARATOR_SWITCH,
                  LongOpt.REQUIRED_ARGUMENT, null, 0),
      new LongOpt(IGNORE_UNJUSTIFIED_SWITCH,
                  LongOpt.NO_ARGUMENT, null, 0),
    };

    Getopt g = new Getopt("daikon.diff.Diff", args,
                          "Hhyduastmxno:jzpevl", longOpts);
    int c;
    while ((c = g.getopt()) !=-1) {
      switch (c) {
      case 0:
        // got a long option
        String optionName = longOpts[g.getLongind()].getName();
        if (INV_SORT_COMPARATOR1_SWITCH.equals(optionName)) {
          if (invSortComparator1Classname != null) {
            throw new Error("multiple --" + INV_SORT_COMPARATOR1_SWITCH +
                            " classnames supplied on command line");
          }
          invSortComparator1Classname = g.getOptarg();
        } else if (INV_SORT_COMPARATOR2_SWITCH.equals(optionName)) {
          if (invSortComparator2Classname != null) {
            throw new Error("multiple --" + INV_SORT_COMPARATOR2_SWITCH +
                            " classnames supplied on command line");
          }
          invSortComparator2Classname = g.getOptarg();
        } else if (INV_PAIR_COMPARATOR_SWITCH.equals(optionName)) {
          if (invPairComparatorClassname != null) {
            throw new Error("multiple --" + INV_PAIR_COMPARATOR_SWITCH +
                            " classnames supplied on command line");
          }
          invPairComparatorClassname = g.getOptarg();
        } else if (IGNORE_UNJUSTIFIED_SWITCH.equals(optionName)) {
          optionSelected = true;
          includeUnjustified = false;
          break;
        } else {
          throw new RuntimeException("Unknown long option received: " +
                                     optionName);
        }
        break;
      case 'h':
        System.out.println(usage);
        System.exit(1);
        break;
      case 'H':
        PrintAllVisitor.HUMAN_OUTPUT = true;
        break;
      case 'y':  // included for legacy code
        optionSelected = true;
        includeUnjustified = false;
        break;
      case 'd':
        optionSelected = true;
        printDiff = true;
        break;
      case 'u':
        printUninteresting = true;
        break;
      case 'a':
        optionSelected = true;
        printAll = true;
        break;
      case 's':
        optionSelected = true;
        stats = true;
        break;
      case 't':
        optionSelected = true;
        tabSeparatedStats = true;
        break;
      case 'm':
        optionSelected = true;
        minus = true;
        break;
      case 'x':
        optionSelected = true;
        xor = true;
        break;
      case 'n':
        optionSelected = true;
        union = true;
        break;
      case 'o':
        if (outputFile != null) {
          throw new Error
            ("multiple output files supplied on command line");
        }
        String outputFilename = g.getOptarg();
        outputFile = new File(outputFilename);
        if (! UtilMDE.canCreateAndWrite(outputFile)) {
          throw new Error("Cannot write to file " + outputFile);
        }
        break;
      case 'j':
        continuousJustification = true;
        break;
      case 'z':
        treeManip = true;
        // no break on purpose, only makes sense
        // if -p is also on -LL
      case 'p':
        examineAllPpts = true;
        break;
      case 'e':
        printEmptyPpts = true;
        break;
      case 'v':
        verbose = true;
        break;
      case 'l':
        logging = true;
        break;
      case '?':
        // getopt() already printed an error
        System.out.println(usage);
        System.exit(1);
        break;
      default:
        System.out.println("getopt() returned " + c);
        break;
      }
    }

    // Turn on the defaults
    if (! optionSelected) {
      printDiff = true;
    }

    if (logging)
      System.err.println("Invariant Diff: Starting Log");

    if (logging)
      System.err.println("Invariant Diff: Creating Diff Object");

    Diff diff = new Diff(examineAllPpts);

    // Set the comparators based on the command-line options

    Comparator defaultComparator;
    if (minus || xor || union) {
      defaultComparator = new Invariant.ClassVarnameFormulaComparator();
    } else {
      defaultComparator = new Invariant.ClassVarnameComparator();
    }
    diff.setInvSortComparator1
      (selectComparator(invSortComparator1Classname, defaultComparator));
    diff.setInvSortComparator2
      (selectComparator(invSortComparator2Classname, defaultComparator));
    diff.setInvPairComparator
      (selectComparator(invPairComparatorClassname, defaultComparator));

    if ((!(diff.invSortComparator1.getClass().toString().equals
           (diff.invSortComparator2.getClass().toString()))) ||
        (!(diff.invSortComparator1.getClass().toString().equals
           (diff.invPairComparator.getClass().toString())))) {
      String warning = "You are using different comparators to sort or " +
        "pair up invariants.\nThis may cause misalignment of invariants " +
        "and may cause Diff to\nwork incorectly.  Make sure you know what " +
        "you are doing!\n";
      System.out.println(warning);
    }

    // The index of the first non-option argument -- the name of the
    // first file
    int firstFileIndex = g.getOptind();
    int numFiles = args.length - firstFileIndex;

    InvMap invMap1 = null;
    InvMap invMap2 = null;

    if (logging)
      System.err.println("Invariant Diff: Reading Files");

    if (numFiles == 1) {
      String filename1 = args[firstFileIndex];
      invMap1 = diff.readInvMap(new File(filename1));
      invMap2 = new InvMap();
    } else if (numFiles == 2) {
      String filename1 = args[firstFileIndex];
      String filename2 = args[firstFileIndex + 1];
      invMap1 = diff.readInvMap(new File(filename1));
      invMap2 = diff.readInvMap(new File(filename2));
    } else if (treeManip) {
      System.out.println ("Warning, the preSplit file must be second");
      if (numFiles < 3) {
        System.out.println
          ("Sorry, no manip file [postSplit] [preSplit] [manip]");
      }
      String filename1 = args[firstFileIndex];
      String filename2 = args[firstFileIndex + 1];
      String filename3 = args[firstFileIndex + 2];
      String filename4 = args[firstFileIndex + 3];
      PptMap map1 = FileIO.read_serialized_pptmap(new File(filename1),
                                                  false // use saved config
                                                  );
      PptMap map2 = FileIO.read_serialized_pptmap(new File(filename2),
                                                  false // use saved config
                                                  );
      manip1 = FileIO.read_serialized_pptmap(new File(filename3),
                                             false // use saved config
                                             );
      manip2 = FileIO.read_serialized_pptmap(new File(filename4),
                                             false // use saved config
                                             );

      // get the xor from these two manips
      treeManip = false;


      // RootNode pass_and_both = diff.diffPptMap (manip1, map2, includeUnjustified);
      // RootNode fail_and_both = diff.diffPptMap (manip2, map2, includeUnjustified);


      // get rid of the "both" invariants
      // MinusVisitor2 aMinusB = new MinusVisitor2();
      //      pass_and_both.accept (aMinusB);
      // fail_and_both.accept (aMinusB);


      RootNode pass_and_fail = diff.diffPptMap (manip1, manip2, includeUnjustified);



      XorInvariantsVisitor xiv = new XorInvariantsVisitor(System.out,
                                                          false,
                                                          false,
                                                          false);
      pass_and_fail.accept (xiv);

      // remove for the latest version
      treeManip = true;

      // form the root with tree manips
      RootNode root = diff.diffPptMap (map1, map2, includeUnjustified);


      // now run the stats visitor for checking matches
      //      MatchCountVisitor2 mcv = new MatchCountVisitor2
      //  (System.out, verbose, false);
      /*
      PptCountVisitor mcv = new PptCountVisitor
        (System.out, verbose, false);

      root.accept (mcv);
      mcv.printFinal();
      System.out.println ("Precison: " + mcv.calcPrecision());
      System.out.println ("Recall: " + mcv.calcRecall());
      System.out.println ("Success");
      //      System.exit(0);
      */




      MatchCountVisitor2 mcv2 = new MatchCountVisitor2
        (System.out, verbose, false);

      root.accept (mcv2);
      // print final is simply for debugging, remove
      // when experiments are over.
      mcv2.printFinal();

      // Most of the bug-experiments expect the final output
      // of the Diff to be these three lines.  It is best
      // not to change it.
      System.out.println ("Precison: " + mcv2.calcPrecision());
      System.out.println ("Recall: " + mcv2.calcRecall());
      System.out.println ("Success");
      System.exit(0);

    } else if (numFiles > 2) {

      // The new stuff that allows multiple files -LL


      PptMap[] mapAr = new PptMap[numFiles];
      int j = 0;
      for (int i = firstFileIndex; i < args.length; i++) {
        String fileName = args[i];
        mapAr[j++] = FileIO.read_serialized_pptmap(new File (fileName),
                                                   false);
      }

      // Cascade a lot of the different invariants into one map,
      // and then put them into map1, map2

      // Initialize it all
      RootNode root = null;
      MultiDiffVisitor v1 = new MultiDiffVisitor (mapAr[0]);

      for (int i = 1; i < mapAr.length; i++) {
        root = diff.diffPptMap (mapAr[i], v1.currMap, includeUnjustified);
        root.accept (v1);
      }

      // now take the final result for the MultiDiffVisitor
      // and use it along side a null empty map
      PptMap map1 = v1.currMap;
      PptMap map2 = new PptMap();

      v1.printAll();
      return;
    } else {
      System.out.println (usage);
      System.exit(0);
    }

    if (logging)
      System.err.println("Invariant Diff: Creating Tree");

    if (logging)
      System.err.println("Invariant Diff: Visiting Tree");

    RootNode root = diff.diffInvMap(invMap1, invMap2, includeUnjustified);

    if (stats) {
      DetailedStatisticsVisitor v =
        new DetailedStatisticsVisitor(continuousJustification);
      root.accept(v);
      System.out.print(v.format());
    }

    if (tabSeparatedStats) {
      DetailedStatisticsVisitor v =
        new DetailedStatisticsVisitor(continuousJustification);
      root.accept(v);
      System.out.print(v.repr());
    }

    if (printDiff) {
      PrintDifferingInvariantsVisitor v = new PrintDifferingInvariantsVisitor
        (System.out, verbose, printEmptyPpts, printUninteresting);
      root.accept(v);
    }

    if (printAll) {
      PrintAllVisitor v = new PrintAllVisitor
        (System.out, verbose, printEmptyPpts);
      root.accept(v);
    }

    if (minus) {
      if (outputFile != null) {
        MinusVisitor v = new MinusVisitor();
        root.accept(v);
        UtilMDE.writeObject(v.getResult(), outputFile);
        // System.out.println("Output written to: " + outputFile);
      } else {
        throw new Error("no output file specified on command line");
      }
    }

    if (xor) {
      if (outputFile != null) {
        XorVisitor v = new XorVisitor();
        root.accept(v);
        UtilMDE.writeObject(v.getResult(), outputFile);
        // System.out.println("Output written to: " + outputFile);
      } else {
        throw new Error("no output file specified on command line");
      }
    }

    if (union) {
      if (outputFile != null) {
        UnionVisitor v = new UnionVisitor();
        root.accept(v);
        UtilMDE.writeObject(v.getResult(), outputFile);
        // System.out.println("Output written to: " + outputFile);
      } else {
        throw new Error("no output file specified on command line");
      }
    }


    if (logging)
      System.err.println("Invariant Diff: Ending Log");

    System.exit(0);
  }

  /**
   * Reads an InvMap from a file that contains a serialized InvMap or
   * PptMap
   **/
  private InvMap readInvMap(File file) throws
  IOException, ClassNotFoundException {
    Object o = UtilMDE.readObject(file);
    if (o instanceof InvMap) {
      return (InvMap) o;
    } else {
      PptMap pptMap = FileIO.read_serialized_pptmap(file, false);
      return convertToInvMap(pptMap);
    }
  }

  /**
   * Extracts the PptTopLevel and Invariants out of a pptMap, and
   * places them into an InvMap.  Maps PptTopLevel to a List of
   * Invariants.  The InvMap is a cleaner representation than the
   * PptMap, and it allows us to more easily manipulate the contents.
   * The InvMap contains exactly the elements contained in the PptMap.
   * Conditional program points are also added as keys.  Filtering is
   * done when creating the pair tree.  The ppts in the InvMap must be
   * sorted, but the invariants need not be sorted.
   **/
  public InvMap convertToInvMap(PptMap pptMap) {
    InvMap map = new InvMap();

    // Created sorted set of top level ppts, possibly including
    // conditional ppts
    SortedSet ppts = new TreeSet(PPT_COMPARATOR);
    ppts.addAll(pptMap.asCollection());

    for (Iterator i = ppts.iterator(); i.hasNext(); ) {
      PptTopLevel ppt = (PptTopLevel) i.next();
      // List invs = ppt.getInvariants();
      List invs = UtilMDE.sortList(ppt.getInvariants(), PptTopLevel.icfp);
      map.put(ppt, invs);
      if (examineAllPpts) {
        // Add conditional ppts
        for (Iterator i2 = ppt.views_cond.iterator(); i2.hasNext(); ) {
          PptConditional pptCond = (PptConditional) i2.next();
          List invsCond = UtilMDE.sortList (pptCond.getInvariants(),
                                          PptTopLevel.icfp);
          // List invsCond = pptCond.getInvariants();
          map.put(pptCond, invsCond);
        }
      }
    }
    return map;
  }

  /**
   * Returns a pair tree of corresponding program points, and
   * corresponding invariants at each program point.  This tree can be
   * walked to determine differences between the sets of invariants.
   * Calls diffInvMap and asks to include all justified invariants
   **/
  public RootNode diffInvMap(InvMap map1, InvMap map2) {
    return diffInvMap(map1, map2, true);
  }

  /**
   * Returns a pair tree of corresponding program points, and
   * corresponding invariants at each program point.  This tree can be
   * walked to determine differences between the sets of invariants.
   * The tree consists of the invariants in map1 and map2.  If
   * includeUnjustified is true, the unjustified invariants are included.
   **/
  public RootNode diffInvMap(InvMap map1, InvMap map2,
                             boolean includeUnjustified) {
    RootNode root = new RootNode();

    Iterator opi = new OrderedPairIterator(map1.pptSortedIterator(PPT_COMPARATOR), map2.pptSortedIterator(PPT_COMPARATOR), PPT_COMPARATOR);
    while (opi.hasNext()) {
      Pair ppts = (Pair) opi.next();
      PptTopLevel ppt1 = (PptTopLevel) ppts.a;
      PptTopLevel ppt2 = (PptTopLevel) ppts.b;
      if (shouldAdd(ppt1) || shouldAdd(ppt2)) {
        PptNode node = diffPptTopLevel(ppt1, ppt2, map1, map2,
                                       includeUnjustified);
        root.add(node);
      }
    }

    return root;
  }


  /**
   * Diffs two PptMaps by converting them to InvMaps.  Provided for
   * compatibiliy with legacy code.
   * Calls diffPptMap and asks to include all invariants.
   **/
  public RootNode diffPptMap(PptMap pptMap1, PptMap pptMap2) {
    return diffPptMap(pptMap1, pptMap2, true);
  }

  /**
   * Diffs two PptMaps by converting them to InvMaps.  Provided for
   * compatibiliy with legacy code.
   * If includeUnjustified is true, the unjustified invariants are included.
   **/
  public RootNode diffPptMap(PptMap pptMap1, PptMap pptMap2,
                             boolean includeUnjustified) {
    InvMap map1 = convertToInvMap(pptMap1);
    InvMap map2 = convertToInvMap(pptMap2);
    return diffInvMap(map1, map2, includeUnjustified);
  }

  /**
   * Returns true if the program point should be added to the tree,
   * false otherwise.
   **/
  private boolean shouldAdd(PptTopLevel ppt) {
    if (examineAllPpts) {
      return true;
    } else {
      if (ppt == null) {
        return false;
      } else if (ppt.ppt_name.isEnterPoint()) {
        return true;
      } else if (ppt.ppt_name.isCombinedExitPoint()) {
        return true;
      } else {
        return false;
      }
    }
  }

  /**
   * Takes a pair of corresponding top-level program points and maps,
   * and returns a tree of the corresponding invariants.  Either of
   * the program points may be null.
   * If includeUnjustied is true, the unjustified invariants are included.
   **/
  private PptNode diffPptTopLevel(PptTopLevel ppt1, PptTopLevel ppt2,
                                  InvMap map1, InvMap map2,
                                  boolean includeUnjustified) {
    PptNode pptNode = new PptNode(ppt1, ppt2);

    Assert.assertTrue(ppt1 == null || ppt2 == null ||
                      PPT_COMPARATOR.compare(ppt1, ppt2) == 0,
                      "Program points do not correspond");

    List invs1;
    if (ppt1 != null && !treeManip) {
      invs1 = (List) map1.get(ppt1);
      Collections.sort(invs1, invSortComparator1);
    }

    else if (ppt1 != null && treeManip && !isCond(ppt1)) {
      HashSet repeatFilter = new HashSet();
      ArrayList ret = new ArrayList ();
      invs1 = (List) map1.get(ppt1);
      for (Iterator j = invs1.iterator(); j.hasNext(); ) {
        Invariant inv = (Invariant)  j.next();
        if (/*inv.justified() && */inv instanceof Implication) {
          Implication imp = (Implication) inv;
          if (!repeatFilter.contains (imp.consequent().format_using(OutputFormat.JAVA))) {
            repeatFilter.add (imp.consequent().format_using(OutputFormat.JAVA));
            ret.add (imp.consequent());
          }
          // add both sides of a biimplication
          if (imp.iff == true) {
            if (!repeatFilter.contains(imp.predicate().format())) {
              repeatFilter.add (imp.predicate().format());
              ret.add (imp.predicate());
            }
          }
        }
        // Report invariants that are not part of implications
        // "as is".
        else {
          ret.add (inv);
        }
      }
      invs1 = ret;
      Collections.sort(invs1, invSortComparator1);
    }

    else {
      invs1 = Collections.EMPTY_LIST;
    }

    List invs2;
    if (ppt2 != null && !treeManip) {
      invs2 = (List) map2.get(ppt2);
      Collections.sort(invs2, invSortComparator2);
    } else {
      if ( false && treeManip && isCond (ppt1)) {
        // remember, only want to mess with the second list
        invs2 = findCondPpt (manip1, ppt1);
        List tmpList = findCondPpt (manip2, ppt1);

        invs2.addAll (tmpList);

        // This uses set difference model instead of XOR
        //        invs2 = tmpList;

        // must call sort or it won't work!
        Collections.sort(invs2, invSortComparator2);
      }
      else if (treeManip && ppt2 != null && !isCond(ppt2)) {

        invs2 = findNormalPpt (manip1, ppt2);
        invs2.addAll ( findNormalPpt (manip2, ppt2));
        Collections.sort (invs2, invSortComparator2);
      }
      else {
        invs2 = Collections.EMPTY_LIST;
      }
    }

    Iterator opi = new OrderedPairIterator(invs1.iterator(), invs2.iterator(),
                                           invPairComparator);
    while (opi.hasNext()) {
      Pair invariants = (Pair) opi.next();
      Invariant inv1 = (Invariant) invariants.a;
      Invariant inv2 = (Invariant) invariants.b;
      if (!includeUnjustified) {
        if ((inv1 != null) && !(inv1.justified())) {
          inv1 = null;
        }
        if ((inv2 != null) && !(inv2.justified())) {
          inv2 = null;
        }
      }
      if ((inv1 != null) || (inv2 != null)) {
        InvNode invNode = new InvNode(inv1, inv2);
        pptNode.add(invNode);
      }
    }


    return pptNode;
  }

  private boolean isCond (PptTopLevel ppt) {
    return (ppt instanceof PptConditional);
  }

  private List findCondPpt (PptMap manip, PptTopLevel ppt) {
    // targetName should look like this below
    // Contest.smallestRoom(II)I:::EXIT9;condition="max < num
    String targetName = ppt.name;

    String targ = targetName.substring (0, targetName.lastIndexOf(";condition"));

    for ( Iterator i = manip.nameStringSet().iterator(); i.hasNext(); ) {
      String somePptName = (String) i.next();
      // A conditional Ppt always contains the normal Ppt
      if (targ.equals (somePptName)) {
        PptTopLevel repl = manip.get (somePptName);
        return repl.getInvariants();
      }
    }
    //    System.out.println ("Could not find the left hand side of implication!!!");
    System.out.println ("LHS Missing: " + targ);
    return Collections.EMPTY_LIST;
  }


  private List findNormalPpt (PptMap manip, PptTopLevel ppt) {
    // targetName should look like this below
    // Contest.smallestRoom(II)I:::EXIT9
    String targetName = ppt.name;

    //    String targ = targetName.substring (0, targetName.lastIndexOf(";condition"));

    for ( Iterator i = manip.nameStringSet().iterator(); i.hasNext(); ) {
      String somePptName = (String) i.next();
      // A conditional Ppt always contains the normal Ppt
      if (targetName.equals (somePptName)) {
        PptTopLevel repl = manip.get (somePptName);
        return UtilMDE.sortList(repl.getInvariants(), PptTopLevel.icfp);
      }
    }
    //    System.out.println ("Could not find the left hand side of implication!!!");
    System.out.println ("LHS Missing: " + targetName);
    return Collections.EMPTY_LIST;
  }


  /**
   * Use the comparator for sorting both sets and creating the pair
   * tree.
   **/
  public void setAllInvComparators(Comparator c) {
    setInvSortComparator1(c);
    setInvSortComparator2(c);
    setInvPairComparator(c);
  }

  /**
   * If the classname is non-null, returns the comparator named by the
   * classname.  Else, returns the default.
   **/
  private static Comparator selectComparator
    (String classname, Comparator defaultComparator) throws
    ClassNotFoundException, InstantiationException, IllegalAccessException {

    if (classname != null) {
      Class cls = Class.forName(classname);
      Comparator cmp = (Comparator) cls.newInstance();
      return cmp;
    } else {
      return defaultComparator;
    }
  }

  /** Use the comparator for sorting the first set. **/
  public void setInvSortComparator1(Comparator c) {
    invSortComparator1 = c;
  }

  /** Use the comparator for sorting the second set. **/
  public void setInvSortComparator2(Comparator c) {
    invSortComparator2 = c;
  }

  /** Use the comparator for creating the pair tree. **/
  public void setInvPairComparator(Comparator c) {
    invPairComparator = c;
  }

}
