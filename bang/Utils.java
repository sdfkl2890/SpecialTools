package bang;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    static InvocationHandler invocationHandler;

    static {
        scriptEngine = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            invocationHandler = (InvocationHandler) scriptEngine.eval("var InvocationHandler = java.lang.reflect.InvocationHandler;\n" +
                    "var Utils = Java.type(\"bang.Utils\");\n" +
                    "var InvocationHandler3 = Java.extend(InvocationHandler,{\n" +
                    "\tinvoke: function(invocation,method,args){\n" +
                    "\t\tvar target = invocation.getTarget();\n" +
                    "\t\tvar clToExtend = target.getClass().static;\n" +
                    "\t\tvar object = {};\n" +
                    "\t\tobject[method.getName()] = function(){\n" +
                    "\t\t\tinvocation.invoke(target,method,Java.to(arguments, \"java.lang.Object[]\"));\n" +
                    "\t\t};\n" +
                    "\t\tvar extendedClass = Java.extend(clToExtend,object);\n" +
                    "\t\treturn Utils.convertFatherToSon(extendedClass.class,target,null,null);\n" +
                    "\t}\n" +
                    "});\n" +
                    "new InvocationHandler3();");
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }


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

    public static boolean containsMethod(Class<?> cl,String methodName,String methodDesc,String owner,String methodName2,String methodDesc2) throws Exception{
        ClassReader cr = new ClassReader(cl.getName());
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        final MyMethodAdapter[] methodVisitor = new MyMethodAdapter[1];
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);;
                if(name.equals(methodName) && desc.equals(methodDesc)){
                    return (methodVisitor[0] = new MyMethodAdapter(Opcodes.ASM5,mv,owner,methodName2,methodDesc2));
                }
                return mv;
            }
        };
        cr.accept(cv,ClassReader.SKIP_DEBUG);
        if(methodVisitor[0] != null){
            return methodVisitor[0].contains;
        }
        return false;
    }

    static MyExclusionStrategy strategy = new MyExclusionStrategy();
    static Gson gson = new GsonBuilder().setExclusionStrategies(strategy).create();

    public static <T,E extends T> E convertFatherToSon(Class<E> sonClass, T instance, List<String> fieldsToIgnore, Map<String,Object> fieldsToPut){//被newProxy调用


        strategy.fieldsToIgnore = fieldsToIgnore == null ? (fieldsToIgnore = new ArrayList<>()) : fieldsToIgnore;
        fieldsToPut = fieldsToPut == null ? new HashMap<>() : fieldsToPut;

        String json = gson.toJson(instance);

        ArrayList<String> cannotPut = new ArrayList<>();
        JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
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

    public static <T extends InvocationHandler2,E> E newProxy(T invocation,E target,Method method) throws Throwable{
        if(scriptEngine == null){
            throw new Exception("nashorn script engine not found");
        }
        if (invocationHandler != null){
            invocation.target = target;
            return (E)invocationHandler.invoke(invocation,method,null);
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


    public static void main(String[] args) throws Throwable{
        System.out.println("----------");
        isInvokedByMain(); //true
        System.out.println("----------");
        Person person = new Person();
        person.age = 1;
        Person person1 = newProxy(new InvocationHandler2(true){
            public void before(Object[] args){
                System.out.println(args[0] + "1");//wasd1
                System.out.println("abcd");
            }
        },person,Person.class.getMethod("printName",String.class));
        person1.printName("wasd");
        System.out.println("----------");
        isContainsMethod();//true
        System.out.println("----------");
    }

    private static void isInvokedByMain(){
        System.out.println("Invoked by bang.Utils.main(args) " + isInvoke("bang.Utils","main"));
    }

    private static void isContainsMethod() throws Exception {
        System.out.println("Utils.class Contains Method isInvokedByMain " + containsMethod(Utils.class,"isInvokedByMain","()V","java/io/PrintStream","println","(Ljava/lang/String;)V"));
    }


}
