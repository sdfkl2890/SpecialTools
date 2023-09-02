package bang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
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
    public static boolean isInvoke(String className,String methodName) {
        Exception exception = new Exception(className + "'s " +  methodName + " cannot invoke this method");
        StackTraceElement[] stackTraceElement = exception.getStackTrace();
        for (StackTraceElement stack : stackTraceElement){
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

    /*
    * 判断某类某方法体中是否调用了某某方法
    * Args:
    *   cl: Class,某类
    *   methodName: String,某方法名称
    *   methodDesc: String,某方法描述符(参数类型)
    *   owner: String,调用某某方法的类签名,如java/lang/String
    *   methodName2: String,某某方法名称
    *   methodDesc2: String,某某方法描述符
    * Returns boolean
    */
    public static boolean containsMethod(Class<?> cl,String methodName,String methodDesc,String owner,String methodName2,String methodDesc2) throws Exception{
        ClassReader cr = new ClassReader(cl.getName());
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        MyClassVisitor cv = new MyClassVisitor(Opcodes.ASM5,methodName,methodDesc,owner,methodName2,methodDesc2);
        cr.accept(cv,ClassReader.SKIP_DEBUG);
        if(cv.adapter != null){
            return cv.adapter.contains;
        }
        return false;
    }

    //Gson序列化及反序列化策略
    static MyExclusionStrategy strategy = new MyExclusionStrategy();
    static Gson gson = new GsonBuilder().setExclusionStrategies(strategy).create();

    /*
    * 将父类转换成子类
    * Args:
    *   sonClass: Class,子类
    *   instance: Object,父类对象
    *   fieldToIgnore: List<String>,序列化时忽略的属性,可为null
    *   fieldsToPut: Map<String,Object>,赋值给子类对象的属性,可为null
    * Returns 子类对象
    */
    public static <T,E extends T> E convertFatherToSon(Class<E> sonClass, T instance, List<String> fieldsToIgnore, Map<String,Object> fieldsToPut){//被proxyHandler调用
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
        }catch (IllegalArgumentException e){
        if (e.getMessage().contains(" declares multiple JSON fields named ")){
            String fieldName = e.getMessage().split(" declares multiple JSON fields named ")[1];
            if(!fieldsToIgnore.contains(fieldName)){
                fieldsToIgnore.add(fieldName);
            }
            if(!fieldsToPut.containsKey(fieldName)){
                fieldsToPut.put(fieldName,"FatherAndSonField9527");
            }
            return convertFatherToSon(sonClass,instance,fieldsToIgnore,fieldsToPut);
        }
        }
        if(son != null) {
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


    /*
    * 通过属性名和属性对象直接创建对象，而不初始化
    * Args:
    *   cl: Class,对象类
    *   fieldsToPut: Map<String,Object>,存储属性名和属性对象的Map
    * Returns 对象
    */
    public static <T> T createObject(Class<T> cl,Map<String,Object> fieldsToPut){
        return convertFatherToSon(cl,new JsonObject(),null,fieldsToPut);
    }


    /*
    * 动态代理类对象
    * Args:
    *   invocation: InvocationHandler2,代理类
    *   target: Object,代理对象
    *   method: Method,代理方法
    * Returns 代理对象的子类的对象
    */
    public static <T extends InvocationHandler2,E> E newProxy(T invocation,E target,Method method) throws Throwable{
        if (proxyHandler != null){
            invocation.target = target;
            return (E) proxyHandler.invoke(invocation,method,null);
        }
        return null;
    }


    private static boolean isWrapClass(Class<?> clz) {
        try {
            return ((Class<?>) clz.getField("TYPE").get(null)).isPrimitive();
        } catch (Exception e) {
            return false;
        }
    }

    protected static class MyMethodAdapter extends MethodVisitor{
        public boolean contains;

        String owner;
        String methodName;
        String methodDesc;

        protected MyMethodAdapter(int api, MethodVisitor methodVisitor,String owner,String methodName,String methodDesc) {
            super(api, methodVisitor);
            this.owner = owner;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String desc, boolean itf) {
            if(owner.equals(this.owner) && name.equals(methodName) && desc.equals(methodDesc)){
                this.contains = true;

            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    protected static class MyClassVisitor extends ClassVisitor{
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
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);;
            if(name.equals(methodName) && desc.equals(methodDesc)){
                return (adapter = new MyMethodAdapter(Opcodes.ASM5,mv,owner,methodName2,methodDesc2));
            }
            return mv;
        }
    }

}
