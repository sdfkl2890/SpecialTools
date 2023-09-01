package bang;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.util.ArrayList;
import java.util.List;

public class MyExclusionStrategy implements ExclusionStrategy {
    public List<String> fieldsToIgnore = new ArrayList<>();
    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {//对以下属性不进行序列化
        return fieldsToIgnore.contains(fieldAttributes.getName());
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
        return false;
    }
}
