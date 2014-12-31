package com.yogpc.ff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

public class Main extends DummyModContainer implements IFMLLoadingPlugin, IClassTransformer {
  private static final boolean TSL_DEBUG = Boolean.parseBoolean(System.getProperty(
      "yog.tessellator.debug", "false"));
  private static String tsl, potion, BGB, PE, potionAD, BGBAD;
  private static boolean initialized = false;
  private static final ModMetadata md = new ModMetadata();
  static {
    md.modId = "yogfixer";
    md.name = "YogFixer";
  }

  public Main() {
    super(md);
  }

  @Override
  public String[] getASMTransformerClass() {
    return new String[] {this.getClass().getName()};
  }

  public String[] getLibraryRequestClass() { // FIXME override version diff
    return null;
  }

  public String getAccessTransformerClass() { // FIXME override version diff
    return null;
  }

  @Override
  public String getModContainerClass() {
    return this.getClass().getName();
  }

  @Override
  public String getSetupClass() {
    return null;
  }

  @Override
  public void injectData(final Map<String, Object> data) {}

  private static final void init() {
    tsl = FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/client/renderer/Tessellator");
    potion = FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/potion/Potion");
    PE = FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/potion/PotionEffect");
    BGB = FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/world/biome/BiomeGenBase");
    potionAD = "[L" + potion + ";";
    BGBAD = "[L" + BGB + ";";
    initialized = true;
  }

  private static final void startDrawingFix(final ClassNode cn, final InsnList il) {
    // Add throwable field
    cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "prevT", "Ljava/lang/Throwable;", null, null));
    // Find throw pos
    AbstractInsnNode ain = il.getFirst();
    do {
      if (ain.getOpcode() != Opcodes.INVOKESPECIAL)
        continue;
      final MethodInsnNode min = (MethodInsnNode) ain;
      if (!"java/lang/IllegalStateException".equals(min.owner))
        continue;
      if (!"<init>".equals(min.name))
        continue;
      if (!"(Ljava/lang/String;)V".equals(min.desc))
        continue;
      ain = ain.getPrevious();
      il.remove(ain.getNext());
      break;
    } while ((ain = ain.getNext()) != null);
    if (ain == null)
      return;
    // Add cause to exception
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
        "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V"));
    il.insert(ain, new FieldInsnNode(Opcodes.GETFIELD, tsl, "prevT", "Ljava/lang/Throwable;"));
    il.insert(ain, new VarInsnNode(Opcodes.ALOAD, 0));
    // Skip stuff node
    while ((ain = ain.getNext()) != null)
      if (ain.getOpcode() == Opcodes.ATHROW)
        break;
    if (ain == null)
      return;
    while ((ain = ain.getNext()) != null)
      if (!(ain instanceof LabelNode))
        break;
    if (ain == null)
      return;
    // Add putting throwable field
    ain = ain.getPrevious();
    il.insert(ain, new FieldInsnNode(Opcodes.PUTFIELD, tsl, "prevT", "Ljava/lang/Throwable;"));
    il.insert(ain,
        new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V"));
    il.insert(ain, new InsnNode(Opcodes.DUP));
    il.insert(ain, new TypeInsnNode(Opcodes.NEW, "java/lang/Throwable"));
    il.insert(ain, new VarInsnNode(Opcodes.ALOAD, 0));
  }

  private static final byte[] tsl(final byte[] arg2) {
    if (!TSL_DEBUG)
      return arg2;
    final ClassNode cnode = new ClassNode();
    final ClassReader reader = new ClassReader(arg2);
    reader.accept(cnode, 0);
    mloop: for (final MethodNode mnode : cnode.methods) {
      if (!"(I)V".equals(mnode.desc))
        continue;
      if (mnode.name.startsWith("<"))
        continue;
      if ((mnode.access & Opcodes.ACC_PUBLIC) == 0)
        continue;
      final InsnList il = mnode.instructions;
      AbstractInsnNode iter = il.getFirst();
      do {
        if (iter.getOpcode() != Opcodes.NEW)
          continue;
        if ("java/lang/IllegalStateException".equals(((TypeInsnNode) iter).desc)) {
          startDrawingFix(cnode, il);
          break mloop;
        }
      } while ((iter = iter.getNext()) != null);
    }
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cnode.accept(cw);
    return cw.toByteArray();
  }

  private static final void add(final InsnList il, final String tu, final String owner,
      final String desc) {
    // Find array access
    AbstractInsnNode iter = il.getFirst();
    FieldInsnNode ain = null;
    do {
      if (iter.getOpcode() != Opcodes.GETSTATIC)
        continue;
      ain = (FieldInsnNode) iter;
      if (owner.equals(ain.owner) && desc.equals(ain.desc))
        break;
      ain = null;
    } while ((iter = iter.getNext()) != null);
    if (ain == null)
      return;
    // Add If Label
    final LabelNode ln = new LabelNode();
    il.insert(ain, new FieldInsnNode(ain.getOpcode(), ain.owner, ain.name, ain.desc));
    il.insert(ain, ln);
    // Throw exception
    il.insert(ain, new InsnNode(Opcodes.ATHROW));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException",
        "<init>", "(Ljava/lang/String;)V"));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
        "()Ljava/lang/String;"));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(I)Ljava/lang/StringBuilder;"));
    il.insert(ain, new VarInsnNode(Opcodes.ILOAD, 1));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
    il.insert(ain, new LdcInsnNode(" " + tu + " ID:"));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass",
        "()Ljava/lang/Class;"));
    il.insert(ain, new InsnNode(Opcodes.AALOAD));
    il.insert(ain, new VarInsnNode(Opcodes.ILOAD, 1));
    il.insert(ain, new FieldInsnNode(ain.getOpcode(), ain.owner, ain.name, ain.desc));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
    il.insert(ain, new LdcInsnNode(" and "));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
        "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass",
        "()Ljava/lang/Class;"));
    il.insert(ain, new VarInsnNode(Opcodes.ALOAD, 0));
    il.insert(ain, new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>",
        "(Ljava/lang/String;)V"));
    il.insert(ain, new LdcInsnNode("Duplicate " + tu.toLowerCase() + " id! "));
    il.insert(ain, new InsnNode(Opcodes.DUP));
    il.insert(ain, new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
    il.insert(ain, new InsnNode(Opcodes.DUP));
    il.insert(ain, new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalArgumentException"));
    // Check array null
    il.insert(ain, new JumpInsnNode(Opcodes.IFNULL, ln));
    il.insert(ain, new InsnNode(Opcodes.AALOAD));
    il.insert(ain, new VarInsnNode(Opcodes.ILOAD, 1));
  }

  private static final byte[] addw(final byte[] arg2, final String id, final String tu,
      final String owner, final String desc) {
    final ClassNode cnode = new ClassNode();
    final ClassReader reader = new ClassReader(arg2);
    reader.accept(cnode, 0);
    for (final MethodNode mnode : cnode.methods)
      if ("<init>".equals(mnode.name) && id.equals(mnode.desc))
        add(mnode.instructions, tu, owner, desc);
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cnode.accept(cw);
    return cw.toByteArray();
  }

  private static final byte[] wavefrontObject(final byte[] b) {
    final ClassNode c = new ClassNode();
    final ClassReader reader = new ClassReader(b);
    reader.accept(c, 0);
    final List<FieldNode> l = new ArrayList<FieldNode>(c.fields);
    c.fields.clear();
    for (final FieldNode f : l)
      if (!"Ljava/util/regex/Matcher;".equals(f.desc))
        c.fields.add(f);
    for (final MethodNode m : c.methods) {
      if (!"(Ljava/lang/String;)Z".equals(m.desc))
        continue;
      String o = null, n = null;
      AbstractInsnNode i = m.instructions.getFirst();
      while (i != null) {
        if (i instanceof FieldInsnNode) {
          final FieldInsnNode f = (FieldInsnNode) i;
          if ("Ljava/util/regex/Pattern;".equals(f.desc)) {
            o = f.owner;
            n = f.name;
          }
        }
        i = i.getNext();
      }
      if (o == null || n == null)
        continue;
      m.instructions.clear();
      m.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, o, n, "Ljava/util/regex/Pattern;"));
      m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
      m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/regex/Pattern",
          "matcher", "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"));
      m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/regex/Matcher",
          "matches", "()Z"));
      m.instructions.add(new InsnNode(Opcodes.IRETURN));
    }
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    c.accept(cw);
    return cw.toByteArray();
  }

  private static final byte[] potionEffect(final byte[] b) {
    final ClassNode c = new ClassNode();
    final ClassReader reader = new ClassReader(b);
    reader.accept(c, 0);
    for (final MethodNode m : c.methods)
      if ("(IIIZ)V".equals(m.desc) && "<init>".equals(m.name)) {
        final LabelNode ln = new LabelNode();
        m.instructions.insert(ln);
        m.instructions.insert(new VarInsnNode(Opcodes.ISTORE, 1));
        m.instructions.insert(new InsnNode(Opcodes.IADD));
        m.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 1));
        m.instructions.insert(new LdcInsnNode(new Integer(256)));
        m.instructions.insert(new JumpInsnNode(Opcodes.IFGE, ln));
        m.instructions.insert(new VarInsnNode(Opcodes.ILOAD, 1));
      }
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    c.accept(cw);
    return cw.toByteArray();
  }

  @Override
  public byte[] transform(final String arg0, final String arg1, final byte[] arg2) {
    if (!initialized)
      init();
    final String un = arg0.replace('.', '/');
    if (tsl.equals(un))
      return tsl(arg2);
    else if (potion.equals(un))
      return addw(arg2, "(IZI)V", "Potion", potion, potionAD);
    else if (BGB.equals(un))
      return addw(arg2, "(IZ)V", "Biome", BGB, BGBAD);
    else if (PE.equals(un))
      potionEffect(arg2);
    else if ("net/minecraftforge/client/model/obj/WavefrontObject".equals(un))
      return wavefrontObject(arg2);
    return arg2;
  }
}
