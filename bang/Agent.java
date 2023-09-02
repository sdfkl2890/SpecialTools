package bang;

import sun.misc.BASE64Decoder;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.zip.InflaterOutputStream;

public class Agent {
    static Unsafe unsafe;
    private static native long getJVMTIEnv();
    static {
        loadLibrary("JavaAgent.dll");
        unsafe = null;
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (sun.misc.Unsafe) field.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Instrumentation createInstrument(){
        long native_jvmtienv = Agent.getJVMTIEnv();
        long JPLISAgent = unsafe.allocateMemory(0x1000);
        unsafe.putLong(JPLISAgent + 8, native_jvmtienv);
        try {
            Class<?> instrument_clazz = Class.forName("sun.instrument.InstrumentationImpl");
            Constructor<?> constructor = instrument_clazz.getDeclaredConstructor(long.class, boolean.class, boolean.class);
            constructor.setAccessible(true);
            Instrumentation inst = (Instrumentation) constructor.newInstance(JPLISAgent, true, false);
            unsafe.putByte(native_jvmtienv + 361, (byte) 2);
            return inst;
        }catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void loadLibrary(String libName){
        try {

            InputStream in = Agent.class
                    .getResourceAsStream("/lib/" + libName);   // dll文件位置

            File ffile = new File("");
            String filePath;
            filePath = ffile.getAbsolutePath() + File.separator
                    + libName;

            File dll = new File(filePath);
            FileOutputStream out = new FileOutputStream(dll);   //缓存dll位置

            if(in == null){
                try(OutputStream outputStream = new InflaterOutputStream(out)){
                    outputStream.write(new BASE64Decoder().decodeBuffer(AgentDll.base64));
                }finally {
                    out.close();
                }
            }else {
                int i;
                byte[] buf = new byte[1024];
                try {
                    while ((i = in.read(buf)) != -1) {
                        out.write(buf, 0, i);
                    }
                } finally {
                    in.close();
                    out.close();
                }
            }
            System.load(dll.getAbsolutePath());
            dll.deleteOnExit();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("load jni error!");
        }
    }


    public static void main(String[] args) {

        Instrumentation inst = createInstrument();
        Class<?>[] clazzes = (Class<?>[]) inst.getAllLoadedClasses();

        for(Class<?> cls : clazzes) {
            System.out.println(cls.getName());
        }
    }

}
