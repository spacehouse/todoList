package com.todolist.gui.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 class 字节码扫描器：解析常量池与方法 Code 属性，
 * 收集指定方法体内的 invoke 指令引用（owner 内部类名 + 方法名），
 * 供离线测试做渲染路径守卫断言，不依赖外部字节码处理库。
 *
 * <p>已知局限：仅在目标方法自身字节码内线性扫描，不追踪 lambda 合成方法与
 * 被内联私有方法体外的引用；扫描按方法名匹配，不做描述符级签名比对。
 */
public final class ClassFileMethodScanner {

    /**
     * 方法调用引用记录：owner 为 JVM 内部类名（斜杠分隔），name 为方法名。
     */
    public record MethodRef(String owner, String name) {
    }

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ClassFileMethodScanner() {
    }

    /**
     * 收集指定方法体内的全部 invoke 引用（同名重载合并扫描）。
     *
     * @param clazz 目标类
     * @param methodName 方法名
     * @return invoke 引用列表
     */
    public static List<MethodRef> findInvokes(Class<?> clazz, String methodName) {
        return parse(readClassBytes(clazz), methodName, null);
    }

    /**
     * 收集类内全部方法体的 invoke 引用。
     *
     * @param clazz 目标类
     * @return invoke 引用列表
     */
    public static List<MethodRef> findAllInvokes(Class<?> clazz) {
        return parse(readClassBytes(clazz), null, null);
    }

    /**
     * 收集指定方法体内 ldc/ldc_w 指令加载的整数常量（同名重载合并扫描）。
     * 颜色字面量（如 0xFFFFFF）超出 bipush/sipush 值域必然经 ldc 加载，
     * 因此本方法足以覆盖大整数颜色常量的收集需求；掩码用途的常量
     * （ldc 后紧跟 iand，如 color &amp; 0x00FFFFFF）已被过滤，不算入结果。
     *
     * @param clazz 目标类
     * @param methodName 目标方法名，null 表示扫描全部方法
     * @return ldc 加载的整数常量列表（含重复出现）
     */
    public static List<Integer> findIntConstants(Class<?> clazz, String methodName) {
        List<Integer> constants = new ArrayList<>();
        parse(readClassBytes(clazz), methodName, constants);
        return constants;
    }

    /**
     * 读取类对应的 class 资源字节。
     *
     * @param clazz 目标类
     * @return class 字节
     */
    private static byte[] readClassBytes(Class<?> clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = clazz.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("找不到类资源: " + resource);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("读取类资源失败: " + resource, e);
        }
    }

    /**
     * 解析 class 字节并收集目标方法体内的 invoke 引用与 ldc 整数常量。
     *
     * @param b class 字节
     * @param targetMethod 目标方法名，null 表示扫描全部方法
     * @param intConsts ldc 整数常量收集列表，null 表示不收集
     * @return invoke 引用列表
     */
    private static List<MethodRef> parse(byte[] b, String targetMethod, List<Integer> intConsts) {
        Reader r = new Reader(b);
        r.u4(); // magic
        r.u2(); // minor_version
        r.u2(); // major_version
        int cpCount = r.u2();
        int[] tags = new int[cpCount];
        int[] utf8Offset = new int[cpCount];
        int[] utf8Length = new int[cpCount];
        int[] classUtf8Index = new int[cpCount];
        int[] refClassIndex = new int[cpCount];
        int[] refNameAndTypeIndex = new int[cpCount];
        int[] natNameIndex = new int[cpCount];
        int[] intValues = new int[cpCount];
        for (int i = 1; i < cpCount; i++) {
            int tag = r.u1();
            tags[i] = tag;
            switch (tag) {
                case 1 -> { // Utf8
                    int len = r.u2();
                    utf8Offset[i] = r.pos;
                    utf8Length[i] = len;
                    r.skip(len);
                }
                case 3 -> intValues[i] = r.u4(); // Integer
                case 4 -> r.skip(4); // Float
                case 5, 6 -> { // Long / Double：8 字节且占双槽
                    r.skip(8);
                    i++;
                }
                case 7 -> classUtf8Index[i] = r.u2(); // Class
                case 8, 16 -> r.skip(2); // String / MethodType
                case 9, 10, 11 -> { // Fieldref / Methodref / InterfaceMethodref
                    refClassIndex[i] = r.u2();
                    refNameAndTypeIndex[i] = r.u2();
                }
                case 12 -> { // NameAndType
                    natNameIndex[i] = r.u2();
                    r.skip(2);
                }
                case 15 -> r.skip(3); // MethodHandle
                case 17, 18 -> r.skip(4); // Dynamic / InvokeDynamic
                case 19, 20 -> r.skip(2); // Module / Package
                default -> throw new IllegalStateException("未知常量池 tag: " + tag);
            }
        }
        r.u2(); // access_flags
        r.u2(); // this_class
        r.u2(); // super_class
        int interfaceCount = r.u2();
        r.skip(interfaceCount * 2L);
        skipMembers(r); // fields
        int methodCount = r.u2();
        List<MethodRef> refs = new ArrayList<>();
        for (int m = 0; m < methodCount; m++) {
            r.u2(); // access_flags
            int nameIndex = r.u2();
            r.u2(); // descriptor_index
            int attrCount = r.u2();
            String methodName = utf8(b, utf8Offset, utf8Length, nameIndex);
            boolean scan = targetMethod == null || targetMethod.equals(methodName);
            for (int a = 0; a < attrCount; a++) {
                int attrNameIndex = r.u2();
                int attrLength = r.u4();
                String attrName = utf8(b, utf8Offset, utf8Length, attrNameIndex);
                if (scan && "Code".equals(attrName)) {
                    scanCode(r, b, tags, utf8Offset, utf8Length, classUtf8Index,
                            refClassIndex, refNameAndTypeIndex, natNameIndex, intValues, refs, intConsts);
                } else {
                    r.skip(attrLength);
                }
            }
        }
        return refs;
    }

    /**
     * 跳过字段或方法表结构之外的字段成员部分（字段与方法结构同构）。
     *
     * @param r 字节读取器
     */
    private static void skipMembers(Reader r) {
        int count = r.u2();
        for (int i = 0; i < count; i++) {
            r.u2(); // access_flags
            r.u2(); // name_index
            r.u2(); // descriptor_index
            int attrCount = r.u2();
            for (int a = 0; a < attrCount; a++) {
                r.u2(); // attribute_name_index
                r.skip(r.u4());
            }
        }
    }

    /**
     * 扫描 Code 属性内的字节码，收集 invoke 指令引用与 ldc 整数常量。
     * 线性扫描 opcode 流，遇到 invoke 系指令按常量池索引解析 Methodref/InterfaceMethodref，
     * 遇到 ldc/ldc_w 且常量池条目为 Integer 时记录其值；
     * 数据字节被误判为 opcode 时会因常量池 tag 校验被自然过滤。
     *
     * @param r 字节读取器（当前位于 Code 属性 body 起点）
     * @param b class 字节
     * @param tags 常量池 tag 表
     * @param utf8Offset UTF8 条目偏移表
     * @param utf8Length UTF8 条目长度表
     * @param classUtf8Index Class 条目名索引表
     * @param refClassIndex Methodref 条目类索引表
     * @param refNameAndTypeIndex Methodref 条目 NameAndType 索引表
     * @param natNameIndex NameAndType 条目名索引表
     * @param intValues Integer 条目值表
     * @param refs 输出引用收集列表
     * @param intConsts 输出 ldc 整数常量收集列表，null 表示不收集
     */
    private static void scanCode(Reader r, byte[] b, int[] tags, int[] utf8Offset, int[] utf8Length,
                                 int[] classUtf8Index, int[] refClassIndex,
                                 int[] refNameAndTypeIndex, int[] natNameIndex, int[] intValues,
                                 List<MethodRef> refs, List<Integer> intConsts) {
        r.u2(); // max_stack
        r.u2(); // max_locals
        int codeLength = r.u4();
        int codeStart = r.pos;
        r.skip(codeLength);
        byte[] code = new byte[codeLength];
        System.arraycopy(b, codeStart, code, 0, codeLength);
        int i = 0;
        while (i < code.length) {
            int op = code[i] & 0xFF;
            if (op == 0xB6 || op == 0xB7 || op == 0xB8 || op == 0xBA) {
                // invokevirtual / invokespecial / invokestatic / invokedynamic：u2 常量池索引
                if (i + 2 < code.length) {
                    int idx = ((code[i + 1] & 0xFF) << 8) | (code[i + 2] & 0xFF);
                    collectRef(b, idx, tags, utf8Offset, utf8Length, classUtf8Index,
                            refClassIndex, refNameAndTypeIndex, natNameIndex, refs);
                }
                i += 3;
            } else if (op == 0xB9) {
                // invokeinterface：u2 常量池索引 + u2 count + u1 zero
                if (i + 2 < code.length) {
                    int idx = ((code[i + 1] & 0xFF) << 8) | (code[i + 2] & 0xFF);
                    collectRef(b, idx, tags, utf8Offset, utf8Length, classUtf8Index,
                            refClassIndex, refNameAndTypeIndex, natNameIndex, refs);
                }
                i += 5;
            } else if (op == 0x12 && intConsts != null) {
                // ldc：u1 常量池索引；ldc 后紧跟 iand 说明是掩码用途（color & 0x00FFFFFF），
                // 属正当位运算而非颜色字面量，跳过不收集
                if (i + 1 < code.length && (i + 2 >= code.length || (code[i + 2] & 0xFF) != 0x7E)) {
                    collectInt(code[i + 1] & 0xFF, tags, intValues, intConsts);
                }
                i += 2;
            } else if (op == 0x13 && intConsts != null) {
                // ldc_w：u2 常量池索引；同样过滤后随 iand 的掩码用途
                if (i + 2 < code.length && (i + 3 >= code.length || (code[i + 3] & 0xFF) != 0x7E)) {
                    int idx = ((code[i + 1] & 0xFF) << 8) | (code[i + 2] & 0xFF);
                    collectInt(idx, tags, intValues, intConsts);
                }
                i += 3;
            } else {
                i++;
            }
        }
        // 剩余异常表与属性长度由调用方未知，这里已把 r 推进到 code 结束，
        // 跳过异常表与 Code 子属性以保持读取器位置正确
        int exceptionCount = r.u2();
        r.skip(exceptionCount * 8L);
        int subAttrCount = r.u2();
        for (int a = 0; a < subAttrCount; a++) {
            r.u2();
            r.skip(r.u4());
        }
    }

    /**
     * 按常量池索引收集 Integer 常量（仅 tag==Integer 时记录）。
     *
     * @param idx 常量池索引
     * @param tags 常量池 tag 表
     * @param intValues Integer 条目值表
     * @param intConsts 输出收集列表
     */
    private static void collectInt(int idx, int[] tags, int[] intValues, List<Integer> intConsts) {
        if (idx <= 0 || idx >= tags.length || tags[idx] != 3) {
            return;
        }
        intConsts.add(intValues[idx]);
    }

    /**
     * 按常量池索引解析方法引用并追加到收集列表。
     *
     * @param idx 常量池索引
     * @param tags 常量池 tag 表
     * @param utf8Offset UTF8 条目偏移表
     * @param utf8Length UTF8 条目长度表
     * @param classUtf8Index Class 条目名索引表
     * @param refClassIndex Methodref 条目类索引表
     * @param refNameAndTypeIndex Methodref 条目 NameAndType 索引表
     * @param natNameIndex NameAndType 条目名索引表
     * @param refs 输出引用收集列表
     */
    private static void collectRef(byte[] b, int idx, int[] tags, int[] utf8Offset, int[] utf8Length,
                                   int[] classUtf8Index, int[] refClassIndex,
                                   int[] refNameAndTypeIndex, int[] natNameIndex, List<MethodRef> refs) {
        if (b == null || idx <= 0 || idx >= tags.length) {
            return;
        }
        int tag = tags[idx];
        if (tag != 10 && tag != 11) {
            return;
        }
        String owner = utf8(b, utf8Offset, utf8Length, classUtf8Index[refClassIndex[idx]]);
        String name = utf8(b, utf8Offset, utf8Length, natNameIndex[refNameAndTypeIndex[idx]]);
        if (owner != null && name != null) {
            refs.add(new MethodRef(owner, name));
        }
    }

    /**
     * 读取常量池 UTF8 条目内容（方法名等 ASCII 内容按 ISO-8859-1 解码即可）。
     *
     * @param b class 字节
     * @param utf8Offset UTF8 条目偏移表
     * @param utf8Length UTF8 条目长度表
     * @param index 常量池索引
     * @return UTF8 内容
     */
    private static String utf8(byte[] b, int[] utf8Offset, int[] utf8Length, int index) {
        if (b == null || index <= 0 || index >= utf8Offset.length) {
            return null;
        }
        int offset = utf8Offset[index];
        int length = utf8Length[index];
        return new String(b, offset, length, StandardCharsets.ISO_8859_1);
    }

    /**
     * class 字节大端序读取器。
     */
    private static final class Reader {
        private final byte[] b;
        private int pos;

        /**
         * 创建读取器。
         *
         * @param b class 字节
         */
        private Reader(byte[] b) {
            this.b = b;
        }

        /**
         * 读取 u1。
         *
         * @return 无符号单字节
         */
        private int u1() {
            return b[pos++] & 0xFF;
        }

        /**
         * 读取 u2。
         *
         * @return 无符号双字节
         */
        private int u2() {
            int v = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        /**
         * 读取 u4。
         *
         * @return 无符号四字节
         */
        private int u4() {
            int v = ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16)
                    | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        /**
         * 跳过指定字节数。
         *
         * @param count 字节数
         */
        private void skip(long count) {
            pos += (int) count;
        }
    }
}
