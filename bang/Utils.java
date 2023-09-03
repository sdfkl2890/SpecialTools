package bang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.ClassNode;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    private static HashMap<Method,InvocationHandler2> handlers = new HashMap<>();
    static ScriptEngine scriptEngine;
    static InvocationHandler proxyHandler;


    static {
        scriptEngine = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            InvocationHandler[] handlers = (InvocationHandler[]) scriptEngine.eval("var InvocationHandler = java.lang.reflect.InvocationHandler;\n" +
                    "var Utils = Java.type(\"bang.Utils\");\n" +
                    "var ProxyHandler = Java.extend(InvocationHandler,{\n" +
                    "\tinvoke: function(invocation,method,args){\n" +
                    "\t\tvar target = invocation.getTarget();\n" +
                    "\t\tvar clToExtend = target.getClass().static;\n" +
                    "\t\tvar object = {};\n" +
                    "\t\tobject[method.getName()] = function(){\n" +
                    "\t\t\tinvocation.invoke(null,method,Java.to(arguments, \"java.lang.Object[]\"));\n" +
                    "\t\t};\n" +
                    "\t\tvar extendedClass = Java.extend(clToExtend,object);\n" +
                    "\t\treturn Utils.convertFatherToSon(extendedClass.class,target,null,null);\n" +
                    "\t}\n" +
                    "});\n" +
                    "\n" +
                    "Java.to([new ProxyHandler()],\"java.lang.reflect.InvocationHandler[]\");");
            proxyHandler = handlers[0];
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }


    /*
     * 判断当前方法是否被某类的某方法调用
     * Args:
     *   className: Class,某类名称,如java.lang.String
     *   methodName: String,某方法名称
     * Returns boolean
     */
    public static boolean isInvoke(String className, String methodName) {
        Exception exception = new Exception(className + "'s " + methodName + " cannot invoke this method");
        StackTraceElement[] stackTraceElement = exception.getStackTrace();
        for (StackTraceElement stack : stackTraceElement) {
            if (stack.getClassName().equals(className)) {
                if (methodName.equals("*")) {//表示类的所有方法
                    return true;
                } else {
                    if (stack.getMethodName().equals(methodName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    /**
     * 判断某类某方法体中是否调用了某某方法
     *
     * @param cl:          Class,某类
     * @param methodName:  String,某方法名称
     * @param methodDesc:  String,某方法描述符(参数类型)
     * @param owner:       String,调用某某方法的类签名,如java/lang/String
     * @param methodName2: String,某某方法名称
     * @param methodDesc2: String,某某方法描述符
     * @return boolean
     * @throws Exception
     */
    public static boolean containsMethod(Class<?> cl, String methodName, String methodDesc, String owner, String methodName2, String methodDesc2) throws Exception {
        ClassReader cr = new ClassReader(cl.getName());
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        MyClassVisitor cv = new MyClassVisitor(Opcodes.ASM5, methodName, methodDesc, owner, methodName2, methodDesc2);
        cr.accept(cv, ClassReader.SKIP_DEBUG);
        if (cv.adapter != null) {
            return cv.adapter.contains;
        }
        return false;
    }

    //Gson序列化及反序列化策略
    static MyExclusionStrategy strategy = new MyExclusionStrategy();
    static Gson gson = new GsonBuilder().setExclusionStrategies(strategy).create();


    /**
     * 将父类对象转换其子类对象
     *
     * @param sonClass:       Class,子类
     * @param instance:       Object,父类对象
     * @param fieldsToIgnore: List<String>,序列化时忽略的属性,可为null
     * @param fieldsToPut:    Map<String,Object>,赋值给子类对象的属性,可为null
     * @return 子类对象
     */
    public static <T, E extends T> E convertFatherToSon(Class<E> sonClass, T instance, List<String> fieldsToIgnore, Map<String, Object> fieldsToPut) {//被proxyHandler调用
        strategy.fieldsToIgnore = fieldsToIgnore == null ? (fieldsToIgnore = new ArrayList<>()) : fieldsToIgnore;
        fieldsToPut = fieldsToPut == null ? new HashMap<>() : fieldsToPut;


        ArrayList<String> cannotPut = new ArrayList<>();

        JsonObject jsonObject = gson.toJsonTree(instance).getAsJsonObject();
        for (String key : fieldsToPut.keySet()) {
            Object value = fieldsToPut.get(key);
            if (isWrapClass(value.getClass())) {
                jsonObject.add(key, gson.toJsonTree(value).getAsJsonObject());
            } else if (value instanceof String) {
                jsonObject.addProperty(key, (String) value);
            } else {
                cannotPut.add(key);
            }
        }
        E son = null;
        try {
            son = gson.fromJson(jsonObject, sonClass);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains(" declares multiple JSON fields named ")) {
                String fieldName = e.getMessage().split(" declares multiple JSON fields named ")[1];
                if (!fieldsToIgnore.contains(fieldName)) {
                    fieldsToIgnore.add(fieldName);
                }
                if (!fieldsToPut.containsKey(fieldName)) {
                    fieldsToPut.put(fieldName, "FatherAndSonField9527");
                }
                return convertFatherToSon(sonClass, instance, fieldsToIgnore, fieldsToPut);
            }
        }
        if (son != null) {
            for (String key : cannotPut) {
                try {
                    Field field = sonClass.getDeclaredField(key);
                    field.setAccessible(true);
                    if (fieldsToPut.get(key).equals("FatherAndSonField9527")) {
                        field.set(son, field.get(instance));
                    } else {
                        field.set(son, fieldsToPut.get(key));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return son;
    }

    /**
     * 根据一个Map创建对象，不经过初始化
     *
     * @param cl:          Class,对象类
     * @param fieldsToPut: Map<String,Object>,存储属性名和属性对象的Map
     * @return 对象
     */
    public static <T> T createObject(Class<T> cl, Map<String, Object> fieldsToPut) {
        return convertFatherToSon(cl, new JsonObject(), null, fieldsToPut);
    }


    /**
     * 动态代理类对象
     *
     * @param invocation: InvocationHandler2,代理类
     * @param target:     Object,代理对象
     * @param method:     Method,代理方法
     * @param <T>         继承InvocationHandler2
     * @param <E>         子类对象
     * @return 子类对象
     */
    public static <T extends InvocationHandler2, E> E newProxy(T invocation, E target, Method method) throws Throwable {
        if (proxyHandler != null) {
            invocation.target = target;
            return (E) proxyHandler.invoke(invocation, method, null);
        }
        return null;
    }


    /** <a href="https://stackoverflow.com/questions/32148846/">...</a>
     * 获取类描述符
     * @param c: Class,类
     * @return String, 描述符
     */
    public static String getDescriptorForClass(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == byte.class)
                return "B";
            if (c == char.class)
                return "C";
            if (c == double.class)
                return "D";
            if (c == float.class)
                return "F";
            if (c == int.class)
                return "I";
            if (c == long.class)
                return "J";
            if (c == short.class)
                return "S";
            if (c == boolean.class)
                return "Z";
            if (c == void.class)
                return "V";
            throw new RuntimeException("Unrecognized primitive " + c);
        }
        if (c.isArray()) return c.getName().replace('.', '/');
        return ('L' + c.getName() + ';').replace('.', '/');
    }



    /** <a href="https://stackoverflow.com/questions/32148846/">...</a>
     * 获取方法描述符
     * @param m: Method,方法
     * @return String,描述符
     */
    public static String getMethodDescriptor(Method m) {
        StringBuilder s = new StringBuilder("(");
        for (final Class<?> c : m.getParameterTypes())
            s.append(getDescriptorForClass(c));
        s.append(')');
        return s.append(getDescriptorForClass(m.getReturnType())).toString();
    }

    /**
     * 代码插桩,未测试
     * @param method: Method,方法
     * @param invocation: InvocationHandler2,插桩
     * @throws Exception
     */
    public static void StubMethod(Method method, InvocationHandler2 invocation) throws Exception {
        handlers.put(method,invocation);
        Class<?> cl = method.getDeclaringClass();
        ClassReader cr = new ClassReader(cl.getName());
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        ClassWriter cw = new ClassWriter(cr, 0);
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
                if (name.equals(method.getName()) && desc.equals(getMethodDescriptor(method))) {
                    return new AdviceAdapter(api, methodVisitor, access, name, signature) {
                        @Override
                        protected void onMethodEnter() {
                            mv.visitInsn(Opcodes.ICONST_0);
                            mv.visitLdcInsn(name);
                            mv.visitLdcInsn(desc);
                            visitParams(mv,method);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,"bang/Utils","invokeMethod","(ILjava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",false);
                        }
                        @Override
                        protected void onMethodExit(int opcode){
                            mv.visitInsn(Opcodes.ICONST_1);
                            mv.visitLdcInsn(name);
                            mv.visitLdcInsn(desc);
                            visitParams(mv,method);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,"bang/Utils","invokeMethod","(ILjava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",false);
                        }

                        @Override
                        public void visitCode(){
                            if(invocation.invoke){
                                super.visitCode();
                            }else{
                                mv.visitInsn(Opcodes.ICONST_2);
                                mv.visitLdcInsn(name);
                                mv.visitLdcInsn(desc);
                                visitParams(mv,method);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,"bang/Utils","invokeMethod","(ILjava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",false);
                                if(desc.endsWith(")V")){
                                    mv.visitInsn(Opcodes.RETURN);
                                }else{
                                    String type = desc.split("\\)")[1];
                                    if(type.endsWith(";")){
                                        type = type.substring(0,type.length() - 1);
                                    }
                                    if(!type.startsWith("L")){
                                        switch (type){
                                            case "B":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Byte","byteValue","()B",false);
                                                mv.visitInsn(Opcodes.IRETURN);
                                                break;
                                            case "C":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Character","charValue","()C",false);
                                                mv.visitInsn(Opcodes.IRETURN);
                                                break;
                                            case "D":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Double","doubleValue","()D",false);
                                                mv.visitInsn(Opcodes.DRETURN);
                                                break;
                                            case "F":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Float","floatValue","()F",false);
                                                mv.visitInsn(Opcodes.FRETURN);
                                                break;
                                            case "I":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Integer","intValue","()I",false);
                                                mv.visitInsn(Opcodes.IRETURN);
                                                break;
                                            case "J":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Long","longValue","()J",false);
                                                mv.visitInsn(Opcodes.LRETURN);
                                                break;
                                            case "S":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Short","shortValue","()S",false);
                                                mv.visitInsn(Opcodes.IRETURN);
                                                break;
                                            case "Z":
                                                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false);
                                                mv.visitInsn(Opcodes.IRETURN);
                                                break;
                                        }
                                    }else {
                                        mv.visitTypeInsn(Opcodes.CHECKCAST, type.substring(1));
                                        mv.visitInsn(Opcodes.ARETURN);
                                    }
                                }
                                mv.visitEnd();
                            }
                        }
                    };
                }
                return methodVisitor;
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        Instrumentation instrumentation = Agent.getInstrument();
        if(instrumentation != null && instrumentation.isRedefineClassesSupported()){
            instrumentation.redefineClasses(new ClassDefinition(cl,cw.toByteArray()));
        }
    }

    //未测试
    public static Object invokeMethod(int i,String name,String desc,Object[] args) throws Throwable {
        for (Method method : handlers.keySet()){
            if(name.equals(method.getName()) && desc.equals(getMethodDescriptor(method))){
                InvocationHandler2 invocationHandler = handlers.get(method);
                switch (i){
                    case 0:
                        invocationHandler.before(args);
                        break;
                    case 1:
                        return invocationHandler.after(args);
                    case 2:
                        return invocationHandler.invoke(null,method,args);
                }
            }
        }
        return null;
    }

    public static void visitParams(MethodVisitor mv,Method method){
        mv.visitIntInsn(Opcodes.BIPUSH,method.getParameterTypes().length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY,"java/lang/Object");
        mv.visitInsn(Opcodes.DUP);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ALOAD,0);
        Class<?>[] classes = method.getParameterTypes();
        for (int i = 0; i < classes.length;) {
            Class<?> c = classes[i];
            if(i < 5){
                switch (i){
                    case 0:
                        mv.visitInsn(Opcodes.ICONST_1);
                        break;
                    case 1:
                        mv.visitInsn(Opcodes.ICONST_2);
                        break;
                    case 2:
                        mv.visitInsn(Opcodes.ICONST_3);
                        break;
                    case 3:
                        mv.visitInsn(Opcodes.ICONST_4);
                        break;
                    case 4:
                        mv.visitInsn(Opcodes.ICONST_5);
                        break;
                }
            }else{
                mv.visitIntInsn(Opcodes.BIPUSH,i + 1);
            }
            if (c.isPrimitive()) {
                if (c == byte.class || c == char.class || c == int.class || c == short.class || c == boolean.class) {
                    mv.visitVarInsn(Opcodes.ILOAD, ++i);
                    if(c == byte.class) {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                    }else if(c == char.class){
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                    }else if(c == int.class){
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    }else if(c == short.class){
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                    }else {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    }
                }
                else if (c == double.class) {
                    mv.visitVarInsn(Opcodes.DLOAD, ++i);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                }
                else if (c == float.class) {
                    mv.visitVarInsn(Opcodes.FLOAD, ++i);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                }
                else if (c == long.class) {
                    mv.visitVarInsn(Opcodes.LLOAD, ++i);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                }
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, ++i);
            }
            mv.visitInsn(Opcodes.AASTORE);
            if(i != classes.length) {
                mv.visitInsn(Opcodes.DUP);
            }
        }
        if(classes.length == 0){
            mv.visitInsn(Opcodes.AASTORE);
        }
    }


    private static boolean isWrapClass(Class<?> clz) {
        try {
            return ((Class<?>) clz.getField("TYPE").get(null)).isPrimitive();
        } catch (Exception e) {
            return false;
        }
    }

    protected static class MyMethodAdapter extends MethodVisitor {
        public boolean contains;

        String owner;
        String methodName;
        String methodDesc;

        protected MyMethodAdapter(int api, MethodVisitor methodVisitor, String owner, String methodName, String methodDesc) {
            super(api, methodVisitor);
            this.owner = owner;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String desc, boolean itf) {
            if (owner.equals(this.owner) && name.equals(methodName) && desc.equals(methodDesc)) {
                this.contains = true;

            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    protected static class MyClassVisitor extends ClassVisitor {
        public MyMethodAdapter adapter;

        private final String methodName;
        private final String methodDesc;
        private final String owner;
        private final String methodName2;
        private final String methodDesc2;

        public MyClassVisitor(int i, String methodName, String methodDesc, String owner, String methodName2, String methodDesc2) {
            super(i);
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.owner = owner;
            this.methodName2 = methodName2;
            this.methodDesc2 = methodDesc2;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            ;
            if (name.equals(methodName) && desc.equals(methodDesc)) {
                return (adapter = new MyMethodAdapter(api, mv, owner, methodName2, methodDesc2));
            }
            return mv;
        }
    }

}
