package bang;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public abstract class InvocationHandler2 implements InvocationHandler {

    Object target;
    boolean invoke;

    public InvocationHandler2(boolean invoke) {
        this.invoke = invoke;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        before(args);
        Object result;
        if (invoke) {
            result = method.invoke(target, args);
            after(args);
        } else {
            result = after(args);
        }
        return result;
    }

    public void before(Object[] args) {
    }

    public Object after(Object[] args) {
        return null;
    }

    public Object getTarget() {
        return target;
    }
}
