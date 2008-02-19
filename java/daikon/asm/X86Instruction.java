package daikon.asm;

import java.util.*;

/**
 * Represents an x86 instruction.
 */
public class X86Instruction implements IInstruction {

  private String dllName;

  private String address;

  private String opName;

  private List<String> args;

  private List<String> killedVars;

  protected boolean isFirstInBlock = false;

  // For debugging printing purposes.
  // The name of the basic block that contains this instruction.
  public String owner = null;

  // See method parseInstruction. It sets all fields appropriately.
  private X86Instruction() {
  }

  public String getOpName() {
    return opName;
  }

  public List<String> getArgs() {
    return args;
  }

  public Set<String> getBinaryVarNames() {
    Set<String> retval = new LinkedHashSet<String>();

    for (String s : args) {

      if (Operand.isConstant(s))
        continue;
      if (Operand.isDerefdNumber(s))
        continue;
      if (Operand.is8BitReg(s))
        continue;
      if (Operand.is16BitReg(s))
        continue;
      if (Operand.isFPUReg(s))
        continue;

      // If the string looks like this: [0+eax+(edx*4)]
      // return the string without brackets, and return eax.
      if (Operand.isDeref(s)) {
        List<String> regs = Operand.getExtendedRegisters(s);
        assert regs.size() == 1 || regs.size() == 2 : s;

        if (regs.size() == 1) {
          if (opName.equals("lea")) {
            retval.add(s.substring(1, s.length() - 1));
            continue;
          }
        } else { // regs.size() == 2
          retval.add(regs.get(1));
          retval.add(s.substring(1, s.length() - 1));
          if (!opName.equals("lea"))
            retval.add(s);
          continue;
        }
      }

      if (s.equals("esp")) {
        if (isFirstInBlock)
          retval.add(s);
        else if (opName.equals("call") || opName.equals("call_ind"))
          retval.add(s);
        else
          continue;
      }

      retval.add(s);
    }
    return retval;
  }

  /*
   * (non-Javadoc)
   *
   * @see daikon.IInstruction#toString()
   */
  public String toString() {
    StringBuilder b = new StringBuilder();
    // b.append(owner != null ? owner + ":" : "");
    // b.append(dllName);
    // b.append(":");
    b.append(address);
    b.append(" ");
    b.append(opName);
    for (String a : args) {
      b.append(" ");
      b.append(a);
    }
    if (killedVars.isEmpty())
      return b.toString();

    b.append(" -> ");
    for (String a : killedVars) {
      b.append(" ");
      b.append(a);
    }
    return b.toString();
  }

  /**
   * <dll name>:<address> <op name> <arg> ... <arg> -> <result var>
   */
  public static X86Instruction parseInstruction(String s) {
    if (s == null)
      throw new IllegalArgumentException("String cannot be null.");
    String[] tokens = s.trim().split("\\s+");
    if (tokens.length < 2)
      throw new IllegalArgumentException("Invalid instruction string: " + s);

    X86Instruction inst = new X86Instruction();

    // Set dllName and address fields.
    String[] dllAddr = tokens[0].split(":");
    if (dllAddr.length != 2)
      throw new IllegalArgumentException("Invalid instruction string: " + s);
    if (!(dllAddr[0].endsWith(".dll") || dllAddr[0].endsWith(".exe")))
      throw new IllegalArgumentException("Invalid instruction string: " + s);
    inst.dllName = dllAddr[0];
    if (!dllAddr[1].startsWith("0x"))
      throw new IllegalArgumentException("Invalid instruction string: " + s);
    try {
      Long.parseLong(dllAddr[1].substring(2), 16);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid instruction string: " + s);
    }
    inst.address = dllAddr[1];

    // Set opName field.
    if (!isValidOp(tokens[1]))
      throw new IllegalArgumentException("Invalid instruction string: " + s);
    inst.opName = tokens[1];

    // Set args
    inst.args = new ArrayList<String>();
    int i = 2;
    for (; i < tokens.length; i++) {
      if (tokens[i].equals("->")) {
        if (tokens.length < i + 2) // There should be at least one resultVar.
          throw new IllegalArgumentException("Invalid instruction string: " + s);
        i++; // Move i to first result var.
        break;
      }
      if (!isValidLHSOp(tokens[i]))
        throw new IllegalArgumentException("Invalid instruction string: " + s);
      inst.args.add(tokens[i]);
    }

    // Set resultVars.
    inst.killedVars = new ArrayList<String>();
    for (; i < tokens.length; i++) {
      if (!isValidRHSVar(tokens[i])) {
        throw new IllegalArgumentException("Invalid instruction string: " + s);
      }
      inst.killedVars.add(tokens[i]);
    }

    return inst;
  }

  // TODO fill in if necessary.
  private static boolean isValidOp(String string) {
    return true;
  }

  private static boolean isValidRHSVar(String s) {
    if (Operand.isRegister(s))
      return true;
    if (Operand.isDeref(s))
      return true;
    return false;
  }

  // Refine if needed.
  private static boolean isValidLHSOp(String s) {
    if (Operand.isConstant(s))
      return true;
    if (Operand.isRegister(s))
      return true;
    if (Operand.isDeref(s))
      return true;
    return false;
  }

  private static Set<String> varsUnmodifiedByCallOp;
  static {
    varsUnmodifiedByCallOp = new LinkedHashSet<String>();
    varsUnmodifiedByCallOp.add("ebx");
    varsUnmodifiedByCallOp.add("esi");
    varsUnmodifiedByCallOp.add("edi");
    varsUnmodifiedByCallOp.add("ebp");
  }


  // Only works for extended registers. This should
  // be fine because we only compute variables over
  // extended registers.
  public boolean kills(String var) {

    assert Operand.isRegister(var) ? Operand.isExtendedReg(var) : true;

    if (opName.startsWith("call")) {
      return !varsUnmodifiedByCallOp.contains(var);
    }

    if (var.equals("eax")) {
      if (killedVars.contains(var) || killedVars.contains("ax")
          || killedVars.contains("ah") || killedVars.contains("al"))
        return true;
      else
        return false;
    }

    if (var.equals("ebx")) {
      if (killedVars.contains(var) || killedVars.contains("bx")
          || killedVars.contains("bh") || killedVars.contains("bl"))
        return true;
      else
        return false;
    }

    if (var.equals("ecx")) {
      if (killedVars.contains(var) || killedVars.contains("cx")
          || killedVars.contains("ch") || killedVars.contains("cl"))
        return true;
      else
        return false;
    }

    if (var.equals("edx")) {
      if (killedVars.contains(var) || killedVars.contains("dx")
          || killedVars.contains("dh") || killedVars.contains("dl"))
        return true;
      else
        return false;
    }

    if (var.equals("edi")) {
      if (killedVars.contains(var) || killedVars.contains("di"))
        return true;
      else
        return false;
    }

    if (var.equals("esi")) {
      if (killedVars.contains(var) || killedVars.contains("si"))
        return true;
      else
        return false;
    }

    if (var.equals("esp")) {
      if (killedVars.contains(var) || killedVars.contains("sp"))
        return true;
      else
        return false;
    }

    if (var.equals("ebp")) {
      if (killedVars.contains(var) || killedVars.contains("bp"))
        return true;
      else
        return false;
    }

    if (Operand.isRegister(var)) {
      return killedVars.contains(var);
    }

    if (Operand.isDeref(var)) {
      if (killedVars.contains(var))
        return true;
      for (String killedVar : killedVars) {
        if (Operand.isDeref(killedVar))
          return true;
      }
      for (String reg : Operand.getExtendedRegisters(var)) {
        if (killedVars.contains(reg))
          return true;
      }
      return false;
    }

    // It may be something like "16+ebx". Do a quick sanity check.
    if (var.indexOf('+') != -1) {
      for (String reg : Operand.getExtendedRegisters(var)) {
        if (killedVars.contains(reg))
          return true;
      }
    }

    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @see daikon.IInstruction#getAddress()
   */
  public String getAddress() {
    return address;
  }

}
